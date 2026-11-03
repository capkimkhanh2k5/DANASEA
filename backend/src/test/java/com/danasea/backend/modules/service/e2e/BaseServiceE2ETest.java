package com.danasea.backend.modules.service.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.service.domain.models.DocStatus;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.*;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.*;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.security.authentication.infrastructure.security.JwtTokenProvider;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Real HTTP/security/persistence fixtures; every business row rolls back after its case. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(BaseServiceE2ETest.AsyncAuditConfiguration.class)
public abstract class BaseServiceE2ETest extends BaseSecurityIntegrationTest {
    protected static final String VENDOR_API_BASE = "/api/vendor/services";
    protected static final String ADMIN_API_BASE = "/api/admin/services";

    @DynamicPropertySource
    static void isolatedServiceSchema(DynamicPropertyRegistry registry) {
        String url = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort()
                + "/testdb?currentSchema=service_legacy";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.schemas", () -> "service_legacy");
        registry.add("spring.flyway.default-schema", () -> "service_legacy");
        registry.add("spring.datasource.hikari.schema", () -> "service_legacy");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "service_legacy");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JpaServiceRepository serviceRepository;
    @Autowired protected JpaServiceImageRepository imageRepository;
    @Autowired protected JpaCategoryRepository categoryRepository;
    @Autowired private JpaUserRepository userRepository;
    @Autowired private JpaVendorRepository vendorRepository;
    @Autowired private JpaServiceSafetyDocumentRepository safetyRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TrackingAuditExecutor auditExecutor;

    protected UUID VENDOR_A_ID;
    protected UUID VENDOR_B_ID;
    protected UUID ADMIN_USER_ID;
    protected UUID ACTIVE_CATEGORY_ID;
    protected UUID ACTIVE_CATEGORY_ID_2;
    protected UUID INACTIVE_CATEGORY_ID;
    protected UUID NON_EXISTENT_CATEGORY_ID;
    protected String vendorAToken;
    protected String vendorBToken;
    protected String pendingToken;
    protected String rejectedToken;
    protected String adminToken;
    protected String customerToken;
    private final Set<UUID> serviceIds = new HashSet<>();
    private final Map<String, Integer> expectedAuditCounts = new HashMap<>();

    @BeforeEach
    void setUpServiceFixtures() {
        awaitAuditTasks();
        serviceIds.clear();
        expectedAuditCounts.clear();
        UserJpaEntity vendorA = createUser(Role.VENDOR);
        UserJpaEntity vendorB = createUser(Role.VENDOR);
        UserJpaEntity pending = createUser(Role.VENDOR);
        UserJpaEntity rejected = createUser(Role.VENDOR);
        UserJpaEntity admin = createUser(Role.ADMIN);
        UserJpaEntity customer = createUser(Role.CUSTOMER);
        VENDOR_A_ID = createVendor(vendorA, VerificationStatus.APPROVED);
        VENDOR_B_ID = createVendor(vendorB, VerificationStatus.APPROVED);
        createVendor(pending, VerificationStatus.PENDING);
        createVendor(rejected, VerificationStatus.REJECTED);
        ADMIN_USER_ID = admin.getId();
        ACTIVE_CATEGORY_ID = createCategory(true);
        ACTIVE_CATEGORY_ID_2 = createCategory(true);
        INACTIVE_CATEGORY_ID = createCategory(false);
        NON_EXISTENT_CATEGORY_ID = UUID.randomUUID();
        vendorAToken = token(vendorA);
        vendorBToken = token(vendorB);
        pendingToken = token(pending);
        rejectedToken = token(rejected);
        adminToken = token(admin);
        customerToken = token(customer);
        entityManager.flush();
    }

    private UserJpaEntity createUser(Role role) {
        UserJpaEntity user = new UserJpaEntity();
        user.setEmail(UUID.randomUUID() + "@service-legacy.example.test");
        user.setFullName("Service legacy " + role);
        user.setPasswordHash("unused-jwt-fixture-password");
        user.setRole(role);
        user.setIsEmailVerified(true);
        user.setIsLocked(false);
        return userRepository.saveAndFlush(user);
    }

    private UUID createVendor(UserJpaEntity user, VerificationStatus status) {
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setUserId(user.getId());
        vendor.setBusinessName("Service legacy " + UUID.randomUUID());
        vendor.setVerificationStatus(status);
        return vendorRepository.saveAndFlush(vendor).getId();
    }

    private UUID createCategory(boolean active) {
        CategoryJpaEntity category = new CategoryJpaEntity();
        category.setName("Service legacy category");
        category.setSlug("service-legacy-" + UUID.randomUUID());
        category.setIsActive(active);
        category.setRequiresSafetyCert(false);
        return categoryRepository.saveAndFlush(category).getId();
    }

    private String token(UserJpaEntity user) {
        return jwtTokenProvider.generateAccessToken(new Authentication(user.getId(), user.getEmail(),
                user.getPasswordHash(), user.getRole().name(), true, true, "vi", 0L));
    }

    protected MockHttpServletRequestBuilder authenticateAsVendorA(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + vendorAToken);
    }
    protected MockHttpServletRequestBuilder authenticateAsVendorB(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + vendorBToken);
    }
    protected MockHttpServletRequestBuilder authenticateAsPendingVendor(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + pendingToken);
    }
    protected MockHttpServletRequestBuilder authenticateAsRejectedVendor(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + rejectedToken);
    }
    protected MockHttpServletRequestBuilder authenticateAsAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + adminToken);
    }
    protected MockHttpServletRequestBuilder authenticateAsCustomer(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + customerToken);
    }

    protected Map<String, Object> standardPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("categoryId", ACTIVE_CATEGORY_ID.toString());
        payload.put("name", "Lặn Ngắm San Hô Bán Đảo Sơn Trà");
        payload.put("nameEn", "Son Tra Coral Reef Scuba Diving");
        payload.put("description", "Khám phá biển cùng hướng dẫn viên");
        payload.put("descriptionEn", "Explore the sea with an instructor");
        payload.put("price", new BigDecimal("1250000.00"));
        payload.put("durationMinutes", 120);
        payload.put("capacityPerSlot", 12);
        payload.put("locationName", "Bãi Bụt");
        payload.put("address", "Sơn Trà, Đà Nẵng");
        payload.put("latitude", new BigDecimal("16.12"));
        payload.put("longitude", new BigDecimal("108.26"));
        payload.put("weatherSensitive", false);
        payload.put("waiverContent", "Tuân thủ hướng dẫn an toàn");
        return payload;
    }

    protected JsonNode response(RequestBuilder request, int expectedStatus) throws Exception {
        String json = mockMvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return json.isBlank() ? objectMapper.getNodeFactory().nullNode() : objectMapper.readTree(json);
    }

    protected void error(MockHttpServletRequestBuilder request, int expectedStatus, String code) throws Exception {
        mockMvc.perform(request).andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value(code));
    }

    protected MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object payload) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload));
    }

    protected JsonNode create(Map<String, Object> payload) throws Exception {
        JsonNode result = response(body(authenticateAsVendorA(post(VENDOR_API_BASE)), payload), 201);
        UUID id = id(result);
        serviceIds.add(id);
        assertThat(result.path("status").asText()).isEqualTo("DRAFT");
        assertThat(result.path("vendorId").asText()).isEqualTo(VENDOR_A_ID.toString());
        assertPersisted(result);
        assertPayload(result, payload);
        return result;
    }

    protected UUID id(JsonNode json) {
        return UUID.fromString(json.path("id").asText());
    }

    protected ServiceJpaEntity seed(ServiceStatus status) {
        return seed(status, VENDOR_A_ID);
    }

    protected ServiceJpaEntity seed(ServiceStatus status, UUID vendorId) {
        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setVendorId(vendorId);
        service.setCategoryId(ACTIVE_CATEGORY_ID);
        service.setName("Service " + UUID.randomUUID());
        service.setNameEn("English service");
        service.setSlug("service-" + UUID.randomUUID());
        service.setDescription("Original description");
        service.setDescriptionEn("Original English description");
        service.setPrice(new BigDecimal("1250000.00"));
        service.setDurationMinutes(120);
        service.setCapacityPerSlot(12);
        service.setLocationName("Bãi Bụt");
        service.setAddress("Sơn Trà, Đà Nẵng");
        service.setLatitude(new BigDecimal("16.12"));
        service.setLongitude(new BigDecimal("108.26"));
        service.setStatus(status);
        service.setWeatherSensitive(false);
        service.setWaiverContent("Original waiver");
        service.setAvgRating(BigDecimal.ZERO);
        service.setRatingCount(0);
        service.setViewCount(0);
        if (status == ServiceStatus.REJECTED) service.setRejectionReason("Original rejection");
        service = serviceRepository.saveAndFlush(service);
        serviceIds.add(service.getId());
        return service;
    }

    protected ServiceImageJpaEntity addImage(UUID serviceId, int order) {
        ServiceImageJpaEntity image = new ServiceImageJpaEntity();
        image.setServiceId(serviceId);
        image.setUrl("https://example.test/service/" + serviceId + "/" + order + ".jpg");
        image.setSortOrder((short) order);
        return imageRepository.saveAndFlush(image);
    }

    protected void addApprovedSafetyDocument(UUID serviceId) {
        ServiceSafetyDocumentJpaEntity document = new ServiceSafetyDocumentJpaEntity();
        document.setServiceId(serviceId);
        document.setFileUrl("https://example.test/safety/" + serviceId + ".pdf");
        document.setStatus(DocStatus.APPROVED);
        document.setReviewedBy(ADMIN_USER_ID);
        document.setReviewedAt(OffsetDateTime.now());
        safetyRepository.saveAndFlush(document);
    }

    protected JsonNode detail(UUID serviceId) throws Exception {
        JsonNode result = response(authenticateAsVendorA(get(VENDOR_API_BASE + "/" + serviceId)), 200);
        assertPersisted(result);
        return result;
    }

    protected JsonNode update(UUID serviceId, Map<String, Object> fields) throws Exception {
        JsonNode result = response(body(authenticateAsVendorA(patch(VENDOR_API_BASE + "/" + serviceId)), fields), 200);
        assertPersisted(result);
        assertPayload(result, fields);
        return result;
    }

    protected JsonNode transition(UUID serviceId, String action, ServiceStatus expected) throws Exception {
        MockHttpServletRequestBuilder request;
        if (action.equals("submit")) {
            request = authenticateAsVendorA(post(VENDOR_API_BASE + "/" + serviceId + "/submit"));
        } else if (action.equals("approve")) {
            request = authenticateAsAdmin(patch(ADMIN_API_BASE + "/" + serviceId + "/approve"));
        } else {
            request = authenticateAsVendorA(patch(VENDOR_API_BASE + "/" + serviceId + "/" + action));
        }
        JsonNode result = response(request, 200);
        assertThat(result.path("status").asText()).isEqualTo(expected.name());
        assertPersisted(result);
        assertState(serviceId, expected);
        if (action.equals("approve")) assertAudit(serviceId, "SERVICE_APPROVED", null);
        return result;
    }

    protected JsonNode reject(UUID serviceId, String reason) throws Exception {
        JsonNode result = response(body(authenticateAsAdmin(patch(ADMIN_API_BASE + "/" + serviceId + "/reject")),
                Map.of("reason", reason)), 200);
        assertThat(result.path("status").asText()).isEqualTo("REJECTED");
        assertThat(result.path("rejectionReason").asText()).isEqualTo(reason);
        assertPersisted(result);
        assertAudit(serviceId, "SERVICE_REJECTED", reason);
        return result;
    }

    protected ServiceJpaEntity reload(UUID serviceId) {
        entityManager.flush();
        entityManager.clear();
        return serviceRepository.findById(serviceId).orElseThrow();
    }

    protected void assertState(UUID serviceId, ServiceStatus expected) {
        assertThat(reload(serviceId).getStatus()).isEqualTo(expected);
    }

    protected void assertPayload(JsonNode result, Map<String, Object> payload) {
        payload.forEach((field, value) -> {
            if (field.equals("vendorId")) return; // Ownership always comes from authenticated vendor.
            JsonNode expected = objectMapper.valueToTree(value);
            if (expected.isNumber()) {
                assertThat(result.path(field).decimalValue()).as(field).isEqualByComparingTo(expected.decimalValue());
            } else {
                assertThat(result.path(field)).as(field).isEqualTo(expected);
            }
        });
    }

    protected void assertPersisted(JsonNode result) {
        ServiceJpaEntity saved = reload(id(result));
        assertThat(result.path("vendorId").asText()).isEqualTo(saved.getVendorId().toString());
        assertThat(result.path("categoryId").asText()).isEqualTo(saved.getCategoryId().toString());
        assertThat(result.path("status").asText()).isEqualTo(saved.getStatus().name());
        assertThat(result.path("name").asText()).isEqualTo(saved.getName());
        assertThat(result.path("nameEn").asText(null)).isEqualTo(saved.getNameEn());
        assertThat(result.path("description").asText(null)).isEqualTo(saved.getDescription());
        assertThat(result.path("descriptionEn").asText(null)).isEqualTo(saved.getDescriptionEn());
        assertThat(result.path("locationName").asText(null)).isEqualTo(saved.getLocationName());
        assertThat(result.path("address").asText(null)).isEqualTo(saved.getAddress());
        assertThat(result.path("waiverContent").asText(null)).isEqualTo(saved.getWaiverContent());
        assertThat(result.path("waiverContentEn").asText(null)).isEqualTo(saved.getWaiverContentEn());
        assertThat(result.path("waiverRequired").asBoolean()).isEqualTo(saved.getWaiverRequired());
        assertThat(result.path("waiverVersion").asInt()).isEqualTo(saved.getWaiverVersion());
        assertThat(result.path("rejectionReason").asText(null)).isEqualTo(saved.getRejectionReason());
        assertThat(result.path("price").decimalValue()).isEqualByComparingTo(saved.getPrice());
        assertThat(result.path("durationMinutes").asInt()).isEqualTo(saved.getDurationMinutes());
        assertThat(result.path("capacityPerSlot").asInt()).isEqualTo(saved.getCapacityPerSlot());
        assertThat(result.path("weatherSensitive").asBoolean()).isEqualTo(Boolean.TRUE.equals(saved.getWeatherSensitive()));
        for (String field : List.of("latitude", "longitude", "minWindKmh", "maxWaveM")) {
            BigDecimal expected = switch (field) {
                case "latitude" -> saved.getLatitude();
                case "longitude" -> saved.getLongitude();
                case "minWindKmh" -> saved.getMinWindKmh();
                default -> saved.getMaxWaveM();
            };
            if (expected == null) assertThat(result.path(field).asText(null)).isNull();
            else assertThat(result.path(field).decimalValue()).isEqualByComparingTo(expected);
        }
        List<ServiceImageJpaEntity> images = imageRepository.findByServiceIdOrderBySortOrderAsc(saved.getId());
        assertThat(result.path("images").size()).isEqualTo(images.size());
        for (int i = 0; i < images.size(); i++) {
            assertThat(result.path("images").get(i).path("id").asText()).isEqualTo(images.get(i).getId().toString());
            assertThat(result.path("images").get(i).path("url").asText()).isEqualTo(images.get(i).getUrl());
        }
    }

    protected Set<UUID> ids(JsonNode array) {
        assertThat(array.isArray()).isTrue();
        Set<UUID> result = new HashSet<>();
        array.forEach(row -> assertThat(result.add(id(row))).isTrue());
        return result;
    }

    protected void assertVendorList(UUID... expected) throws Exception {
        JsonNode result = response(authenticateAsVendorA(get(VENDOR_API_BASE)), 200);
        assertThat(ids(result)).containsExactlyInAnyOrder(expected);
        for (JsonNode item : result) {
            assertThat(item.path("vendorId").asText()).isEqualTo(VENDOR_A_ID.toString());
            assertPersisted(item);
        }
    }

    protected void assertDeleted(UUID serviceId) throws Exception {
        response(authenticateAsVendorA(delete(VENDOR_API_BASE + "/" + serviceId)), 204);
        entityManager.flush();
        entityManager.clear();
        assertThat(serviceRepository.existsById(serviceId)).isFalse();
        assertThat(imageRepository.findByServiceId(serviceId)).isEmpty();
        error(authenticateAsVendorA(get(VENDOR_API_BASE + "/" + serviceId)), 404, "SERVICE_NOT_FOUND");
    }

    protected void invalidState(String action, ServiceStatus initial) throws Exception {
        UUID serviceId = seed(initial).getId();
        addImage(serviceId, 0);
        MockHttpServletRequestBuilder request;
        if (action.equals("submit")) request = authenticateAsVendorA(post(VENDOR_API_BASE + "/" + serviceId + "/submit"));
        else if (action.equals("delete")) request = authenticateAsVendorA(delete(VENDOR_API_BASE + "/" + serviceId));
        else if (action.equals("approve") || action.equals("reject")) {
            request = authenticateAsAdmin(patch(ADMIN_API_BASE + "/" + serviceId + "/" + action));
            if (action.equals("reject")) request = body(request, Map.of("reason", "Valid reason"));
        } else request = authenticateAsVendorA(patch(VENDOR_API_BASE + "/" + serviceId + "/" + action));
        error(request, 400, "INVALID_SERVICE_STATE");
        assertState(serviceId, initial);
        assertThat(imageRepository.findByServiceId(serviceId)).hasSize(1);
        assertThat(auditCount(serviceId)).isZero();
    }

    protected MockHttpServletRequestBuilder vendorRequest(UUID serviceId, String action) throws Exception {
        String path = VENDOR_API_BASE + "/" + serviceId;
        return switch (action) {
            case "create" -> body(post(VENDOR_API_BASE), standardPayload());
            case "list" -> get(VENDOR_API_BASE);
            case "get" -> get(path);
            case "update" -> body(patch(path), Map.of("price", 1));
            case "submit" -> post(path + "/submit");
            case "delete" -> delete(path);
            default -> patch(path + "/" + action);
        };
    }

    protected MockHttpServletRequestBuilder adminRequest(UUID serviceId, String action) throws Exception {
        if (action.equals("list")) return get(ADMIN_API_BASE);
        MockHttpServletRequestBuilder request = patch(ADMIN_API_BASE + "/" + serviceId + "/" + action);
        return action.equals("reject") ? body(request, Map.of("reason", "Valid reason")) : request;
    }

    protected void otherVendorDenied(String action) throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        MockHttpServletRequestBuilder request = switch (action) {
            case "get" -> get(VENDOR_API_BASE + "/" + serviceId);
            case "update" -> body(patch(VENDOR_API_BASE + "/" + serviceId), Map.of("price", 1));
            case "delete" -> delete(VENDOR_API_BASE + "/" + serviceId);
            case "submit" -> post(VENDOR_API_BASE + "/" + serviceId + "/submit");
            default -> patch(VENDOR_API_BASE + "/" + serviceId + "/" + action);
        };
        error(authenticateAsVendorB(request), 403, "UNAUTHORIZED_SERVICE_ACCESS");
        assertState(serviceId, ServiceStatus.PUBLISHED);
        assertThat(reload(serviceId).getPrice()).isEqualByComparingTo("1250000.00");
        assertThat(auditCount(serviceId)).isZero();
    }

    protected int auditCount(UUID serviceId) {
        awaitAuditTasks();
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?", Integer.class, serviceId);
    }

    protected void assertAudit(UUID serviceId, String action, String reason) throws Exception {
        int expectedCount = expectedAuditCounts.merge(serviceId + ":" + action, 1, Integer::sum);
        awaitAuditTasks();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT actor_user_id, entity_type, metadata FROM audit_logs WHERE entity_id = ? AND action = ?",
                serviceId, action);
        assertThat(rows).hasSize(expectedCount);
        for (Map<String, Object> row : rows) {
            assertThat(row.get("actor_user_id")).isEqualTo(ADMIN_USER_ID);
            assertThat(row.get("entity_type")).isEqualTo("SERVICE");
            if (reason != null) assertThat(row.get("metadata").toString()).contains(reason);
        }
    }

    @AfterEach
    void cleanCommittedAsyncAuditRows() {
        awaitAuditTasks();
        TransactionTemplate independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        independent.executeWithoutResult(tx -> {
            for (UUID id : serviceIds) jdbc.update("DELETE FROM audit_logs WHERE entity_id = ?", id);
        });
    }

    private void awaitAuditTasks() {
        try {
            auditExecutor.awaitAll(Duration.ofSeconds(10));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting asynchronous audit completion", exception);
        } catch (TimeoutException exception) {
            throw new AssertionError("Asynchronous audit tasks did not finish within 10 seconds", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AsyncAuditConfiguration {
        @Bean(name = "taskExecutor", destroyMethod = "close")
        TrackingAuditExecutor taskExecutor() {
            return new TrackingAuditExecutor();
        }
    }

    /** Tracks real asynchronous tasks, including tasks submitted by another tracked task. */
    public static final class TrackingAuditExecutor extends com.danasea.backend.configs.TrackingAsyncTestExecutor {
    }

}

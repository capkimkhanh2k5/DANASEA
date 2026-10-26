package com.danasea.backend.e2e;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.List;
import com.danasea.backend.security.authentication.domain.models.Authentication;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceOptionJpaEntity;
import com.danasea.backend.modules.service.domain.models.PricingUnit;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.NotificationStatus;
import com.danasea.backend.modules.communication.infrastructure.persistence.entities.NotificationJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaNotificationRepository;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authentication.infrastructure.security.JwtTokenProvider;
import com.danasea.backend.security.authentication.presentation.dtos.LoginRequest;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseE2ETest extends BaseSecurityIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired(required = false)
    @Qualifier("requestMappingHandlerMapping")
    protected RequestMappingHandlerMapping handlerMapping;

    @Autowired
    protected JpaUserRepository userRepository;

    @Autowired
    protected JpaVendorRepository vendorRepository;

    @Autowired
    protected JpaCategoryRepository categoryRepository;

    @Autowired
    protected JpaServiceRepository serviceRepository;

    @Autowired
    protected JpaServiceSlotRepository serviceSlotRepository;

    @Autowired
    protected JpaServiceOptionRepository optionRepository;

    @Autowired
    protected JpaMasterOrderRepository masterOrderRepository;

    @Autowired
    protected JpaSubOrderRepository subOrderRepository;

    @Autowired
    protected JpaPaymentRepository paymentRepository;

    @Autowired
    protected JpaNotificationRepository notificationRepository;

    @Autowired(required = false)
    protected CacheManager cacheManager;

    protected final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Standard Fixture IDs
    protected UserJpaEntity customerUser;
    protected UserJpaEntity attackerUser;
    protected UserJpaEntity adminUser;

    protected UserJpaEntity vendorUserA;
    protected VendorJpaEntity vendorProfileA;

    protected UserJpaEntity vendorUserB;
    protected VendorJpaEntity vendorProfileB;

    protected CategoryJpaEntity activeCategory;

    protected ServiceJpaEntity serviceA1;
    protected ServiceSlotJpaEntity slotA1TomorrowMorning;
    protected ServiceSlotJpaEntity slotA1TomorrowAfternoon;

    protected ServiceJpaEntity serviceA2;
    protected ServiceSlotJpaEntity slotA2TomorrowEarly;

    protected ServiceJpaEntity serviceB1;
    protected ServiceSlotJpaEntity slotB1TomorrowLate;

    protected MasterOrderJpaEntity paidOrder;
    protected SubOrderJpaEntity paidSubOrder;
    protected PaymentJpaEntity paidPayment;

    protected MasterOrderJpaEntity unpaidOrder;
    protected SubOrderJpaEntity unpaidSubOrder;

    protected MasterOrderJpaEntity cancelledOrder;
    protected SubOrderJpaEntity cancelledSubOrder;

    protected NotificationJpaEntity sampleNotificationCustomer;

    // Tokens
    protected String customerToken;
    protected String attackerToken;
    protected String adminToken;
    protected String vendorAToken;
    protected String vendorBToken;

    @BeforeEach
    void setUpBaseE2E() throws Exception {
        cleanDatabase();
        seedTestData();
        generateAuthTokens();
    }

    @AfterEach
    void cleanUpE2E() {
        cleanDatabase();
    }

    protected void cleanDatabase() {
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(name -> {
                var cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            });
        }
        jdbc.execute("""
                TRUNCATE TABLE sub_order_reschedule_history, sub_order_reschedule_proposals,
                    notifications, refunds, payments, sub_orders, master_orders,
                    booking_item_allocations, booking_items, bookings, service_slot_units,
                    service_slots, service_options, services, categories, vendors, users CASCADE
                """);
    }

    protected void seedTestData() {
        jdbc.update("DELETE FROM system_configs WHERE \"key\" = 'PLATFORM_COMMISSION_RATE'");
        jdbc.update("INSERT INTO system_configs(id, \"key\", \"value\", description, created_at, updated_at) VALUES (gen_random_uuid(), 'PLATFORM_COMMISSION_RATE', '10.0', 'Platform Commission Rate', now(), now())");

        // 1. Users
        customerUser = createUser("customer.e2e@danasea.vn", "Customer One", Role.CUSTOMER);
        attackerUser = createUser("attacker.e2e@danasea.vn", "Attacker User", Role.CUSTOMER);
        adminUser = createUser("admin.e2e@danasea.vn", "Super Admin", Role.ADMIN);

        vendorUserA = createUser("vendor.a.e2e@danasea.vn", "Vendor Alpha Owner", Role.VENDOR);
        vendorProfileA = createVendorProfile(vendorUserA.getId(), "Danang Diving Adventure", BadgeTier.TOP_RATED,
                new BigDecimal("4.8"), 12, "0123456789", "0401234567");

        vendorUserB = createUser("vendor.b.e2e@danasea.vn", "Vendor Beta Owner", Role.VENDOR);
        vendorProfileB = createVendorProfile(vendorUserB.getId(), "Son Tra Watersports", BadgeTier.VERIFIED,
                new BigDecimal("4.2"), 5, "9876543210", "0409876543");

        // 2. Category
        activeCategory = new CategoryJpaEntity();
        activeCategory.setName("Lặn Biển & Khám Phá");
        activeCategory.setSlug("lan-bien-kham-pha");
        activeCategory.setIsActive(true);
        activeCategory = categoryRepository.save(activeCategory);

        // 3. Services & Slots
        // Service A1 (Vendor A, Price 500k, Rating 4.8)
        serviceA1 = new ServiceJpaEntity();
        serviceA1.setVendorId(vendorProfileA.getId());
        serviceA1.setCategoryId(activeCategory.getId());
        serviceA1.setName("Lặn ngắm san hô Bán đảo Sơn Trà");
        serviceA1.setNameEn("Son Tra Scuba Diving Tour");
        serviceA1.setSlug("lan-ngam-san-ho-son-tra");
        serviceA1.setDescription("Khám phá hệ sinh thái biển tuyệt đẹp tại bán đảo Sơn Trà");
        serviceA1.setDescriptionEn("Explore marine life in Son Tra Peninsula");
        serviceA1.setPrice(new BigDecimal("500000.00"));
        serviceA1.setDurationMinutes(120);
        serviceA1.setCapacityPerSlot(10);
        serviceA1.setStatus(ServiceStatus.PUBLISHED);
        serviceA1.setAvgRating(new BigDecimal("4.80"));
        serviceA1.setRatingCount(12);
        serviceA1.setViewCount(150);
        serviceA1.setWaiverContent("Cam kết tuân thủ quy định an toàn dưới nước");
        serviceA1.setWaiverContentEn("Agree to safety regulations underwater");
        serviceA1.setAddress("Bãi Bụt, Bán đảo Sơn Trà, Đà Nẵng");
        serviceA1.setLatitude(new BigDecimal("16.1050"));
        serviceA1.setLongitude(new BigDecimal("108.2600"));
        serviceA1 = serviceRepository.save(serviceA1);

        // Slot A1 Tomorrow morning: capacity 10, booked 2 -> available 8
        slotA1TomorrowMorning = createSlot(serviceA1.getId(), LocalDate.now().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(10, 0), 10, 2, SlotStatus.OPEN);

        // Slot A1 Tomorrow afternoon: capacity 10, booked 10 -> available 0 (fully booked)
        slotA1TomorrowAfternoon = createSlot(serviceA1.getId(), LocalDate.now().plusDays(1),
                LocalTime.of(14, 0), LocalTime.of(16, 0), 10, 10, SlotStatus.OPEN);

        // Service A2 (Vendor A, Price 250k, Rating 4.5)
        serviceA2 = new ServiceJpaEntity();
        serviceA2.setVendorId(vendorProfileA.getId());
        serviceA2.setCategoryId(activeCategory.getId());
        serviceA2.setName("Chèo thuyền SUP Ngắm Bình Minh");
        serviceA2.setNameEn("Sunrise SUP Paddleboarding");
        serviceA2.setSlug("cheo-sup-binh-minh");
        serviceA2.setDescription("Chèo SUP ngắm bình minh trên biển Đà Nẵng");
        serviceA2.setPrice(new BigDecimal("250000.00"));
        serviceA2.setDurationMinutes(60);
        serviceA2.setCapacityPerSlot(6);
        serviceA2.setStatus(ServiceStatus.PUBLISHED);
        serviceA2.setAvgRating(new BigDecimal("4.50"));
        serviceA2.setRatingCount(6);
        serviceA2.setViewCount(80);
        serviceA2.setAddress("Bãi biển Mỹ Khê, Đà Nẵng");
        serviceA2.setLatitude(new BigDecimal("16.0600"));
        serviceA2.setLongitude(new BigDecimal("108.2400"));
        serviceA2 = serviceRepository.save(serviceA2);

        slotA2TomorrowEarly = createSlot(serviceA2.getId(), LocalDate.now().plusDays(1),
                LocalTime.of(5, 30), LocalTime.of(6, 30), 6, 1, SlotStatus.OPEN);

        // Service B1 (Vendor B, Price 800k, Rating 3.9)
        serviceB1 = new ServiceJpaEntity();
        serviceB1.setVendorId(vendorProfileB.getId());
        serviceB1.setCategoryId(activeCategory.getId());
        serviceB1.setName("Lướt ván diều Bãi biển Mỹ Khê");
        serviceB1.setNameEn("My Khe Kitesurfing Experience");
        serviceB1.setSlug("luot-van-dieu-my-khe");
        serviceB1.setDescription("Trải nghiệm cảm giác mạnh cùng ván diều");
        serviceB1.setPrice(new BigDecimal("800000.00"));
        serviceB1.setDurationMinutes(90);
        serviceB1.setCapacityPerSlot(4);
        serviceB1.setStatus(ServiceStatus.PUBLISHED);
        serviceB1.setAvgRating(new BigDecimal("3.90"));
        serviceB1.setRatingCount(4);
        serviceB1.setViewCount(220);
        serviceB1.setAddress("Bãi biển Mỹ Khê, Đà Nẵng");
        serviceB1 = serviceRepository.save(serviceB1);

        slotB1TomorrowLate = createSlot(serviceB1.getId(), LocalDate.now().plusDays(1),
                LocalTime.of(15, 0), LocalTime.of(16, 30), 4, 1, SlotStatus.OPEN);

        for (var service : List.of(serviceA1, serviceA2, serviceB1)) {
            var option = new ServiceOptionJpaEntity();
            option.setServiceId(service.getId());
            option.setName("Shared tour");
            option.setOptionType(OptionType.SHARED);
            option.setPricingUnit(PricingUnit.PER_PERSON);
            option.setPrice(service.getPrice());
            option.setStatus(OptionStatus.ACTIVE);
            optionRepository.saveAndFlush(option);
        }

        // 4. Orders & Receipts
        // Paid Order
        paidOrder = new MasterOrderJpaEntity();
        paidOrder.setCustomerId(customerUser.getId());
        paidOrder.setStatus(MasterOrderStatus.PAID);
        paidOrder.setPaymentStatus(PaymentOrderStatus.PAID);
        paidOrder.setTotalAmount(new BigDecimal("900000.00"));
        paidOrder.setDiscountAmount(new BigDecimal("100000.00"));
        paidOrder.setPaymentDeadline(OffsetDateTime.now().plusDays(1));
        paidOrder = masterOrderRepository.save(paidOrder);

        paidSubOrder = new SubOrderJpaEntity();
        paidSubOrder.setMasterOrderId(paidOrder.getId());
        paidSubOrder.setVendorId(vendorProfileA.getId());
        paidSubOrder.setServiceId(serviceA1.getId());
        paidSubOrder.setSlotId(slotA1TomorrowMorning.getId());
        paidSubOrder.setQuantity(2);
        paidSubOrder.setUnitPrice(new BigDecimal("500000.00"));
        paidSubOrder.setSubtotalAmount(new BigDecimal("1000000.00"));
        paidSubOrder.setDiscountAmount(new BigDecimal("100000.00"));
        paidSubOrder.setFinalAmount(new BigDecimal("900000.00"));
        paidSubOrder.setStatus(SubOrderStatus.CONFIRMED);
        paidSubOrder = subOrderRepository.save(paidSubOrder);

        paidPayment = new PaymentJpaEntity();
        paidPayment.setMasterOrderId(paidOrder.getId());
        paidPayment.setProvider(PaymentProvider.VNPAY);
        paidPayment.setProviderTransactionId("VNP123456789");
        paidPayment.setAmount(new BigDecimal("900000.00"));
        paidPayment.setStatus(PaymentStatus.SUCCESS);
        paidPayment.setPaidAt(OffsetDateTime.now().minusMinutes(10));
        paidPayment = paymentRepository.save(paidPayment);

        // Unpaid Order
        unpaidOrder = new MasterOrderJpaEntity();
        unpaidOrder.setCustomerId(customerUser.getId());
        unpaidOrder.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        unpaidOrder.setPaymentStatus(PaymentOrderStatus.UNPAID);
        unpaidOrder.setTotalAmount(new BigDecimal("250000.00"));
        unpaidOrder.setDiscountAmount(BigDecimal.ZERO);
        unpaidOrder = masterOrderRepository.save(unpaidOrder);

        unpaidSubOrder = new SubOrderJpaEntity();
        unpaidSubOrder.setMasterOrderId(unpaidOrder.getId());
        unpaidSubOrder.setVendorId(vendorProfileA.getId());
        unpaidSubOrder.setServiceId(serviceA2.getId());
        unpaidSubOrder.setSlotId(slotA2TomorrowEarly.getId());
        unpaidSubOrder.setQuantity(1);
        unpaidSubOrder.setUnitPrice(new BigDecimal("250000.00"));
        unpaidSubOrder.setSubtotalAmount(new BigDecimal("250000.00"));
        unpaidSubOrder.setStatus(SubOrderStatus.PENDING);
        unpaidSubOrder = subOrderRepository.save(unpaidSubOrder);

        // Cancelled Order
        cancelledOrder = new MasterOrderJpaEntity();
        cancelledOrder.setCustomerId(customerUser.getId());
        cancelledOrder.setStatus(MasterOrderStatus.CANCELLED);
        cancelledOrder.setPaymentStatus(PaymentOrderStatus.UNPAID);
        cancelledOrder.setTotalAmount(new BigDecimal("800000.00"));
        cancelledOrder = masterOrderRepository.save(cancelledOrder);

        cancelledSubOrder = new SubOrderJpaEntity();
        cancelledSubOrder.setMasterOrderId(cancelledOrder.getId());
        cancelledSubOrder.setVendorId(vendorProfileB.getId());
        cancelledSubOrder.setServiceId(serviceB1.getId());
        cancelledSubOrder.setSlotId(slotB1TomorrowLate.getId());
        cancelledSubOrder.setQuantity(1);
        cancelledSubOrder.setUnitPrice(new BigDecimal("800000.00"));
        cancelledSubOrder.setSubtotalAmount(new BigDecimal("800000.00"));
        cancelledSubOrder.setStatus(SubOrderStatus.CANCELLED);
        cancelledSubOrder = subOrderRepository.save(cancelledSubOrder);

        // 5. Initial Notification for Customer
        sampleNotificationCustomer = new NotificationJpaEntity();
        sampleNotificationCustomer.setUserId(customerUser.getId());
        sampleNotificationCustomer.setType("SYSTEM_ALERT");
        sampleNotificationCustomer.setChannel(NotificationChannel.IN_APP);
        sampleNotificationCustomer.setTitle("Chào mừng bạn đến với Danasea");
        sampleNotificationCustomer.setBody("Chúc bạn có những chuyến trải nghiệm biển tuyệt vời tại Đà Nẵng.");
        sampleNotificationCustomer.setStatus(NotificationStatus.SENT);
        sampleNotificationCustomer.setSentAt(OffsetDateTime.now());
        sampleNotificationCustomer = notificationRepository.save(sampleNotificationCustomer);
    }

    protected void generateAuthTokens() throws Exception {
        if (jwtTokenProvider != null) {
            customerToken = generateTokenDirectly(customerUser);
            attackerToken = generateTokenDirectly(attackerUser);
            adminToken = generateTokenDirectly(adminUser);
            vendorAToken = generateTokenDirectly(vendorUserA);
            vendorBToken = generateTokenDirectly(vendorUserB);
        } else {
            customerToken = obtainJwtToken("customer.e2e@danasea.vn", "Password123!");
            attackerToken = obtainJwtToken("attacker.e2e@danasea.vn", "Password123!");
            adminToken = obtainJwtToken("admin.e2e@danasea.vn", "Password123!");
            vendorAToken = obtainJwtToken("vendor.a.e2e@danasea.vn", "Password123!");
            vendorBToken = obtainJwtToken("vendor.b.e2e@danasea.vn", "Password123!");
        }
    }

    protected String generateTokenDirectly(UserJpaEntity user) {
        return jwtTokenProvider.generateAccessToken(new Authentication(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().name(),
                true,
                true,
                "vi",
                0L
        ));
    }

    protected String obtainJwtToken(String email, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("accessToken").asText();
    }

    protected UserJpaEntity createUser(String email, String fullName, Role role) {
        UserJpaEntity user = new UserJpaEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFullName(fullName);
        user.setRole(role);
        user.setIsEmailVerified(true);
        user.setIsLocked(false);
        return userRepository.save(user);
    }

    protected VendorJpaEntity createVendorProfile(UUID userId, String businessName, BadgeTier badgeTier,
                                                  BigDecimal ratingAvg, Integer ratingCount,
                                                  String bankAccount, String taxCode) {
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setUserId(userId);
        vendor.setBusinessName(businessName);
        vendor.setBadgeTier(badgeTier);
        vendor.setRatingAvg(ratingAvg);
        vendor.setRatingCount(ratingCount);
        vendor.setBankAccountNumber(bankAccount);
        vendor.setBankName("Vietcombank");
        vendor.setBankAccountHolder("DANASEA PARTNER");
        vendor.setTaxCode(taxCode);
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor.setAddress("Sơn Trà, Đà Nẵng");
        return vendorRepository.save(vendor);
    }

    protected ServiceSlotJpaEntity createSlot(UUID serviceId, LocalDate date, LocalTime start, LocalTime end,
                                              int capacity, int booked, SlotStatus status) {
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setServiceId(serviceId);
        slot.setDate(date);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setCapacity(capacity);
        slot.setBookedCount(booked);
        slot.setStatus(status);
        slot.setInventoryType(InventoryType.PERSON_LIMIT);
        return serviceSlotRepository.save(slot);
    }

    // --- Progressive Testability & Reflection Helpers ---

    public boolean isEndpointRegistered(String pathPattern, RequestMethod requestMethod) {
        if (handlerMapping == null) {
            return false;
        }
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            var info = entry.getKey();
            var patterns = info.getPatternValues();
            var methods = info.getMethodsCondition().getMethods();

            for (String pattern : patterns) {
                if (pathMatcher.match(pattern, pathPattern) || pattern.equals(pathPattern)) {
                    if (methods.isEmpty() || methods.contains(requestMethod)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void assumeEndpoint(String pathPattern, RequestMethod requestMethod) {
        if (!isEndpointRegistered(pathPattern, requestMethod)) {
            boolean strict = Boolean.getBoolean("e2e.strict");
            if (strict) {
                Assertions.fail("Strict Mode: Endpoint " + requestMethod + " " + pathPattern + " is not registered!");
            }
            Assumptions.assumeTrue(false,
                    "Endpoint " + requestMethod + " " + pathPattern + " is pending implementation in current milestone.");
        }
    }

    public boolean isParamSupportedOnController(Class<?> controllerClass, String methodName, String paramName) {
        try {
            for (Method method : controllerClass.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    for (var param : method.getParameters()) {
                        var requestParam = param.getAnnotation(RequestParam.class);
                        if (requestParam != null && (paramName.equals(requestParam.value()) ||
                                paramName.equals(requestParam.name()) ||
                                paramName.equals(param.getName()))) {
                            return true;
                        }
                        if (param.getName().equals(paramName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void assumeParamSupported(Class<?> controllerClass, String methodName, String paramName) {
        if (!isParamSupportedOnController(controllerClass, methodName, paramName)) {
            boolean strict = Boolean.getBoolean("e2e.strict");
            if (strict) {
                Assertions.fail("Strict Mode: Parameter '" + paramName + "' on " + controllerClass.getSimpleName() + "." + methodName + " is not supported!");
            }
            Assumptions.assumeTrue(false,
                    "Parameter '" + paramName + "' on " + controllerClass.getSimpleName() + "." + methodName + " is pending implementation.");
        }
    }

    public boolean isFieldPresent(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return field != null;
        } catch (NoSuchFieldException e) {
            // Check record components if record
            if (clazz.isRecord()) {
                for (var rc : clazz.getRecordComponents()) {
                    if (rc.getName().equals(fieldName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public void assumeFieldPresent(Class<?> clazz, String fieldName) {
        if (!isFieldPresent(clazz, fieldName)) {
            boolean strict = Boolean.getBoolean("e2e.strict");
            if (strict) {
                Assertions.fail("Strict Mode: Field '" + fieldName + "' on " + clazz.getSimpleName() + " is not present!");
            }
            Assumptions.assumeTrue(false,
                    "Field '" + fieldName + "' on " + clazz.getSimpleName() + " is pending implementation.");
        }
    }

    public boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void assumeClassPresent(String className) {
        if (!isClassPresent(className)) {
            boolean strict = Boolean.getBoolean("e2e.strict");
            if (strict) {
                Assertions.fail("Strict Mode: Class '" + className + "' is not present on classpath!");
            }
            Assumptions.assumeTrue(false,
                    "Class '" + className + "' is pending implementation in current milestone.");
        }
    }

    public boolean isBeanAvailable(String beanName) {
        return applicationContext != null && applicationContext.containsBean(beanName);
    }

    public void assumeBean(String beanName) {
        if (!isBeanAvailable(beanName)) {
            boolean strict = Boolean.getBoolean("e2e.strict");
            if (strict) {
                Assertions.fail("Strict Mode: Spring Bean '" + beanName + "' is not available in ApplicationContext!");
            }
            Assumptions.assumeTrue(false,
                    "Spring Bean '" + beanName + "' is pending implementation in current milestone.");
        }
    }
}

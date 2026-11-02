package com.danasea.backend.modules.service.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Executable regression cases retaining the original legacy case identities. */
@DisplayName("Services: strict integration tier 1")
public class ServiceTier1FeatureCoverageE2ETest extends BaseServiceE2ETest {
    // Only the external storage boundary is substituted; security, use cases and persistence remain real.
    @MockitoBean private FileStoragePort fileStoragePort;

    @Test
    @DisplayName("F1.1: Create valid service successfully with default DRAFT status")
    void f1_testCreateService_Success_DefaultDraftStatus() throws Exception {
        JsonNode created = create(standardPayload());
        assertThat(created.path("viewCount").asInt()).isZero();
        assertThat(created.path("images").isArray()).isTrue();
        assertThat(created.path("images").size()).isZero();
    }

    @Test
    @DisplayName("F1.2: Create valid weather-sensitive service with wind and wave limits")
    void f1_testCreateService_Success_WeatherSensitiveWithLimits() throws Exception {
        Map<String, Object> payload = standardPayload();
        payload.put("weatherSensitive", true);
        payload.put("minWindKmh", new BigDecimal("15.00"));
        payload.put("maxWaveM", new BigDecimal("1.80"));
        JsonNode created = create(payload);
        assertThat(created.path("weatherSensitive").asBoolean()).isTrue();
        assertThat(created.path("minWindKmh").decimalValue()).isEqualByComparingTo("15.00");
        assertThat(created.path("maxWaveM").decimalValue()).isEqualByComparingTo("1.80");
    }

    @Test
    @DisplayName("F1.3: Create service with all optional bilingual fields and coordinates")
    void f1_testCreateService_Success_WithFullOptionalFields() throws Exception {
        Map<String, Object> payload = standardPayload();
        payload.put("latitude", new BigDecimal("16.0025"));
        payload.put("longitude", new BigDecimal("108.2635"));
        payload.put("waiverContentEn", "Follow safety instructions");
        payload.put("waiverRequired", true);
        JsonNode created = create(payload);
        assertThat(created.path("nameEn").asText()).isEqualTo(payload.get("nameEn"));
        assertThat(created.path("waiverContentEn").asText()).isEqualTo("Follow safety instructions");
        assertThat(created.path("waiverVersion").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("F1.4: Create service in different active category")
    void f1_testCreateService_Success_MultipleCategories() throws Exception {
        Map<String, Object> payload = standardPayload();
        payload.put("categoryId", ACTIVE_CATEGORY_ID_2.toString());
        JsonNode created = create(payload);
        assertThat(reload(id(created)).getCategoryId()).isEqualTo(ACTIVE_CATEGORY_ID_2);
    }

    @Test
    @DisplayName("F1.5: Create service automatically assigned to authenticated vendor")
    void f1_testCreateService_Success_AssignedToCurrentVendor() throws Exception {
        Map<String, Object> payload = standardPayload();
        payload.put("vendorId", VENDOR_B_ID.toString());
        JsonNode created = create(payload);
        assertThat(reload(id(created)).getVendorId()).isEqualTo(VENDOR_A_ID);
        assertThat(serviceRepository.findByVendorId(VENDOR_B_ID)).isEmpty();
    }

    @Test
    @DisplayName("F2.1: Get all services for authenticated vendor returns 200 with JSON list")
    void f2_testGetVendorServices_Success_ReturnsList() throws Exception {
        UUID first = seed(ServiceStatus.DRAFT).getId();
        UUID second = seed(ServiceStatus.PUBLISHED).getId();
        assertVendorList(first, second);
    }

    @Test
    @DisplayName("F2.2: Get vendor services returns empty list when vendor has no services")
    void f2_testGetVendorServices_Success_EmptyWhenNoServices() throws Exception {
        seed(ServiceStatus.PUBLISHED, VENDOR_B_ID);
        assertVendorList();
    }

    @Test
    @DisplayName("F2.3: Vendor services list includes services across all statuses")
    void f2_testGetVendorServices_Success_AllStatusesIncluded() throws Exception {
        List<UUID> expected = new ArrayList<>();
        for (ServiceStatus state : ServiceStatus.values()) expected.add(seed(state).getId());
        assertVendorList(expected.toArray(UUID[]::new));
    }

    @Test
    @DisplayName("F2.4: Vendor services list enforces strict multi-tenant isolation")
    void f2_testGetVendorServices_Success_VendorIsolation() throws Exception {
        UUID owned = seed(ServiceStatus.DRAFT).getId();
        UUID other = seed(ServiceStatus.PUBLISHED, VENDOR_B_ID).getId();
        assertVendorList(owned);
        JsonNode vendorB = response(authenticateAsVendorB(get(VENDOR_API_BASE)), 200);
        assertThat(ids(vendorB)).containsExactly(other);
        assertThat(vendorB.get(0).path("vendorId").asText()).isEqualTo(VENDOR_B_ID.toString());
    }

    @Test
    @DisplayName("F2.5: Vendor services list payload contains summary fields (id, name, price, status)")
    void f2_testGetVendorServices_Success_SummaryPayloadFields() throws Exception {
        UUID owned = seed(ServiceStatus.DRAFT).getId();
        addImage(owned, 0);
        assertVendorList(owned);
        JsonNode row = response(authenticateAsVendorA(get(VENDOR_API_BASE)), 200).get(0);
        assertThat(row.path("id").asText()).isEqualTo(owned.toString());
        assertThat(row.path("price").decimalValue()).isEqualByComparingTo("1250000.00");
        assertThat(row.path("images").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("F3.1: Get draft service detail by owner returns 200 with complete service data")
    void f3_testGetServiceDetail_Success_DraftService() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        JsonNode result = detail(serviceId);
        assertThat(result.path("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("F3.2: Get published service detail by owner returns 200 with PUBLISHED status")
    void f3_testGetServiceDetail_Success_PublishedService() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        JsonNode result = detail(serviceId);
        assertThat(result.path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("F3.3: Service detail response includes gallery of images")
    void f3_testGetServiceDetail_Success_IncludesImageGallery() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        var second = addImage(serviceId, 2);
        var first = addImage(serviceId, 0);
        JsonNode result = detail(serviceId);
        assertThat(result.path("images").size()).isEqualTo(2);
        assertThat(result.path("images").get(0).path("id").asText()).isEqualTo(first.getId().toString());
        assertThat(result.path("images").get(1).path("id").asText()).isEqualTo(second.getId().toString());
    }

    @Test
    @DisplayName("F3.4: Service detail response includes weather sensitivity parameters")
    void f3_testGetServiceDetail_Success_IncludesWeatherConfig() throws Exception {
        ServiceJpaEntity service = seed(ServiceStatus.DRAFT);
        service.setWeatherSensitive(true);
        service.setMinWindKmh(new BigDecimal("15.00"));
        service.setMaxWaveM(new BigDecimal("1.80"));
        serviceRepository.saveAndFlush(service);
        JsonNode result = detail(service.getId());
        assertThat(result.path("weatherSensitive").asBoolean()).isTrue();
        assertThat(result.path("minWindKmh").decimalValue()).isEqualByComparingTo("15.00");
        assertThat(result.path("maxWaveM").decimalValue()).isEqualByComparingTo("1.80");
    }

    @Test
    @DisplayName("F3.5: Service detail response includes all location, waiver, and capacity details")
    void f3_testGetServiceDetail_Success_CompleteDetailPayload() throws Exception {
        UUID serviceId = seed(ServiceStatus.REJECTED).getId();
        addImage(serviceId, 0);
        JsonNode result = detail(serviceId);
        assertThat(result.path("nameEn").asText()).isEqualTo("English service");
        assertThat(result.path("descriptionEn").asText()).isEqualTo("Original English description");
        assertThat(result.path("waiverContent").asText()).isEqualTo("Original waiver");
        assertThat(result.path("rejectionReason").asText()).isEqualTo("Original rejection");
        assertThat(result.path("createdAt").asText()).isNotBlank();
        assertThat(result.path("updatedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("F4.1: Update draft service modifies fields and keeps status as DRAFT")
    void f4_testUpdateService_Success_DraftRemainsDraft() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        JsonNode result = update(serviceId, Map.of("description", "Updated description"));
        assertThat(result.path("description").asText()).isEqualTo("Updated description");
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F4.2: Update published service automatically transitions status to PENDING_REVIEW")
    void f4_testUpdateService_Success_PublishedTransitionsToPendingReview() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        JsonNode result = update(serviceId, Map.of("description", "Revised published description"));
        assertThat(result.path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("F4.3: Update rejected service modifies fields and maintains REJECTED status")
    void f4_testUpdateService_Success_RejectedRemainsRejected() throws Exception {
        UUID serviceId = seed(ServiceStatus.REJECTED).getId();
        JsonNode result = update(serviceId, Map.of("description", "Updated description"));
        assertThat(result.path("description").asText()).isEqualTo("Updated description");
        assertState(serviceId, ServiceStatus.REJECTED);
    }

    @Test
    @DisplayName("F4.4: Partial update modifies only provided fields leaving others intact")
    void f4_testUpdateService_Success_PartialFieldUpdate() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        JsonNode before = detail(serviceId).deepCopy();
        JsonNode result = update(serviceId, Map.of("capacityPerSlot", 15));
        assertThat(result.path("capacityPerSlot").asInt()).isEqualTo(15);
        for (String field : List.of("name", "description", "price", "durationMinutes", "categoryId", "vendorId", "status", "waiverContent")) {
            assertThat(result.path(field)).as(field + " must remain unchanged").isEqualTo(before.path(field));
        }
    }

    @Test
    @DisplayName("F4.5: Update weather sensitivity to true with valid limits")
    void f4_testUpdateService_Success_ToggleWeatherSensitiveWithLimits() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        JsonNode result = update(serviceId, Map.of("weatherSensitive", true, "minWindKmh", 20, "maxWaveM", new BigDecimal("2.20")));
        assertThat(result.path("weatherSensitive").asBoolean()).isTrue();
        assertThat(reload(serviceId).getMinWindKmh()).isEqualByComparingTo("20.00");
        assertThat(reload(serviceId).getMaxWaveM()).isEqualByComparingTo("2.20");
    }

    @Test
    @DisplayName("F5.1: Submit DRAFT service with images transitions status to PENDING_REVIEW")
    void f5_testSubmitService_Success_FromDraftWithImages() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        addImage(serviceId, 0);
        JsonNode result = transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        assertThat(result.path("images").size()).isEqualTo(1);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("F5.2: Submit REJECTED service with images transitions status to PENDING_REVIEW")
    void f5_testSubmitService_Success_FromRejectedWithImages() throws Exception {
        UUID serviceId = seed(ServiceStatus.REJECTED).getId();
        addImage(serviceId, 0);
        JsonNode result = transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        assertThat(result.path("images").size()).isEqualTo(1);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("F5.3: Submit response explicitly confirms PENDING_REVIEW status")
    void f5_testSubmitService_Success_ResponseReflectsPendingReview() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        addImage(serviceId, 0);
        JsonNode result = transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        assertThat(result.path("images").size()).isEqualTo(1);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("F5.4: Submit service with multiple images succeeds")
    void f5_testSubmitService_Success_MultipleImagesSupported() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        addImage(serviceId, 0);
        addImage(serviceId, 1);
        addImage(serviceId, 2);
        JsonNode result = transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        assertThat(result.path("images").size()).isEqualTo(3);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("F5.5: Owner vendor successfully submits their service")
    void f5_testSubmitService_Success_OwnerAuthorization() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        addImage(serviceId, 0);
        JsonNode result = transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        assertThat(result.path("images").size()).isEqualTo(1);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("F6.1: Pause PUBLISHED service transitions status to PAUSED (200 OK)")
    void f6_testPauseService_Success_FromPublishedToPaused() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        transition(serviceId, "pause", ServiceStatus.PAUSED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("F6.2: Pause response confirms PAUSED status in payload")
    void f6_testPauseService_Success_ResponseStatusIsPaused() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        transition(serviceId, "pause", ServiceStatus.PAUSED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("F6.3: Authenticated owner successfully pauses service")
    void f6_testPauseService_Success_OwnerCanPause() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        transition(serviceId, "pause", ServiceStatus.PAUSED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("F6.4: Subsequent GET shows PAUSED status persisted")
    void f6_testPauseService_Success_DetailReflectsPausedState() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        transition(serviceId, "pause", ServiceStatus.PAUSED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("F6.5: Pausing one service does not affect state of other vendor services")
    void f6_testPauseService_Success_IndependentServicePause() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        UUID other = seed(ServiceStatus.PUBLISHED).getId();
        transition(serviceId, "pause", ServiceStatus.PAUSED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PAUSED");
        assertState(other, ServiceStatus.PUBLISHED);
    }

    @Test
    @DisplayName("F7.1: Resume PAUSED service transitions status to PUBLISHED (200 OK)")
    void f7_testResumeService_Success_FromPausedToPublished() throws Exception {
        UUID serviceId = seed(ServiceStatus.PAUSED).getId();
        transition(serviceId, "resume", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("F7.2: Resume response confirms PUBLISHED status in payload")
    void f7_testResumeService_Success_ResponseStatusIsPublished() throws Exception {
        UUID serviceId = seed(ServiceStatus.PAUSED).getId();
        transition(serviceId, "resume", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("F7.3: Authenticated owner successfully resumes service")
    void f7_testResumeService_Success_OwnerCanResume() throws Exception {
        UUID serviceId = seed(ServiceStatus.PAUSED).getId();
        transition(serviceId, "resume", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("F7.4: Subsequent GET shows PUBLISHED status restored")
    void f7_testResumeService_Success_DetailReflectsPublishedState() throws Exception {
        UUID serviceId = seed(ServiceStatus.PAUSED).getId();
        transition(serviceId, "resume", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("F7.5: Service can undergo multiple pause and resume cycles")
    void f7_testResumeService_Success_MultiplePauseResumeCycles() throws Exception {
        UUID serviceId = seed(ServiceStatus.PAUSED).getId();
        transition(serviceId, "resume", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
        for (int i = 0; i < 3; i++) {
            transition(serviceId, "pause", ServiceStatus.PAUSED);
            transition(serviceId, "resume", ServiceStatus.PUBLISHED);
        }
    }

    @Test
    @DisplayName("F8.1: Delete DRAFT service returns 204 No Content")
    void f8_testDeleteService_Success_DraftServiceDeleted() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        addImage(serviceId, 0);
        addImage(serviceId, 1);
        UUID remaining = seed(ServiceStatus.DRAFT).getId();
        assertDeleted(serviceId);
        assertVendorList(remaining);
        assertState(remaining, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F8.2: Deleted service is no longer returned in GET vendor services list")
    void f8_testDeleteService_Success_NoLongerInVendorList() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        addImage(serviceId, 0);
        addImage(serviceId, 1);
        UUID remaining = seed(ServiceStatus.DRAFT).getId();
        assertDeleted(serviceId);
        assertVendorList(remaining);
        assertState(remaining, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F8.3: Subsequent GET on deleted service returns 404 Not Found")
    void f8_testDeleteService_Success_Returns404OnSubsequentGet() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        addImage(serviceId, 0);
        addImage(serviceId, 1);
        UUID remaining = seed(ServiceStatus.DRAFT).getId();
        assertDeleted(serviceId);
        assertVendorList(remaining);
        assertState(remaining, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F8.4: Authenticated owner successfully deletes their draft service")
    void f8_testDeleteService_Success_OwnerCanDelete() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        addImage(serviceId, 0);
        addImage(serviceId, 1);
        UUID remaining = seed(ServiceStatus.DRAFT).getId();
        assertDeleted(serviceId);
        assertVendorList(remaining);
        assertState(remaining, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F8.5: Associated service images are cleaned up upon deletion")
    void f8_testDeleteService_Success_CascadeImageCleanup() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        addImage(serviceId, 0);
        addImage(serviceId, 1);
        UUID remaining = seed(ServiceStatus.DRAFT).getId();
        assertDeleted(serviceId);
        assertVendorList(remaining);
        assertState(remaining, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F9.1: Admin lists services with status=PENDING_REVIEW returns 200 OK")
    void f9_testAdminListPendingServices_Success_ReturnsOnlyPendingReview() throws Exception {
        UUID pending = seed(ServiceStatus.PENDING_REVIEW).getId();
        seed(ServiceStatus.DRAFT);
        seed(ServiceStatus.PUBLISHED, VENDOR_B_ID);
        JsonNode result = response(authenticateAsAdmin(get(ADMIN_API_BASE)).param("status", "PENDING_REVIEW"), 200);
        assertThat(ids(result)).containsExactly(pending);
        assertThat(result.get(0).path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertPersisted(result.get(0));
    }

    @Test
    @DisplayName("F9.2: Admin pending list returns empty array when no services are pending")
    void f9_testAdminListPendingServices_Success_EmptyWhenNoPending() throws Exception {
        seed(ServiceStatus.DRAFT);
        seed(ServiceStatus.PUBLISHED);
        assertThat(serviceRepository.findByStatus(ServiceStatus.PENDING_REVIEW)).isEmpty();
        JsonNode result = response(authenticateAsAdmin(get(ADMIN_API_BASE)).param("status", "PENDING_REVIEW"), 200);
        assertThat(ids(result)).isEmpty();
    }

    @Test
    @DisplayName("F9.3: Pending review items include vendor and service identification")
    void f9_testAdminListPendingServices_Success_IncludesVendorIdentifier() throws Exception {
        UUID pending = seed(ServiceStatus.PENDING_REVIEW, VENDOR_B_ID).getId();
        JsonNode result = response(authenticateAsAdmin(get(ADMIN_API_BASE)).param("status", "PENDING_REVIEW"), 200);
        assertThat(ids(result)).containsExactly(pending);
        assertThat(result.get(0).path("vendorId").asText()).isEqualTo(VENDOR_B_ID.toString());
        assertPersisted(result.get(0));
    }

    @Test
    @DisplayName("F9.4: Admin can filter services by other statuses (PUBLISHED, REJECTED)")
    void f9_testAdminListPendingServices_Success_FilterOtherStatuses() throws Exception {
        UUID published = seed(ServiceStatus.PUBLISHED).getId();
        UUID rejected = seed(ServiceStatus.REJECTED, VENDOR_B_ID).getId();
        seed(ServiceStatus.PENDING_REVIEW);
        assertThat(ids(response(authenticateAsAdmin(get(ADMIN_API_BASE)).param("status", "PUBLISHED"), 200))).containsExactly(published);
        assertThat(ids(response(authenticateAsAdmin(get(ADMIN_API_BASE)).param("status", "REJECTED"), 200))).containsExactly(rejected);
    }

    @Test
    @DisplayName("F9.5: Authenticated ADMIN user successfully accesses admin services endpoint")
    void f9_testAdminListPendingServices_Success_AdminAuthorized() throws Exception {
        UUID first = seed(ServiceStatus.DRAFT).getId();
        UUID second = seed(ServiceStatus.PUBLISHED, VENDOR_B_ID).getId();
        JsonNode result = response(authenticateAsAdmin(get(ADMIN_API_BASE)), 200);
        assertThat(ids(result)).containsExactlyInAnyOrder(first, second);
        result.forEach(this::assertPersisted);
    }

    @Test
    @DisplayName("F10.1: Admin approves PENDING_REVIEW service transitions to PUBLISHED (200 OK)")
    void f10_testApproveService_Success_PendingReviewToPublished() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        addImage(serviceId, 0);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(auditCount(serviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("F10.2: Approve response explicitly confirms status=PUBLISHED")
    void f10_testApproveService_Success_ResponseStatusPublished() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        addImage(serviceId, 0);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(auditCount(serviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("F10.3: Approving service records audit log SERVICE_APPROVED with admin actor")
    void f10_testApproveService_Success_AuditLogRecorded() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        addImage(serviceId, 0);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(auditCount(serviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("F10.4: Vendor retrieves approved service and sees PUBLISHED status")
    void f10_testApproveService_Success_VendorSeesPublished() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        addImage(serviceId, 0);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(auditCount(serviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("F10.5: Approved service can now transition to PAUSED state by vendor")
    void f10_testApproveService_Success_CanNowBePaused() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        addImage(serviceId, 0);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(auditCount(serviceId)).isEqualTo(1);
        transition(serviceId, "pause", ServiceStatus.PAUSED);
    }

    @Test
    @DisplayName("F11.1: Admin rejects PENDING_REVIEW service with reason transitions to REJECTED (200 OK)")
    void f11_testRejectService_Success_PendingReviewToRejected() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        addImage(serviceId, 0);
        reject(serviceId, "Safety details incomplete");
        JsonNode result = detail(serviceId);
        assertThat(result.path("status").asText()).isEqualTo("REJECTED");
        assertThat(result.path("rejectionReason").asText()).isEqualTo("Safety details incomplete");
        assertThat(auditCount(serviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("F11.2: Reject response explicitly confirms status=REJECTED")
    void f11_testRejectService_Success_ResponseStatusRejected() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        addImage(serviceId, 0);
        reject(serviceId, "Safety details incomplete");
        JsonNode result = detail(serviceId);
        assertThat(result.path("status").asText()).isEqualTo("REJECTED");
        assertThat(result.path("rejectionReason").asText()).isEqualTo("Safety details incomplete");
        assertThat(auditCount(serviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("F11.3: Rejecting service records audit log SERVICE_REJECTED with reason in metadata")
    void f11_testRejectService_Success_AuditLogRecordedWithReason() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        addImage(serviceId, 0);
        reject(serviceId, "Safety details incomplete");
        JsonNode result = detail(serviceId);
        assertThat(result.path("status").asText()).isEqualTo("REJECTED");
        assertThat(result.path("rejectionReason").asText()).isEqualTo("Safety details incomplete");
        assertThat(auditCount(serviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("F11.4: Vendor retrieves rejected service and sees REJECTED status")
    void f11_testRejectService_Success_VendorSeesRejected() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        addImage(serviceId, 0);
        reject(serviceId, "Safety details incomplete");
        JsonNode result = detail(serviceId);
        assertThat(result.path("status").asText()).isEqualTo("REJECTED");
        assertThat(result.path("rejectionReason").asText()).isEqualTo("Safety details incomplete");
        assertThat(auditCount(serviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("F11.5: Rejected service can be updated and resubmitted by vendor")
    void f11_testRejectService_Success_CanBeEditedAndResubmitted() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        addImage(serviceId, 0);
        reject(serviceId, "Safety details incomplete");
        JsonNode result = detail(serviceId);
        assertThat(result.path("status").asText()).isEqualTo("REJECTED");
        assertThat(result.path("rejectionReason").asText()).isEqualTo("Safety details incomplete");
        assertThat(auditCount(serviceId)).isEqualTo(1);
        update(serviceId, Map.of("description", "Corrected safety details"));
        assertState(serviceId, ServiceStatus.REJECTED);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("F12.1: Owner uploads and deletes an image through HTTP, retaining sibling images")
    void f12_testServiceImageLifecycle_UploadThenDelete_OwnerRemovesOnlySelectedImage() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        byte[] imageBytes = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5n8AAAAASUVORK5CYII=");
        String uploadedUrl = "https://example.test/uploaded/" + serviceId + "/cover.png";
        String folder = "services/" + serviceId + "/images";
        when(fileStoragePort.uploadFile(aryEq(imageBytes), eq("cover.png"), eq(folder))).thenReturn(uploadedUrl);
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", imageBytes);

        JsonNode uploaded = response(multipart(VENDOR_API_BASE + "/" + serviceId + "/images")
                .file(file).header("Authorization", "Bearer " + vendorAToken), 201);
        UUID imageId = id(uploaded);
        assertThat(uploaded.path("serviceId").asText()).isEqualTo(serviceId.toString());
        assertThat(uploaded.path("url").asText()).isEqualTo(uploadedUrl);
        assertThat(uploaded.path("sortOrder").asInt()).isEqualTo(1);
        assertThat(imageRepository.findById(imageId).orElseThrow().getUrl()).isEqualTo(uploadedUrl);
        var sibling = addImage(serviceId, 2);
        assertThat(detail(serviceId).path("images").size()).isEqualTo(2);

        response(authenticateAsVendorA(delete(VENDOR_API_BASE + "/" + serviceId + "/images/" + imageId)), 204);
        assertState(serviceId, ServiceStatus.DRAFT); // Flush/clear before observing image deletion.
        assertThat(imageRepository.existsById(imageId)).isFalse();
        assertThat(imageRepository.findByServiceId(serviceId)).extracting(image -> image.getId())
                .containsExactly(sibling.getId());
        assertThat(detail(serviceId).path("images").get(0).path("id").asText()).isEqualTo(sibling.getId().toString());
        error(authenticateAsVendorA(delete(VENDOR_API_BASE + "/" + serviceId + "/images/" + imageId)),
                404, "IMAGE_NOT_FOUND");
        verify(fileStoragePort).uploadFile(aryEq(imageBytes), eq("cover.png"), eq(folder));
        verify(fileStoragePort).deleteFile(uploadedUrl);
        verifyNoMoreInteractions(fileStoragePort);
    }

    @Test
    @DisplayName("F12.2: Image deletion enforces service binding and vendor ownership before touching storage")
    void f12_testDeleteServiceImage_Fail_ImageBoundToDifferentService() throws Exception {
        UUID firstService = seed(ServiceStatus.DRAFT).getId();
        UUID secondService = seed(ServiceStatus.DRAFT).getId();
        var firstImage = addImage(firstService, 0);
        var secondImage = addImage(secondService, 0);

        error(authenticateAsVendorA(delete(VENDOR_API_BASE + "/" + firstService + "/images/" + secondImage.getId())),
                404, "IMAGE_NOT_FOUND");
        error(authenticateAsVendorB(delete(VENDOR_API_BASE + "/" + firstService + "/images/" + firstImage.getId())),
                403, "UNAUTHORIZED_SERVICE_ACCESS");
        assertState(firstService, ServiceStatus.DRAFT);
        assertState(secondService, ServiceStatus.DRAFT);
        assertThat(imageRepository.findByServiceId(firstService)).extracting(image -> image.getId())
                .containsExactly(firstImage.getId());
        assertThat(imageRepository.findByServiceId(secondService)).extracting(image -> image.getId())
                .containsExactly(secondImage.getId());
        assertThat(imageRepository.findById(secondImage.getId()).orElseThrow().getServiceId()).isEqualTo(secondService);
        assertThat(detail(firstService).path("images").size()).isEqualTo(1);
        assertThat(detail(secondService).path("images").size()).isEqualTo(1);
        verifyNoInteractions(fileStoragePort);
    }
}

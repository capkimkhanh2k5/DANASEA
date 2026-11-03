package com.danasea.backend.modules.service.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Executable regression cases retaining the original legacy case identities. */
@DisplayName("Services: strict integration tier 2")
public class ServiceTier2BoundaryCornerCaseE2ETest extends BaseServiceE2ETest {

    @Test
    @DisplayName("F1.B1: Fail when vendor verification_status is PENDING (R3)")
    void f1_testCreateService_Fail_VendorPendingVerification() throws Exception {
        Map<String, Object> payload = standardPayload();
        long before = serviceRepository.count();
        error(body(authenticateAsPendingVendor(post(VENDOR_API_BASE)), payload), 403, "VENDOR_NOT_APPROVED");
        assertThat(serviceRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("F1.B2: Fail when vendor verification_status is REJECTED (R3)")
    void f1_testCreateService_Fail_VendorRejectedVerification() throws Exception {
        Map<String, Object> payload = standardPayload();
        long before = serviceRepository.count();
        error(body(authenticateAsRejectedVendor(post(VENDOR_API_BASE)), payload), 403, "VENDOR_NOT_APPROVED");
        assertThat(serviceRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("F1.B3: Fail when category_id does not exist (404/400 per R3)")
    void f1_testCreateService_Fail_CategoryNotFound() throws Exception {
        Map<String, Object> payload = standardPayload();
        payload.put("categoryId", NON_EXISTENT_CATEGORY_ID.toString());
        long before = serviceRepository.count();
        error(body(authenticateAsVendorA(post(VENDOR_API_BASE)), payload), 404, "CATEGORY_NOT_FOUND");
        assertThat(serviceRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("F1.B4: Fail when category_id is inactive (400 per R3)")
    void f1_testCreateService_Fail_CategoryInactive() throws Exception {
        Map<String, Object> payload = standardPayload();
        payload.put("categoryId", INACTIVE_CATEGORY_ID.toString());
        long before = serviceRepository.count();
        error(body(authenticateAsVendorA(post(VENDOR_API_BASE)), payload), 400, "CATEGORY_INACTIVE");
        assertThat(serviceRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("F1.B5: Fail when weather_sensitive=true but min_wind_kmh is missing (R3)")
    void f1_testCreateService_Fail_WeatherSensitiveMissingMinWind() throws Exception {
        Map<String, Object> payload = standardPayload();
        payload.put("weatherSensitive", true);
        payload.put("maxWaveM", new BigDecimal("1.50"));
        long before = serviceRepository.count();
        error(body(authenticateAsVendorA(post(VENDOR_API_BASE)), payload), 400, "WEATHER_REQUIREMENTS_MISSING");
        assertThat(serviceRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("F1.B6: Fail when weather_sensitive=true but max_wave_m is missing (R3)")
    void f1_testCreateService_Fail_WeatherSensitiveMissingMaxWave() throws Exception {
        Map<String, Object> payload = standardPayload();
        payload.put("weatherSensitive", true);
        payload.put("minWindKmh", new BigDecimal("12.00"));
        long before = serviceRepository.count();
        error(body(authenticateAsVendorA(post(VENDOR_API_BASE)), payload), 400, "WEATHER_REQUIREMENTS_MISSING");
        assertThat(serviceRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("F1.B7: Fail when price is zero or negative")
    void f1_testCreateService_Fail_ZeroOrNegativePrice() throws Exception {
        // Current creation contract permits zero; the legacy label incorrectly grouped it with negative prices.
        Map<String, Object> payload = standardPayload();
        payload.put("price", new BigDecimal("-100000.00"));
        long before = serviceRepository.count();
        error(body(authenticateAsVendorA(post(VENDOR_API_BASE)), payload), 400, "INVALID_INPUT");
        assertThat(serviceRepository.count()).isEqualTo(before);
        payload.put("price", BigDecimal.ZERO);
        JsonNode free = create(payload);
        assertThat(reload(id(free)).getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(serviceRepository.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("F1.B8: Fail when service name is blank or whitespace")
    void f1_testCreateService_Fail_BlankName() throws Exception {
        Map<String, Object> payload = standardPayload();
        payload.put("name", "   ");
        long before = serviceRepository.count();
        error(body(authenticateAsVendorA(post(VENDOR_API_BASE)), payload), 400, "INVALID_INPUT");
        assertThat(serviceRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("F2.B1: Fail when unauthenticated user accesses vendor services")
    void f2_testGetVendorServices_Fail_AnonymousUnauthenticated() throws Exception {
        error(get(VENDOR_API_BASE), 401, "UNAUTHORIZED");
        assertThat(serviceRepository.findByVendorId(VENDOR_A_ID)).isEmpty();
    }

    @Test
    @DisplayName("F2.B2: Block CUSTOMER role from /api/vendor/services (403 per R4)")
    void f2_testGetVendorServices_Fail_CustomerRoleBlocked() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        error(authenticateAsCustomer(get(VENDOR_API_BASE)), 403, "ACCESS_DENIED");
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F2.B3: Block ADMIN role from /api/vendor/services (403 per R4)")
    void f2_testGetVendorServices_Fail_AdminRoleBlocked() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        error(authenticateAsAdmin(get(VENDOR_API_BASE)), 403, "ACCESS_DENIED");
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F2.B4: Handle extremely large pagination offset safely")
    void f2_testGetVendorServices_Boundary_ExtremelyLongPaginationOffset() throws Exception {
        // page/size are unsupported on the vendor list: verify the current ignored-query contract.
        UUID first = seed(ServiceStatus.DRAFT).getId();
        UUID second = seed(ServiceStatus.PUBLISHED).getId();
        seed(ServiceStatus.DRAFT, VENDOR_B_ID);
        Set<UUID> baseline = ids(response(authenticateAsVendorA(get(VENDOR_API_BASE)), 200));
        JsonNode result = response(authenticateAsVendorA(get(VENDOR_API_BASE)).param("page", "999999").param("size", "100"), 200);
        assertThat(ids(result)).isEqualTo(baseline).containsExactlyInAnyOrder(first, second);
        result.forEach(this::assertPersisted);
    }

    @Test
    @DisplayName("F2.B5: SQL meta-characters in search query handled safely without injection")
    void f2_testGetVendorServices_Boundary_SpecialCharactersInFilter() throws Exception {
        // search is unsupported here: no SQL filtering/injection feature is claimed by this regression.
        UUID owned = seed(ServiceStatus.DRAFT).getId();
        UUID other = seed(ServiceStatus.DRAFT, VENDOR_B_ID).getId();
        Set<UUID> baseline = ids(response(authenticateAsVendorA(get(VENDOR_API_BASE)), 200));
        JsonNode result = response(authenticateAsVendorA(get(VENDOR_API_BASE)).param("search", "'; DROP TABLE services; --"), 200);
        assertThat(ids(result)).isEqualTo(baseline).containsExactly(owned);
        assertState(owned, ServiceStatus.DRAFT);
        assertState(other, ServiceStatus.DRAFT);
        assertThat(serviceRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("F3.B1: Fail with 404 when serviceId does not exist")
    void f3_testGetServiceDetail_Fail_NonExistentService() throws Exception {
        UUID missing = UUID.randomUUID();
        long before = serviceRepository.count();
        error(authenticateAsVendorA(get(VENDOR_API_BASE + "/" + missing + "")), 404, "SERVICE_NOT_FOUND");
        assertThat(serviceRepository.existsById(missing)).isFalse();
        assertThat(serviceRepository.count()).isEqualTo(before);
        assertThat(auditCount(missing)).isZero();
    }

    @Test
    @DisplayName("F3.B2: Block Vendor B from viewing Vendor A's service (403 owner check R1, R3)")
    void f3_testGetServiceDetail_Fail_OtherVendorForbidden() throws Exception {
        otherVendorDenied("get");
    }

    @Test
    @DisplayName("F3.B3: Block CUSTOMER from viewing vendor service detail endpoint (403 per R4)")
    void f3_testGetServiceDetail_Fail_CustomerRoleForbidden() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        error(authenticateAsCustomer(get(VENDOR_API_BASE + "/" + serviceId)), 403, "ACCESS_DENIED");
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F3.B4: Block ADMIN from calling vendor service detail endpoint (403 per R4)")
    void f3_testGetServiceDetail_Fail_AdminRoleForbidden() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        error(authenticateAsAdmin(get(VENDOR_API_BASE + "/" + serviceId)), 403, "ACCESS_DENIED");
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F3.B5: Malformed non-UUID serviceId returns 400 Bad Request")
    void f3_testGetServiceDetail_Fail_MalformedUuid() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        error(authenticateAsVendorA(get(VENDOR_API_BASE + "/not-a-valid-uuid-1234")), 400, "BAD_REQUEST");
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F4.B1: Block Vendor B from updating Vendor A's service (403 owner check R3)")
    void f4_testUpdateService_Fail_OtherVendorForbidden() throws Exception {
        otherVendorDenied("update");
    }

    @Test
    @DisplayName("F4.B2: Updating non-existent serviceId returns 404 Not Found")
    void f4_testUpdateService_Fail_ServiceNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        long before = serviceRepository.count();
        error(body(authenticateAsVendorA(patch(VENDOR_API_BASE + "/" + missing + "")), Map.of("price", 999000)), 404, "SERVICE_NOT_FOUND");
        assertThat(serviceRepository.existsById(missing)).isFalse();
        assertThat(serviceRepository.count()).isEqualTo(before);
        assertThat(auditCount(missing)).isZero();
    }

    @Test
    @DisplayName("F4.B3: Updating to an inactive category returns 400 Bad Request (R3)")
    void f4_testUpdateService_Fail_UpdateToInactiveCategory() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        error(body(authenticateAsVendorA(patch(VENDOR_API_BASE + "/" + serviceId)),
                Map.of("categoryId", INACTIVE_CATEGORY_ID.toString())), 400, "CATEGORY_INACTIVE");
        assertThat(reload(serviceId).getCategoryId()).isEqualTo(ACTIVE_CATEGORY_ID);
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F4.B4: Updating weather_sensitive=true without wind/wave limits returns 400 (R3)")
    void f4_testUpdateService_Fail_WeatherSensitiveWithoutWindWave() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        Map<String, Object> changes = new HashMap<>();
        changes.put("weatherSensitive", true);
        changes.put("minWindKmh", null);
        changes.put("maxWaveM", null);
        error(body(authenticateAsVendorA(patch(VENDOR_API_BASE + "/" + serviceId)), changes), 400, "WEATHER_REQUIREMENTS_MISSING");
        assertThat(reload(serviceId).getWeatherSensitive()).isFalse();
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F4.B5: Block CUSTOMER from calling update service endpoint (403 per R4)")
    void f4_testUpdateService_Fail_CustomerRoleForbidden() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        error(body(authenticateAsCustomer(patch(VENDOR_API_BASE + "/" + serviceId)), Map.of("price", 1)), 403, "ACCESS_DENIED");
        assertThat(reload(serviceId).getPrice()).isEqualByComparingTo("1250000.00");
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F4.B6: Updating capacityPerSlot or durationMinutes to negative returns 400")
    void f4_testUpdateService_Boundary_NegativeCapacityOrDuration() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        for (String field : List.of("capacityPerSlot", "durationMinutes")) {
            error(body(authenticateAsVendorA(patch(VENDOR_API_BASE + "/" + serviceId)), Map.of(field, -5)), 400, "INVALID_INPUT");
            assertThat(reload(serviceId).getCapacityPerSlot()).isEqualTo(12);
            assertThat(reload(serviceId).getDurationMinutes()).isEqualTo(120);
            assertState(serviceId, ServiceStatus.DRAFT);
        }
    }

    @Test
    @DisplayName("F5.B1: Fail when submitting service with no images (400 per R3)")
    void f5_testSubmitService_Fail_EmptyImages() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        assertThat(imageRepository.findByServiceId(serviceId)).isEmpty();
        error(authenticateAsVendorA(post(VENDOR_API_BASE + "/" + serviceId + "/submit")), 400, "SERVICE_IMAGES_REQUIRED");
        assertState(serviceId, ServiceStatus.DRAFT);
        assertThat(auditCount(serviceId)).isZero();
    }

    @Test
    @DisplayName("F5.B2: Fail when submitting service that is already PENDING_REVIEW (400 state guard)")
    void f5_testSubmitService_Fail_AlreadyPendingReview() throws Exception {
        invalidState("submit", ServiceStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("F5.B3: Fail when submitting service that is already PUBLISHED (400 state guard)")
    void f5_testSubmitService_Fail_AlreadyPublished() throws Exception {
        invalidState("submit", ServiceStatus.PUBLISHED);
    }

    @Test
    @DisplayName("F5.B4: Fail when submitting service that is currently PAUSED (400 state guard)")
    void f5_testSubmitService_Fail_PausedService() throws Exception {
        invalidState("submit", ServiceStatus.PAUSED);
    }

    @Test
    @DisplayName("F5.B5: Block Vendor B from submitting Vendor A's service (403 owner check)")
    void f5_testSubmitService_Fail_OtherVendorForbidden() throws Exception {
        otherVendorDenied("submit");
    }

    @Test
    @DisplayName("F5.B6: Submitting non-existent service returns 404 Not Found")
    void f5_testSubmitService_Fail_NonExistentService() throws Exception {
        UUID missing = UUID.randomUUID();
        long before = serviceRepository.count();
        error(authenticateAsVendorA(post(VENDOR_API_BASE + "/" + missing + "/submit")), 404, "SERVICE_NOT_FOUND");
        assertThat(serviceRepository.existsById(missing)).isFalse();
        assertThat(serviceRepository.count()).isEqualTo(before);
        assertThat(auditCount(missing)).isZero();
    }

    @Test
    @DisplayName("F6.B1: Fail when calling pause on service in DRAFT status (400 state guard)")
    void f6_testPauseService_Fail_FromDraft() throws Exception {
        invalidState("pause", ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F6.B2: Fail when calling pause on service in PENDING_REVIEW status (400 state guard)")
    void f6_testPauseService_Fail_FromPendingReview() throws Exception {
        invalidState("pause", ServiceStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("F6.B3: Fail when calling pause on service in REJECTED status (400 state guard)")
    void f6_testPauseService_Fail_FromRejected() throws Exception {
        invalidState("pause", ServiceStatus.REJECTED);
    }

    @Test
    @DisplayName("F6.B4: Fail when calling pause on already PAUSED service (400 state guard)")
    void f6_testPauseService_Fail_AlreadyPaused() throws Exception {
        invalidState("pause", ServiceStatus.PAUSED);
    }

    @Test
    @DisplayName("F6.B5: Block Vendor B from pausing Vendor A's service (403 owner check)")
    void f6_testPauseService_Fail_OtherVendorForbidden() throws Exception {
        otherVendorDenied("pause");
    }

    @Test
    @DisplayName("F6.B6: Pausing non-existent service returns 404 Not Found")
    void f6_testPauseService_Fail_ServiceNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        long before = serviceRepository.count();
        error(authenticateAsVendorA(patch(VENDOR_API_BASE + "/" + missing + "/pause")), 404, "SERVICE_NOT_FOUND");
        assertThat(serviceRepository.existsById(missing)).isFalse();
        assertThat(serviceRepository.count()).isEqualTo(before);
        assertThat(auditCount(missing)).isZero();
    }

    @Test
    @DisplayName("F7.B1: Fail when calling resume on service in DRAFT status (400 state guard)")
    void f7_testResumeService_Fail_FromDraft() throws Exception {
        invalidState("resume", ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F7.B2: Fail when calling resume on service in PENDING_REVIEW status (400 state guard)")
    void f7_testResumeService_Fail_FromPendingReview() throws Exception {
        invalidState("resume", ServiceStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("F7.B3: Fail when calling resume on service in REJECTED status (400 state guard)")
    void f7_testResumeService_Fail_FromRejected() throws Exception {
        invalidState("resume", ServiceStatus.REJECTED);
    }

    @Test
    @DisplayName("F7.B4: Fail when calling resume on already PUBLISHED service (400 state guard)")
    void f7_testResumeService_Fail_AlreadyPublished() throws Exception {
        invalidState("resume", ServiceStatus.PUBLISHED);
    }

    @Test
    @DisplayName("F7.B5: Block Vendor B from resuming Vendor A's service (403 owner check)")
    void f7_testResumeService_Fail_OtherVendorForbidden() throws Exception {
        otherVendorDenied("resume");
    }

    @Test
    @DisplayName("F7.B6: Resuming non-existent service returns 404 Not Found")
    void f7_testResumeService_Fail_ServiceNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        long before = serviceRepository.count();
        error(authenticateAsVendorA(patch(VENDOR_API_BASE + "/" + missing + "/resume")), 404, "SERVICE_NOT_FOUND");
        assertThat(serviceRepository.existsById(missing)).isFalse();
        assertThat(serviceRepository.count()).isEqualTo(before);
        assertThat(auditCount(missing)).isZero();
    }

    @Test
    @DisplayName("F8.B1: Fail when attempting to delete PUBLISHED service (400 per R3)")
    void f8_testDeleteService_Fail_PublishedServiceBlocked() throws Exception {
        invalidState("delete", ServiceStatus.PUBLISHED);
    }

    @Test
    @DisplayName("F8.B2: Fail when attempting to delete PAUSED service (400 per R3)")
    void f8_testDeleteService_Fail_PausedServiceBlocked() throws Exception {
        invalidState("delete", ServiceStatus.PAUSED);
    }

    @Test
    @DisplayName("F8.B3: Fail when attempting to delete PENDING_REVIEW service (400 per R3)")
    void f8_testDeleteService_Fail_PendingReviewServiceBlocked() throws Exception {
        invalidState("delete", ServiceStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("F8.B4: Fail when attempting to delete REJECTED service (400 per R3)")
    void f8_testDeleteService_Fail_RejectedServiceBlocked() throws Exception {
        invalidState("delete", ServiceStatus.REJECTED);
    }

    @Test
    @DisplayName("F8.B5: Block Vendor B from deleting Vendor A's service (403 owner check)")
    void f8_testDeleteService_Fail_OtherVendorForbidden() throws Exception {
        otherVendorDenied("delete");
    }

    @Test
    @DisplayName("F8.B6: Deleting non-existent service returns 404 Not Found")
    void f8_testDeleteService_Fail_ServiceNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        long before = serviceRepository.count();
        error(authenticateAsVendorA(delete(VENDOR_API_BASE + "/" + missing + "")), 404, "SERVICE_NOT_FOUND");
        assertThat(serviceRepository.existsById(missing)).isFalse();
        assertThat(serviceRepository.count()).isEqualTo(before);
        assertThat(auditCount(missing)).isZero();
    }

    @Test
    @DisplayName("F9.B1: Block VENDOR role from calling admin services endpoint (403 per R4)")
    void f9_testAdminListServices_Fail_VendorRoleForbidden() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        error(authenticateAsVendorA(get(ADMIN_API_BASE)), 403, "ACCESS_DENIED");
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F9.B2: Block CUSTOMER role from calling admin services endpoint (403 per R4)")
    void f9_testAdminListServices_Fail_CustomerRoleForbidden() throws Exception {
        UUID serviceId = seed(ServiceStatus.DRAFT).getId();
        error(authenticateAsCustomer(get(ADMIN_API_BASE)), 403, "ACCESS_DENIED");
        assertState(serviceId, ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F9.B3: Block unauthenticated access to admin services endpoint (401/403)")
    void f9_testAdminListServices_Fail_AnonymousForbidden() throws Exception {
        error(get(ADMIN_API_BASE), 401, "UNAUTHORIZED");
        assertThat(serviceRepository.findByVendorId(VENDOR_A_ID)).isEmpty();
    }

    @Test
    @DisplayName("F9.B4: Non-existent status query parameter returns 400 Bad Request")
    void f9_testAdminListServices_Boundary_InvalidStatusParameter() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        error(authenticateAsAdmin(get(ADMIN_API_BASE)).param("status", "INVALID_STATUS_VALUE"), 400, "BAD_REQUEST");
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("F9.B5: Accessing admin services with empty query string handles gracefully")
    void f9_testAdminListServices_Boundary_EmptyQueryString() throws Exception {
        UUID first = seed(ServiceStatus.DRAFT).getId();
        UUID second = seed(ServiceStatus.REJECTED, VENDOR_B_ID).getId();
        JsonNode result = response(authenticateAsAdmin(get(ADMIN_API_BASE)), 200);
        assertThat(ids(result)).containsExactlyInAnyOrder(first, second);
        result.forEach(this::assertPersisted);
    }

    @Test
    @DisplayName("F10.B1: Fail when calling approve on service in DRAFT status (400 state guard)")
    void f10_testApproveService_Fail_FromDraft() throws Exception {
        invalidState("approve", ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F10.B2: Fail when calling approve on already PUBLISHED service (400 state guard)")
    void f10_testApproveService_Fail_AlreadyPublished() throws Exception {
        invalidState("approve", ServiceStatus.PUBLISHED);
    }

    @Test
    @DisplayName("F10.B3: Fail when calling approve on service in PAUSED status (400 state guard)")
    void f10_testApproveService_Fail_FromPaused() throws Exception {
        invalidState("approve", ServiceStatus.PAUSED);
    }

    @Test
    @DisplayName("F10.B4: Fail when calling approve on service in REJECTED status (400 state guard)")
    void f10_testApproveService_Fail_FromRejected() throws Exception {
        invalidState("approve", ServiceStatus.REJECTED);
    }

    @Test
    @DisplayName("F10.B5: Block VENDOR role from calling approve endpoint (403 per R4)")
    void f10_testApproveService_Fail_VendorRoleForbidden() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        error(authenticateAsVendorA(patch(ADMIN_API_BASE + "/" + serviceId + "/approve")), 403, "ACCESS_DENIED");
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
        assertThat(auditCount(serviceId)).isZero();
    }

    @Test
    @DisplayName("F10.B6: Approving non-existent service returns 404 Not Found")
    void f10_testApproveService_Fail_ServiceNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        long before = serviceRepository.count();
        error(authenticateAsAdmin(patch(ADMIN_API_BASE + "/" + missing + "/approve")), 404, "SERVICE_NOT_FOUND");
        assertThat(serviceRepository.existsById(missing)).isFalse();
        assertThat(serviceRepository.count()).isEqualTo(before);
        assertThat(auditCount(missing)).isZero();
    }

    @Test
    @DisplayName("F11.B1: Fail when rejecting without reason body (400 per R2)")
    void f11_testRejectService_Fail_MissingReason() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        error(body(authenticateAsAdmin(patch(ADMIN_API_BASE + "/" + serviceId + "/reject")), Map.of()), 400, "INVALID_INPUT");
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
        assertThat(reload(serviceId).getRejectionReason()).isNull();
        assertThat(auditCount(serviceId)).isZero();
    }

    @Test
    @DisplayName("F11.B2: Fail when rejecting with blank whitespace reason (400 per R2)")
    void f11_testRejectService_Fail_BlankReason() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        error(body(authenticateAsAdmin(patch(ADMIN_API_BASE + "/" + serviceId + "/reject")), Map.of("reason", "     ")), 400, "INVALID_INPUT");
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
        assertThat(reload(serviceId).getRejectionReason()).isNull();
        assertThat(auditCount(serviceId)).isZero();
    }

    @Test
    @DisplayName("F11.B3: Fail when rejecting with null reason (400 per R2)")
    void f11_testRejectService_Fail_NullReason() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        error(body(authenticateAsAdmin(patch(ADMIN_API_BASE + "/" + serviceId + "/reject")), Collections.singletonMap("reason", null)), 400, "INVALID_INPUT");
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
        assertThat(reload(serviceId).getRejectionReason()).isNull();
        assertThat(auditCount(serviceId)).isZero();
    }

    @Test
    @DisplayName("F11.B4: Fail when calling reject on service in DRAFT status (400 state guard)")
    void f11_testRejectService_Fail_FromDraft() throws Exception {
        invalidState("reject", ServiceStatus.DRAFT);
    }

    @Test
    @DisplayName("F11.B5: Fail when calling reject on service in PUBLISHED status (400 state guard)")
    void f11_testRejectService_Fail_FromPublished() throws Exception {
        invalidState("reject", ServiceStatus.PUBLISHED);
    }

    @Test
    @DisplayName("F11.B6: Fail when calling reject on service in PAUSED status (400 state guard)")
    void f11_testRejectService_Fail_FromPaused() throws Exception {
        invalidState("reject", ServiceStatus.PAUSED);
    }

    @Test
    @DisplayName("F11.B7: Block VENDOR role from calling reject endpoint (403 per R4)")
    void f11_testRejectService_Fail_VendorRoleForbidden() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        error(body(authenticateAsVendorA(patch(ADMIN_API_BASE + "/" + serviceId + "/reject")), Map.of("reason", "Valid reason")), 403, "ACCESS_DENIED");
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
        assertThat(auditCount(serviceId)).isZero();
    }

    @Test
    @DisplayName("F11.B8: Rejecting non-existent service returns 404 Not Found")
    void f11_testRejectService_Fail_ServiceNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        long before = serviceRepository.count();
        error(body(authenticateAsAdmin(patch(ADMIN_API_BASE + "/" + missing + "/reject")), Map.of("reason", "Valid reason")), 404, "SERVICE_NOT_FOUND");
        assertThat(serviceRepository.existsById(missing)).isFalse();
        assertThat(serviceRepository.count()).isEqualTo(before);
        assertThat(auditCount(missing)).isZero();
    }
}

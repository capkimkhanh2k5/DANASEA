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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Executable regression cases retaining the original legacy case identities. */
@DisplayName("Services: strict integration tier 4")
public class ServiceTier4RealWorldScenarioE2ETest extends BaseServiceE2ETest {

    @Test
    @DisplayName("Scenario 1: Complete Vendor Onboarding and Service Operational Lifecycle")
    void scenario1_CompleteVendorOnboardingAndServiceLifecycle() throws Exception {
        // This legacy scenario starts after vendor approval; onboarding itself is outside its HTTP sequence.
        Map<String, Object> payload = standardPayload();
        payload.put("weatherSensitive", true);
        payload.put("minWindKmh", 15);
        payload.put("maxWaveM", new BigDecimal("1.50"));
        UUID serviceId = id(create(payload));
        assertVendorList(serviceId);
        addImage(serviceId, 0);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        JsonNode queue = response(authenticateAsAdmin(get(ADMIN_API_BASE)).param("status", "PENDING_REVIEW"), 200);
        assertThat(ids(queue)).containsExactly(serviceId);
        error(authenticateAsAdmin(patch(ADMIN_API_BASE + "/" + serviceId + "/approve")), 422, "SAFETY_DOCUMENT_REQUIRED");
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
        assertThat(auditCount(serviceId)).isZero();
        addApprovedSafetyDocument(serviceId);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        transition(serviceId, "pause", ServiceStatus.PAUSED);
        transition(serviceId, "resume", ServiceStatus.PUBLISHED);
        assertVendorList(serviceId);
        assertThat(auditCount(serviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Scenario 2: Service Rejection Remediation and Resubmission Lifecycle")
    void scenario2_RejectionRemediationAndResubmissionWorkflow() throws Exception {
        UUID serviceId = id(create(standardPayload()));
        addImage(serviceId, 0);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        reject(serviceId, "Life jackets and participant age missing");
        JsonNode rejected = detail(serviceId);
        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.path("rejectionReason").asText()).isEqualTo("Life jackets and participant age missing");
        update(serviceId, Map.of("waiverContent", "Life jackets required; participants must be 18",
                "description", "Safety equipment and age restrictions supplied"));
        assertState(serviceId, ServiceStatus.REJECTED);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        JsonNode published = transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        assertThat(published.path("rejectionReason").asText(null)).isNull();
        assertThat(reload(serviceId).getWaiverContent()).isEqualTo("Life jackets required; participants must be 18");
        assertThat(auditCount(serviceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("Scenario 3: Live Service Modification Auto-Triggers Admin Re-Review")
    void scenario3_LiveServiceModificationAutoTriggersAdminReReview() throws Exception {
        UUID serviceId = id(create(standardPayload()));
        addImage(serviceId, 0);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        update(serviceId, Map.of("price", 1950000, "durationMinutes", 150));
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
        JsonNode changed = detail(serviceId);
        assertThat(changed.path("price").decimalValue()).isEqualByComparingTo("1950000.00");
        assertThat(changed.path("durationMinutes").asInt()).isEqualTo(150);
        error(authenticateAsVendorA(delete(VENDOR_API_BASE + "/" + serviceId)), 400, "INVALID_SERVICE_STATE");
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
        JsonNode queue = response(authenticateAsAdmin(get(ADMIN_API_BASE)).param("status", "PENDING_REVIEW"), 200);
        assertThat(ids(queue)).containsExactly(serviceId);
        assertThat(queue.get(0).path("price").decimalValue()).isEqualByComparingTo("1950000.00");
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        assertThat(auditCount(serviceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("Scenario 4: Multi-Tenant Vendor Isolation and Malicious Exploit Prevention")
    void scenario4_MultiTenantIsolationAndMaliciousCrossVendorExploitation() throws Exception {
        UUID serviceId = id(create(standardPayload()));
        for (String action : List.of("get", "update", "pause", "delete")) {
            error(authenticateAsVendorB(vendorRequest(serviceId, action)), 403, "UNAUTHORIZED_SERVICE_ACCESS");
            assertState(serviceId, ServiceStatus.DRAFT);
            assertThat(reload(serviceId).getPrice()).isEqualByComparingTo("1250000.00");
        }
        error(authenticateAsCustomer(get(VENDOR_API_BASE)), 403, "ACCESS_DENIED");
        error(authenticateAsVendorA(patch(ADMIN_API_BASE + "/" + serviceId + "/approve")), 403, "ACCESS_DENIED");
        assertVendorList(serviceId);
        assertThat(ids(response(authenticateAsVendorB(get(VENDOR_API_BASE)), 200))).isEmpty();
        assertThat(auditCount(serviceId)).isZero();
    }

    @Test
    @DisplayName("Scenario 5: Weather-Sensitive Water Sports Lifecycle and Deletion State Guards")
    void scenario5_WeatherSensitiveWaterSportsLifecycleAndDeletionGuards() throws Exception {
        Map<String, Object> payload = standardPayload();
        payload.put("weatherSensitive", true);
        long before = serviceRepository.count();
        error(body(authenticateAsVendorA(post(VENDOR_API_BASE)), payload), 400, "WEATHER_REQUIREMENTS_MISSING");
        assertThat(serviceRepository.count()).isEqualTo(before);
        payload.put("minWindKmh", 18);
        payload.put("maxWaveM", 2);
        UUID serviceId = id(create(payload));
        addImage(serviceId, 0);
        addApprovedSafetyDocument(serviceId);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        error(authenticateAsVendorA(delete(VENDOR_API_BASE + "/" + serviceId)), 400, "INVALID_SERVICE_STATE");
        assertState(serviceId, ServiceStatus.PUBLISHED);
        transition(serviceId, "pause", ServiceStatus.PAUSED);
        error(authenticateAsVendorA(delete(VENDOR_API_BASE + "/" + serviceId)), 400, "INVALID_SERVICE_STATE");
        assertState(serviceId, ServiceStatus.PAUSED);
        UUID draft = id(create(standardPayload()));
        addImage(draft, 0);
        assertDeleted(draft);
        assertVendorList(serviceId);
        assertState(serviceId, ServiceStatus.PAUSED);
    }
}

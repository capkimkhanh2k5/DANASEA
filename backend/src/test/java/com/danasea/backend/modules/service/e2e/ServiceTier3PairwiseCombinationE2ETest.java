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
@DisplayName("Services: strict integration tier 3")
public class ServiceTier3PairwiseCombinationE2ETest extends BaseServiceE2ETest {

    @Test
    @DisplayName("P1: Complete Happy Path Lifecycle: Create -> Submit -> Approve -> Pause -> Resume")
    void testPairwise_Create_Then_Submit_Then_Approve_Then_Pause_Then_Resume() throws Exception {
        UUID serviceId = id(create(standardPayload()));
        addImage(serviceId, 0);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        transition(serviceId, "pause", ServiceStatus.PAUSED);
        transition(serviceId, "resume", ServiceStatus.PUBLISHED);
        assertThat(detail(serviceId).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(auditCount(serviceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("P2: Rejection Remediation Loop: Create -> Submit -> Reject -> Edit -> Resubmit -> Approve")
    void testPairwise_Create_Then_Submit_Then_Reject_Then_Update_Then_Resubmit_Then_Approve() throws Exception {
        UUID serviceId = id(create(standardPayload()));
        addImage(serviceId, 0);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        reject(serviceId, "Refund policy incomplete");
        update(serviceId, Map.of("description", "Refund policy corrected"));
        assertState(serviceId, ServiceStatus.REJECTED);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        JsonNode result = transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        assertThat(result.path("rejectionReason").asText(null)).isNull();
        assertThat(reload(serviceId).getDescription()).isEqualTo("Refund policy corrected");
        assertThat(auditCount(serviceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("P3: Live Service Modification Auto Re-review: Published -> Update -> Auto PENDING_REVIEW")
    void testPairwise_Create_Then_UpdateDraft_Then_Submit_Then_Approve_Then_UpdatePublished_AutoPendingReview() throws Exception {
        UUID serviceId = id(create(standardPayload()));
        update(serviceId, Map.of("name", "Updated draft name"));
        assertState(serviceId, ServiceStatus.DRAFT);
        addImage(serviceId, 0);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        update(serviceId, Map.of("price", 1500000));
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
        assertThat(detail(serviceId).path("price").decimalValue()).isEqualByComparingTo("1500000.00");
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        assertThat(auditCount(serviceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("P4: Deletion Constraints Across State Lifecycle: Only DRAFT deletable")
    void testPairwise_CreateDraft_Then_AttemptDelete_AcrossAllStateTransitions() throws Exception {
        UUID draft = id(create(standardPayload()));
        addImage(draft, 0);
        assertDeleted(draft);
        for (ServiceStatus state : List.of(ServiceStatus.PENDING_REVIEW, ServiceStatus.PUBLISHED, ServiceStatus.PAUSED, ServiceStatus.REJECTED)) {
            invalidState("delete", state);
        }
    }

    @Test
    @DisplayName("P5: Image Requirement Remediation: Submit with 0 images blocked -> Add images -> Submit succeeds")
    void testPairwise_SubmitWithoutImages_Blocked_Then_AddImages_SubmitSucceeds() throws Exception {
        UUID serviceId = id(create(standardPayload()));
        error(authenticateAsVendorA(post(VENDOR_API_BASE + "/" + serviceId + "/submit")), 400, "SERVICE_IMAGES_REQUIRED");
        assertState(serviceId, ServiceStatus.DRAFT);
        addImage(serviceId, 0);
        JsonNode result = transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        assertThat(result.path("images").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("P6: Audit Trail Recording for Sequential Admin Actions (Reject followed by Approve)")
    void testPairwise_AdminReject_AuditLog_Then_AdminApprove_AuditLog() throws Exception {
        UUID serviceId = id(create(standardPayload()));
        addImage(serviceId, 0);
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        reject(serviceId, "Safety equipment list incomplete");
        // Rejected services must be resubmitted; direct approval is forbidden by the current state machine.
        error(authenticateAsAdmin(patch(ADMIN_API_BASE + "/" + serviceId + "/approve")), 400, "INVALID_SERVICE_STATE");
        assertState(serviceId, ServiceStatus.REJECTED);
        assertThat(auditCount(serviceId)).isEqualTo(1);
        update(serviceId, Map.of("description", "Safety equipment supplied"));
        transition(serviceId, "submit", ServiceStatus.PENDING_REVIEW);
        transition(serviceId, "approve", ServiceStatus.PUBLISHED);
        assertThat(auditCount(serviceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("P7: Multi-Tenant Cross-Vendor Isolation Across All Operations")
    void testPairwise_MultiTenantCrossVendorIsolationMatrix() throws Exception {
        UUID serviceId = seed(ServiceStatus.PUBLISHED).getId();
        UUID vendorBService = seed(ServiceStatus.DRAFT, VENDOR_B_ID).getId();
        for (String action : List.of("get", "update", "submit", "pause", "resume", "delete")) {
            MockHttpServletRequestBuilder request = vendorRequest(serviceId, action);
            error(authenticateAsVendorB(request), 403, "UNAUTHORIZED_SERVICE_ACCESS");
            assertState(serviceId, ServiceStatus.PUBLISHED);
            assertThat(reload(serviceId).getPrice()).isEqualByComparingTo("1250000.00");
        }
        assertVendorList(serviceId);
        assertThat(ids(response(authenticateAsVendorB(get(VENDOR_API_BASE)), 200))).containsExactly(vendorBService);
        assertThat(auditCount(serviceId)).isZero();
    }

    @Test
    @DisplayName("P8: RBAC Security Matrix Across All Endpoints (Vendor vs Admin vs Customer vs Anon)")
    void testPairwise_RBAC_RolePermissionMatrix() throws Exception {
        UUID serviceId = seed(ServiceStatus.PENDING_REVIEW).getId();
        for (String action : List.of("create", "list", "get", "update", "submit", "pause", "resume", "delete")) {
            error(authenticateAsCustomer(vendorRequest(serviceId, action)), 403, "ACCESS_DENIED");
            error(authenticateAsAdmin(vendorRequest(serviceId, action)), 403, "ACCESS_DENIED");
            error(vendorRequest(serviceId, action), 401, "UNAUTHORIZED");
        }
        for (String action : List.of("list", "approve", "reject")) {
            error(authenticateAsVendorA(adminRequest(serviceId, action)), 403, "ACCESS_DENIED");
            error(authenticateAsCustomer(adminRequest(serviceId, action)), 403, "ACCESS_DENIED");
            error(adminRequest(serviceId, action), 401, "UNAUTHORIZED");
        }
        assertState(serviceId, ServiceStatus.PENDING_REVIEW);
        assertThat(serviceRepository.count()).isEqualTo(1);
        assertThat(auditCount(serviceId)).isZero();
    }
}

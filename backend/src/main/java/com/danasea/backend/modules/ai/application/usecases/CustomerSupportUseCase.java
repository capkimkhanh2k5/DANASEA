package com.danasea.backend.modules.ai.application.usecases;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.danasea.backend.modules.ai.application.ports.CustomerOrderReadPort.OwnedOrder;
import com.danasea.backend.modules.ai.application.ports.CustomerOrderReadPort;
import com.danasea.backend.modules.ai.application.ports.DecisionModelPort;
import com.danasea.backend.modules.ai.domain.models.DecisionResult;
import com.danasea.backend.modules.ai.domain.models.DecisionTask;
import com.danasea.backend.modules.order.application.dtos.CancellationPreviewResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerSupportUseCase {
    public record Action(String code, String method, String path, boolean requiresConfirmation,
                         boolean requiresIdempotencyKey, String eligibilitySource) {}
    public record Result(String status, OwnedOrder order, CancellationPreviewResult cancellationPreview,
                         String previewStatus, DecisionResult classification, List<String> requiredInputs,
                         List<String> nextActions, boolean financialActionPerformed,
                         List<Action> actionDetails, List<String> eligibilityReasons, OffsetDateTime checkedAt) {}
    private final CustomerOrderReadPort orders;
    private final DecisionModelPort decisions;

    public List<OwnedOrder> listOrders(UUID userId, int limit) {
        if (userId == null) throw new IllegalArgumentException("An authenticated customer is required");
        return orders.listRecent(userId, limit);
    }

    public Result execute(UUID userId, UUID orderId, String message, boolean includeCancellationPreview) {
        if (userId == null) throw new IllegalArgumentException("An authenticated customer is required");
        if (message == null || message.isBlank() || message.length() > 1800) throw new IllegalArgumentException("Support message must contain 1 to 1800 characters");
        if (orderId == null) {
            return new Result("NEEDS_INPUT", null, null, "NOT_REQUESTED", decide(message, null), List.of("orderId"),
                    List.of("SELECT_OWNED_ORDER"), false, List.of(), List.of("OWNED_ORDER_REQUIRED"), OffsetDateTime.now());
        }
        OwnedOrder owned = orders.read(userId, orderId);
        DecisionResult classification = decide(message, owned);
        CancellationPreviewResult preview = null;
        String previewStatus = "NOT_REQUESTED";
        if (includeCancellationPreview) {
            try { preview = orders.previewCancellation(userId, orderId); previewStatus = "CURRENT_POLICY_PREVIEW"; }
            catch (IllegalArgumentException exception) { previewStatus = "UNAVAILABLE_FOR_CURRENT_STATE"; }
        }
        var eligibility = orders.eligibility(userId, orderId, preview);
        if (eligibility == null) eligibility = new CustomerOrderReadPort.Eligibility(false, false, "UNAVAILABLE", List.of("ELIGIBILITY_NOT_VERIFIED"));
        List<Action> actions = new ArrayList<>();
        actions.add(new Action("CONTACT_SUPPORT", "POST", "/api/ai/support/requests", true, true, "AUTHENTICATED_OWNED_ORDER"));
        actions.add(new Action("VIEW_SUPPORT_REQUESTS", "GET", "/api/ai/support/requests", false, false, "AUTHENTICATED_OWNER"));
        actions.add(new Action("VIEW_CANCELLATION_PREVIEW", "GET", "/api/orders/" + orderId + "/cancellation-preview", false, false, "CORE_POLICY_PREVIEW"));
        if (eligibility.changeRequestAvailable()) actions.add(new Action("REQUEST_CHANGE_WITH_CONFIRMATION", "POST", "/api/ai/support/requests", true, true,
                "CURRENT_ORDER_STATE; HUMAN_APPROVAL_REQUIRED; NO_AUTOMATIC_RESCHEDULE"));
        if (eligibility.refundRequestAvailable()) actions.add(new Action("REQUEST_REFUND_WITH_CONFIRMATION", "POST", "/api/orders/" + orderId + "/refund-request", true, true, eligibility.source()));
        return new Result("AVAILABLE", owned, preview, previewStatus, classification, List.of(),
                actions.stream().map(Action::code).toList(), false, List.copyOf(actions), eligibility.reasonCodes(), OffsetDateTime.now());
    }

    private DecisionResult decide(String message, OwnedOrder order) {
        Map<String, Object> facts = order == null ? Map.of("complaint", message) : Map.of("complaint", message,
                "order", Map.of("status", order.status(), "paymentStatus", order.paymentStatus()));
        DecisionResult result = decisions.decide(DecisionTask.COMPLAINT, facts);
        return result == null ? DecisionResult.unavailable(DecisionTask.COMPLAINT, "MODEL_UNAVAILABLE") : result;
    }
}

package com.danasea.backend.modules.ai.application.usecases;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.ai.application.ports.TravelPolicyPort;
import com.danasea.backend.modules.dispute.application.services.DisputeWindowService;
import com.danasea.backend.modules.order.application.dtos.GetCancellationPreviewQuery;
import com.danasea.backend.modules.order.application.usecases.GetCancellationPreviewUseCase;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PolicyChatAnswer {
    private static final Pattern ORDER_ID = Pattern.compile(
            "(?i)(?<![0-9a-f])[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}(?![0-9a-f])");
    private static final Pattern POLICY = CopilotRequest.words(
            "chinh sach (?:huy|hoan|doi|doi tra)|quy dinh (?:huy|hoan|doi lich)|"
                    + "refund policy|cancellation policy|return policy|reschedul(?:e|ing) policy");
    private static final Pattern REFUND = CopilotRequest.words("hoan tien|hoan phi|hoan \\d+|refunds?|reimburse");
    private static final Pattern REPORT = CopilotRequest.words("doanh thu|gmv|bao cao|thong ke|ti le huy|ty le huy|"
            + "nguyen nhan nao|risk|rui ro|revenue|report|metrics");
    private static final Pattern CHANGE = CopilotRequest.words("doi lich|doi tra|reschedul(?:e|ing)");
    private static final Pattern HOURS = CopilotRequest.words("huy truoc|truoc gio khoi hanh|cancel (?:before|more than)|hours before");
    private static final Pattern DRAFT = CopilotRequest.words("viet|soan|tra loi khach|phan hoi|hua|cam ket|draft|promise");
    private static final Pattern AUTOMATIC_REFUND = CopilotRequest.words(
            "tu quyet dinh|tu dong hoan|tu hoan|bo qua (?:buoc )?(?:duyet|xac nhan|quy tac)|"
                    + "automatically refund|refund automatically|decide yourself|bypass approval");
    private static final Pattern DISPUTE_WINDOW = CopilotRequest.words("khieu nai|dispute");
    private static final Pattern WHEN = CopilotRequest.words("bao lau|thoi han|han gui|may ngay|bao nhieu ngay|deadline|how long|how many days");

    private final TravelPolicyPort policy;
    private final GetCancellationPreviewUseCase previews;
    private final LocalizedMessageService messages;

    record Reply(String message, String tool, String status, Map<String, Object> facts) {}

    static UUID reference(String message) {
        if (message == null) return null;
        var matcher = ORDER_ID.matcher(message);
        return matcher.find() ? UUID.fromString(matcher.group()) : null;
    }

    Reply resolve(String message, SupportedLanguage language, UUID actorId, UUID vendorId, boolean admin, UUID orderContext) {
        String text = CopilotRequest.normalize(message);
        boolean refund = REFUND.matcher(text).find();
        if (vendorId != null && refund && DRAFT.matcher(text).find()) {
            return new Reply(messages.get("ai.policy.vendor_draft_review", language), null, "ANSWERED", Map.of());
        }
        if (refund && AUTOMATIC_REFUND.matcher(text).find()) {
            return new Reply(messages.get("ai.policy.refund_requires_approval", language), null, "ANSWERED", Map.of());
        }
        if (DISPUTE_WINDOW.matcher(text).find() && WHEN.matcher(text).find()) {
            int days = DisputeWindowService.filingWindowDays();
            return new Reply(messages.get("ai.policy.dispute_window", language, days), "get_policy", "ANSWERED",
                    Map.of("disputeWindowDays", days, "source", "DisputeWindowService"));
        }
        if (REPORT.matcher(text).find() || !(POLICY.matcher(text).find() || refund || CHANGE.matcher(text).find()
                || HOURS.matcher(text).find())) {
            return null;
        }
        UUID orderId = reference(message);
        if (orderId == null) orderId = orderContext;
        if (orderId != null && (refund || POLICY.matcher(text).find() || HOURS.matcher(text).find())) {
            return preview(orderId, actorId, vendorId, admin, language);
        }
        if (!POLICY.matcher(text).find()
                && !HOURS.matcher(text).find()
                && !text.matches("^(?:hoan tien|refund|doi tra|doi lich)[?!.]*$")
                && !(refund && CopilotRequest.words("co duoc|bao nhieu|how much|can i|weather|thoi tiet|mua").matcher(text).find())
                && !(CHANGE.matcher(text).find() && CopilotRequest.words("mien phi|free").matcher(text).find())) {
            return null;
        }

        var snapshot = policy.current();
        var tiers = snapshot.customerCancellation();
        String answer = messages.get("ai.policy.standard", language,
                percentage(tiers.getFirst().percentage()), percentage(tiers.getLast().percentage()),
                percentage(snapshot.weatherOrVendorFaultPercentage()));
        if (HOURS.matcher(text).find()) {
            // The common policy is a two-tier policy. An existing order can still carry a different version.
            answer += "\n\n" + messages.get("ai.policy.order_version", language);
        }
        if (CHANGE.matcher(text).find()) {
            answer += "\n\n" + messages.get("ai.policy.reschedule", language);
        }
        return new Reply(answer, "get_policy", "ANSWERED",
                Map.of("policy", snapshot, "orderSpecificEligibilityChecked", false,
                        "changeRequestRequiresHumanApproval", true));
    }

    private Reply preview(UUID orderId, UUID actorId, UUID vendorId, boolean admin, SupportedLanguage language) {
        try {
            var result = previews.execute(new GetCancellationPreviewQuery(actorId, orderId, admin, vendorId));
            var currency = NumberFormat.getIntegerInstance(language.locale()).format(result.refundAmount());
            return new Reply(messages.get("ai.policy.order_preview", language, currency, percentage(result.refundPercentage())),
                    "ai_cancellation_preview", "ANSWERED",
                    Map.of("cancellationPreview", result, "financialActionPerformed", false));
        } catch (AccessDeniedException | OrderNotFoundException exception) {
            return new Reply(messages.get("ai.policy.order_unavailable", language),
                    "ai_cancellation_preview", "UNAVAILABLE", Map.of());
        }
    }

    private String percentage(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString() + "%";
    }
}

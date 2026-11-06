package com.danasea.backend.modules.communication.infrastructure.email.renderers;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.ResolvedRecipient;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CaseAndAccountEmailRenderer {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    private final LocalizedMessageService messages;
    private final TransactionalEmailProperties properties;
    private final JpaDisputeRepository disputeRepository;
    private final JpaSettlementRepository settlementRepository;
    private final JpaUserRepository userRepository;
    private final ObjectMapper objectMapper;

    public boolean supports(TransactionalEmailType type) {
        return type == TransactionalEmailType.DISPUTE_RESOLVED
                || type == TransactionalEmailType.PASSWORD_CHANGED
                || type == TransactionalEmailType.ACCOUNT_LOCKED
                || type == TransactionalEmailType.SECURITY_ALERT
                || type == TransactionalEmailType.SETTLEMENT_FINALIZED;
    }

    public RenderedEmail render(TransactionalEmailDeliveryJpaEntity entity, ResolvedRecipient recipient) {
        SupportedLanguage lang = "en".equalsIgnoreCase(recipient.locale())
                ? SupportedLanguage.EN
                : SupportedLanguage.VI;

        Map<String, Object> payload = parsePayload(entity.getPayload());

        return switch (entity.getType()) {
            case DISPUTE_RESOLVED -> renderDisputeResolved(entity, recipient, payload, lang);
            case PASSWORD_CHANGED -> renderPasswordChanged(entity, recipient, payload, lang);
            case ACCOUNT_LOCKED -> renderAccountLocked(entity, recipient, payload, lang);
            case SETTLEMENT_FINALIZED -> renderSettlementFinalized(entity, recipient, payload, lang);
            case SECURITY_ALERT -> renderSecurityAlert(entity, recipient, payload, lang);
            default -> throw new IllegalArgumentException("Unsupported email type: " + entity.getType());
        };
    }

    // =========================================================================
    // 1. DISPUTE_RESOLVED (Customer vs Vendor templates)
    // =========================================================================
    private RenderedEmail renderDisputeResolved(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String recipientRole = payload.get("recipientRole") != null ? payload.get("recipientRole").toString() : "CUSTOMER";
        boolean isVendor = "VENDOR".equalsIgnoreCase(recipientRole);

        String disputeId = payload.get("disputeId") != null ? payload.get("disputeId").toString() : "N/A";
        String resolution = payload.get("resolution") != null ? payload.get("resolution").toString() : "RESOLVED";
        String refundAmountStr = formatCurrency(payload.get("refundAmount"));

        StringBuilder sb = new StringBuilder();

        if (isVendor) {
            // Vendor template
            String subject = messages.get("email.dispute_resolved.vendor.subject", lang, disputeId);
            sb.append(messages.get("email.dispute_resolved.vendor.headline", lang)).append("\n\n");

            String financialImpact;
            if (payload.get("refundAmount") != null && ((Number) payload.get("refundAmount")).doubleValue() > 0) {
                financialImpact = messages.get("email.dispute_resolved.vendor.deduction_info", lang, refundAmountStr);
            } else {
                financialImpact = messages.get("email.dispute_resolved.vendor.no_deduction", lang);
            }

            String ctaUrl = properties.getPublicBaseUrl() + "/vendor/disputes/" + disputeId;
            sb.append(messages.get("email.dispute_resolved.vendor.details", lang, disputeId, resolution, financialImpact)).append("\n\n");
            sb.append(messages.get("email.dispute_resolved.vendor.cta", lang, ctaUrl)).append("\n\n");
            sb.append(messages.get("email.common.signature", lang));

            String body = sb.toString();
            String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                    subject,
                    lang == SupportedLanguage.EN ? "Dispute Resolution" : "Phán quyết tranh chấp",
                    TransactionalEmailHtmlLayout.Theme.OCEAN,
                    body,
                    lang == SupportedLanguage.EN ? "View Dispute Case" : "Xem hồ sơ tranh chấp",
                    ctaUrl,
                    properties.getSupportPhone(),
                    lang
            );
            return new RenderedEmail(recipient.email(), subject, body, htmlBody);
        } else {
            // Customer template
            String recipientName = resolveRecipientName(entity.getRecipientKey());
            String subject = messages.get("email.dispute_resolved.customer.subject", lang, disputeId);

            String reason = payload.get("adminNote") != null && !payload.get("adminNote").toString().isBlank()
                    ? payload.get("adminNote").toString()
                    : (payload.get("reason") != null ? payload.get("reason").toString() : "Giải quyết theo chính sách sàn");

            String refundInfo = "";
            if (payload.get("refundAmount") != null && ((Number) payload.get("refundAmount")).doubleValue() > 0) {
                refundInfo = messages.get("email.dispute_resolved.customer.refund_info", lang, refundAmountStr);
            }

            String orderId = payload.get("orderId") != null ? payload.get("orderId").toString() : "";
            String link = properties.getPublicBaseUrl() + "/profile/orders" + (orderId.isBlank() ? "" : "?orderId=" + orderId);

            sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
            sb.append(messages.get("email.dispute_resolved.customer.headline", lang)).append("\n\n");
            sb.append(messages.get("email.dispute_resolved.customer.details", lang, disputeId, resolution, reason, refundInfo)).append("\n\n");
            sb.append(messages.get("email.dispute_resolved.customer.cta", lang, link)).append("\n\n");
            sb.append(messages.get("email.common.signature", lang));

            String body = sb.toString();
            String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                    subject,
                    lang == SupportedLanguage.EN ? "Dispute Resolved" : "Kết quả giải quyết tranh chấp",
                    TransactionalEmailHtmlLayout.Theme.OCEAN,
                    body,
                    lang == SupportedLanguage.EN ? "View Case & Order Details" : "Xem kết quả & Chi tiết đơn hàng",
                    link,
                    properties.getSupportPhone(),
                    lang
            );
            return new RenderedEmail(recipient.email(), subject, body, htmlBody);
        }
    }

    // =========================================================================
    // 2. PASSWORD_CHANGED
    // =========================================================================
    private RenderedEmail renderPasswordChanged(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String recipientName = resolveRecipientName(entity.getRecipientKey());
        String subject = messages.get("email.password_changed.subject", lang);

        String changeType = payload.get("changeType") != null ? payload.get("changeType").toString() : "DIRECT";
        String methodText = "OTP".equalsIgnoreCase(changeType)
                ? messages.get("email.password_changed.otp", lang)
                : messages.get("email.password_changed.direct", lang);

        String timeStr = formatVietnamTime(payload.get("timestamp"));
        String recoveryUrl = properties.getPublicBaseUrl() + "/auth/forgot-password";

        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(messages.get("email.password_changed.headline", lang)).append("\n\n");
        sb.append(messages.get("email.password_changed.details", lang, timeStr, methodText)).append("\n\n");
        sb.append(messages.get("email.password_changed.security_warning", lang, recoveryUrl)).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Security Notice" : "Bảo mật tài khoản",
                TransactionalEmailHtmlLayout.Theme.WARNING,
                body,
                lang == SupportedLanguage.EN ? "Recover Account Password" : "Khôi phục mật khẩu ngay",
                recoveryUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    // =========================================================================
    // 3. ACCOUNT_LOCKED
    // =========================================================================
    private RenderedEmail renderAccountLocked(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String recipientName = resolveRecipientName(entity.getRecipientKey());
        String subject = messages.get("email.account_locked.subject", lang);

        String timeStr = formatVietnamTime(payload.get("timestamp"));
        String reason = payload.get("reason") != null && !payload.get("reason").toString().isBlank()
                ? payload.get("reason").toString()
                : messages.get("email.account_locked.reason_default", lang);

        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(messages.get("email.account_locked.headline", lang)).append("\n\n");
        sb.append(messages.get("email.account_locked.details", lang, timeStr, reason)).append("\n\n");
        sb.append(messages.get("email.account_locked.support", lang)).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Account Locked" : "Tài khoản tạm khóa",
                TransactionalEmailHtmlLayout.Theme.DANGER,
                body,
                null,
                null,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    // =========================================================================
    // 4. SETTLEMENT_FINALIZED
    // =========================================================================
    private RenderedEmail renderSettlementFinalized(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String settlementId = payload.get("settlementId") != null ? payload.get("settlementId").toString() : "N/A";
        String periodStart = payload.get("periodStart") != null ? payload.get("periodStart").toString() : "";
        String periodEnd = payload.get("periodEnd") != null ? payload.get("periodEnd").toString() : "";

        String grossStr = formatCurrency(payload.get("grossAmount"));
        String commissionStr = formatCurrency(payload.get("commissionAmount"));
        String compensationDeductedStr = formatCurrency(payload.get("compensationDeductedAmount"));
        String netPayableStr = formatCurrency(payload.get("netPayableAmount"));

        String subject = messages.get("email.settlement_finalized.subject", lang, settlementId);
        String ctaUrl = properties.getPublicBaseUrl() + "/vendor/revenue?settlementId=" + settlementId;

        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.settlement_finalized.headline", lang)).append("\n\n");
        sb.append(messages.get("email.settlement_finalized.details", lang,
                settlementId,
                periodStart,
                periodEnd,
                grossStr,
                commissionStr,
                compensationDeductedStr,
                netPayableStr
        )).append("\n\n");
        sb.append(messages.get("email.settlement_finalized.cta", lang, ctaUrl)).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Settlement Finalized" : "Hoàn tất đối soát",
                TransactionalEmailHtmlLayout.Theme.SUCCESS,
                body,
                lang == SupportedLanguage.EN ? "View Settlement Statement" : "Xem bảng kê đối soát doanh thu",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    // =========================================================================
    // 5. SECURITY_ALERT (Generic security notices)
    // =========================================================================
    private RenderedEmail renderSecurityAlert(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String recipientName = resolveRecipientName(entity.getRecipientKey());
        String subject = "[DANASEA BẢO MẬT] Cảnh báo hoạt động tài khoản";
        String message = payload.get("message") != null ? payload.get("message").toString() : "Phát hiện hoạt động bảo mật mới trên tài khoản của bạn.";

        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(message).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Security Alert" : "Cảnh báo bảo mật",
                TransactionalEmailHtmlLayout.Theme.WARNING,
                body,
                null,
                null,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse delivery payload JSON: {}", json);
            return Map.of();
        }
    }

    private String formatVietnamTime(Object timestampObj) {
        if (timestampObj == null) {
            return OffsetDateTime.now(VN_ZONE).format(VN_TIME_FMT);
        }
        try {
            OffsetDateTime odt = OffsetDateTime.parse(timestampObj.toString());
            return odt.atZoneSameInstant(VN_ZONE).format(VN_TIME_FMT);
        } catch (Exception e) {
            return timestampObj.toString();
        }
    }

    private String formatCurrency(Object amountObj) {
        if (amountObj == null) return "0 đ";
        try {
            double val;
            if (amountObj instanceof Number num) {
                val = num.doubleValue();
            } else {
                val = Double.parseDouble(amountObj.toString());
            }
            NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
            return nf.format(val) + " đ";
        } catch (Exception e) {
            return amountObj.toString() + " đ";
        }
    }

    private String resolveRecipientName(String recipientKey) {
        if (recipientKey == null || !recipientKey.startsWith("USER:")) {
            return "Quý khách";
        }
        try {
            UUID userId = UUID.fromString(recipientKey.substring("USER:".length()));
            return userRepository.findById(userId)
                    .map(u -> (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : "Quý khách")
                    .orElse("Quý khách");
        } catch (Exception e) {
            return "Quý khách";
        }
    }
}

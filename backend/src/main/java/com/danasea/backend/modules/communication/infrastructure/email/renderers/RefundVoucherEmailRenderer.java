package com.danasea.backend.modules.communication.infrastructure.email.renderers;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.ResolvedRecipient;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailRenderer;
import com.danasea.backend.modules.communication.domain.exceptions.SupersededEmailException;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryMode;
import com.danasea.backend.modules.communication.domain.models.EmailDeliveryStatus;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailFamily;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.communication.infrastructure.email.repositories.JpaTransactionalEmailDeliveryRepository;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundVoucherEmailRenderer implements TransactionalEmailRenderer {

    private final JpaRefundRepository refundRepository;
    private final JpaDiscountCodeRepository discountCodeRepository;
    private final JpaTransactionalEmailDeliveryRepository deliveryRepository;
    private final JpaUserRepository userRepository;
    private final TransactionalEmailProperties properties;
    private final LocalizedMessageService messages;
    private final ObjectMapper objectMapper;

    public boolean supports(TransactionalEmailType type) {
        return type == TransactionalEmailType.REFUND_REQUESTED
                || type == TransactionalEmailType.REFUND_PROCESSED
                || type == TransactionalEmailType.REFUND_NEEDS_ATTENTION
                || type == TransactionalEmailType.PERSONAL_VOUCHER_ISSUED;
    }

    @Override
    public RenderedEmail render(TransactionalEmailDeliveryJpaEntity entity, ResolvedRecipient recipient) {
        SupportedLanguage lang = "en".equalsIgnoreCase(recipient.locale())
                ? SupportedLanguage.EN
                : SupportedLanguage.VI;
        Map<String, Object> payload = parsePayload(entity.getPayload());

        return switch (entity.getType()) {
            case REFUND_REQUESTED -> renderRefundRequested(entity, recipient, payload, lang);
            case REFUND_PROCESSED -> renderRefundProcessed(entity, recipient, payload, lang);
            case REFUND_NEEDS_ATTENTION -> renderRefundNeedsAttention(entity, recipient, payload, lang);
            case PERSONAL_VOUCHER_ISSUED -> renderPersonalVoucherIssued(entity, recipient, payload, lang);
            default -> throw new IllegalArgumentException("Unsupported email type: " + entity.getType());
        };
    }

    private RenderedEmail renderRefundRequested(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String refundIdStr = payload.get("refundId") != null ? payload.get("refundId").toString() : null;
        UUID refundId = parseUuid(refundIdStr);

        RefundJpaEntity refund = null;
        if (refundId != null) {
            refund = refundRepository.findById(refundId).orElse(null);
            if (refund != null) {
                if (refund.getStatus() == RefundStatus.PROCESSED) {
                    throw new SupersededEmailException("SUPERSEDED_BY_REFUND_PROCESSED",
                            "Refund " + refundId + " has already transitioned to PROCESSED.");
                }
                if (refund.getStatus() == RefundStatus.FAILED) {
                    throw new SupersededEmailException("SUPERSEDED_BY_REFUND_FAILED",
                            "Refund " + refundId + " has already failed.");
                }
            }
        }

        boolean disruptionEnabled = properties.isFamilyEnabled(TransactionalEmailFamily.DISRUPTION)
                && properties.getDeliveryMode() != EmailDeliveryMode.OFF;

        if (disruptionEnabled && entity.getRecipientKey() != null) {
            String subOrderIdStr = payload.get("subOrderId") != null ? payload.get("subOrderId").toString() : null;
            var deliveries = deliveryRepository.findByTypeAndRecipientKey(
                    TransactionalEmailType.TRIP_CANCELLED,
                    entity.getRecipientKey()
            );
            boolean coveredInCancellation = deliveries.stream()
                    .filter(d -> d.getStatus() != EmailDeliveryStatus.SKIPPED)
                    .anyMatch(d -> {
                        String pStr = d.getPayload();
                        if (pStr == null) return false;
                        if (refundIdStr != null && pStr.contains(refundIdStr)) return true;
                        return subOrderIdStr != null && pStr.contains(subOrderIdStr);
                    });

            if (coveredInCancellation) {
                throw new SupersededEmailException("SUPERSEDED_BY_TRIP_CANCELLED",
                        "Refund " + refundIdStr + " is already covered in a cancellation email.");
            }
        }

        boolean casesEnabled = properties.isFamilyEnabled(TransactionalEmailFamily.CASES)
                && properties.getDeliveryMode() != EmailDeliveryMode.OFF;

        if (casesEnabled && entity.getRecipientKey() != null) {
            String subOrderIdStr = payload.get("subOrderId") != null ? payload.get("subOrderId").toString() : null;
            var deliveries = deliveryRepository.findByTypeAndRecipientKey(
                    TransactionalEmailType.DISPUTE_RESOLVED,
                    entity.getRecipientKey()
            );
            boolean coveredInDispute = deliveries.stream()
                    .filter(d -> d.getStatus() != EmailDeliveryStatus.SKIPPED)
                    .anyMatch(d -> {
                        String pStr = d.getPayload();
                        if (pStr == null) return false;
                        if (refundIdStr != null && pStr.contains(refundIdStr)) return true;
                        return subOrderIdStr != null && pStr.contains(subOrderIdStr);
                    });

            if (coveredInDispute) {
                throw new SupersededEmailException("SUPERSEDED_BY_DISPUTE_RESOLUTION",
                        "Refund " + refundIdStr + " is already covered in a dispute resolution email.");
            }
        }

        String recipientName = resolveRecipientName(entity.getRecipientKey());
        String amountStr = formatCurrency(payload.get("amount"));

        String subject = messages.get("email.refund_requested.subject", lang);
        String ctaUrl = properties.getPublicBaseUrl() + "/profile/orders";
        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(messages.get("email.refund_requested.headline", lang)).append("\n\n");
        sb.append(messages.get("email.refund_requested.details", lang, amountStr, refundIdStr != null ? refundIdStr : "N/A")).append("\n\n");
        sb.append(messages.get("email.refund_requested.cta", lang, ctaUrl)).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Refund Requested" : "Đang xử lý hoàn tiền",
                TransactionalEmailHtmlLayout.Theme.WARNING,
                body,
                lang == SupportedLanguage.EN ? "Track Refund Status" : "Theo dõi trạng thái hoàn tiền",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    private RenderedEmail renderRefundProcessed(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String recipientName = resolveRecipientName(entity.getRecipientKey());
        String refundIdStr = payload.get("refundId") != null ? payload.get("refundId").toString() : null;
        String amountStr = formatCurrency(payload.get("amount"));

        String subject = messages.get("email.refund_processed.subject", lang);
        String ctaUrl = properties.getPublicBaseUrl() + "/profile/orders";
        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(messages.get("email.refund_processed.headline", lang)).append("\n\n");
        sb.append(messages.get("email.refund_processed.gateway_disclaimer", lang)).append("\n\n");
        sb.append(messages.get("email.refund_processed.details", lang, amountStr, refundIdStr != null ? refundIdStr : "N/A")).append("\n\n");
        sb.append(messages.get("email.refund_processed.cta", lang, ctaUrl)).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Refund Processed" : "Hoàn tiền thành công",
                TransactionalEmailHtmlLayout.Theme.SUCCESS,
                body,
                lang == SupportedLanguage.EN ? "View Refund Details" : "Xem chi tiết giao dịch hoàn",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    private RenderedEmail renderRefundNeedsAttention(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String refundIdStr = payload.get("refundId") != null ? payload.get("refundId").toString() : null;
        UUID refundId = parseUuid(refundIdStr);

        if (entity.getBusinessKey() != null && entity.getBusinessKey().endsWith(":overdue")) {
            if (refundId != null) {
                var refundOpt = refundRepository.findById(refundId);
                if (refundOpt.isEmpty() || refundOpt.get().getStatus() != RefundStatus.PENDING) {
                    throw new SupersededEmailException("REFUND_NO_LONGER_PENDING",
                            "Refund " + refundId + " is no longer in PENDING status.");
                }
            }
        }

        String reason = payload.get("reason") != null ? payload.get("reason").toString() : "ATTENTION_REQUIRED";
        String lastError = payload.get("lastError") != null ? payload.get("lastError").toString() : "N/A";
        String amountStr = formatCurrency(payload.get("amount"));

        String subject = messages.get("email.refund_needs_attention.subject", lang, reason);
        String ctaUrl = properties.getPublicBaseUrl() + "/admin/orders";
        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.refund_needs_attention.headline", lang, reason)).append("\n\n");
        sb.append(messages.get("email.refund_needs_attention.details", lang,
                refundIdStr != null ? refundIdStr : "N/A",
                amountStr,
                reason,
                lastError
        )).append("\n\n");
        sb.append(messages.get("email.refund_needs_attention.cta", lang, ctaUrl)).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                "OPS · " + reason,
                TransactionalEmailHtmlLayout.Theme.DANGER,
                body,
                lang == SupportedLanguage.EN ? "Open Admin Orders" : "Mở trang Quản trị Đơn hàng",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    private RenderedEmail renderPersonalVoucherIssued(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String recipientName = resolveRecipientName(entity.getRecipientKey());
        String voucherIdStr = payload.get("voucherId") != null ? payload.get("voucherId").toString() : null;
        UUID voucherId = parseUuid(voucherIdStr);

        String code = payload.get("code") != null ? payload.get("code").toString() : "DANASEA-VOUCHER";
        String discountValueStr = "";
        String minOrderStr = "";
        String validToStr = "";

        if (voucherId != null) {
            DiscountCodeJpaEntity voucher = discountCodeRepository.findById(voucherId).orElse(null);
            if (voucher != null) {
                code = voucher.getCode();
                if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
                    discountValueStr = voucher.getDiscountValue() + "%";
                } else {
                    discountValueStr = formatCurrency(voucher.getDiscountValue());
                }
                minOrderStr = voucher.getMinOrderAmount() != null && voucher.getMinOrderAmount().signum() > 0
                        ? formatCurrency(voucher.getMinOrderAmount())
                        : "0 VNĐ";
                if (voucher.getValidTo() != null) {
                    validToStr = voucher.getValidTo().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                }
            }
        }

        if (discountValueStr.isBlank()) {
            discountValueStr = formatCurrency(payload.get("discountValue"));
        }
        if (minOrderStr.isBlank()) {
            minOrderStr = "0 VNĐ";
        }
        if (validToStr.isBlank()) {
            validToStr = "30 ngày kể từ ngày nhận";
        }

        String subject = messages.get("email.personal_voucher.subject", lang, code);
        String ctaUrl = properties.getPublicBaseUrl() + "/profile/vouchers";
        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(messages.get("email.personal_voucher.headline", lang)).append("\n\n");
        sb.append(messages.get("email.personal_voucher.details", lang, code, discountValueStr, minOrderStr, validToStr)).append("\n\n");
        sb.append(messages.get("email.personal_voucher.cta", lang, ctaUrl)).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String voucherHtml = "<p>" + TransactionalEmailHtmlLayout.escapeHtml(messages.get("email.common.greeting", lang, recipientName)) + "</p>"
                + "<p>" + TransactionalEmailHtmlLayout.escapeHtml(messages.get("email.personal_voucher.headline", lang)) + "</p>"
                + TransactionalEmailHtmlLayout.buildVoucherBox(code, discountValueStr, minOrderStr, validToStr, lang);
        String htmlBody = TransactionalEmailHtmlLayout.wrapDocument(
                subject,
                lang == SupportedLanguage.EN ? "Personal Voucher" : "Mã ưu đãi riêng",
                TransactionalEmailHtmlLayout.Theme.OCEAN,
                voucherHtml,
                lang == SupportedLanguage.EN ? "View My Vouchers" : "Mở Kho Voucher của tôi",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    private String resolveRecipientName(String recipientKey) {
        if (recipientKey != null && recipientKey.startsWith("USER:")) {
            try {
                UUID uid = UUID.fromString(recipientKey.substring(5));
                var uOpt = userRepository.findById(uid);
                if (uOpt.isPresent() && uOpt.get().getFullName() != null) {
                    return uOpt.get().getFullName();
                }
            } catch (Exception ignored) {}
        }
        return "Quý khách";
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse transactional email payload JSON: {}", json, e);
            return Map.of();
        }
    }

    private UUID parseUuid(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return UUID.fromString(str.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String formatCurrency(Object amountObj) {
        if (amountObj == null) return "0 VNĐ";
        try {
            BigDecimal bd = new BigDecimal(amountObj.toString());
            NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
            return nf.format(bd) + " VNĐ";
        } catch (Exception e) {
            return amountObj.toString() + " VNĐ";
        }
    }
}

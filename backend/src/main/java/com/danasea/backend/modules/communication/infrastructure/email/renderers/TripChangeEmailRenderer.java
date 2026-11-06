package com.danasea.backend.modules.communication.infrastructure.email.renderers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.communication.application.email.RenderedEmail;
import com.danasea.backend.modules.communication.application.email.ResolvedRecipient;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailRenderer;
import com.danasea.backend.modules.communication.domain.exceptions.SupersededEmailException;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.infrastructure.email.TransactionalEmailProperties;
import com.danasea.backend.modules.communication.infrastructure.email.entities.TransactionalEmailDeliveryJpaEntity;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRescheduleProposalRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TripChangeEmailRenderer implements TransactionalEmailRenderer {

    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaServiceRepository serviceRepository;
    private final JpaServiceSlotRepository serviceSlotRepository;
    private final JpaRefundRepository refundRepository;
    private final JpaSubOrderRescheduleProposalRepository proposalRepository;
    private final JpaUserRepository userRepository;
    private final TransactionalEmailProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    private LocalizedMessageService messages = LocalizedMessageService.standalone();

    public TripChangeEmailRenderer(
            JpaMasterOrderRepository masterOrderRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaServiceRepository serviceRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            JpaRefundRepository refundRepository,
            JpaSubOrderRescheduleProposalRepository proposalRepository,
            JpaUserRepository userRepository,
            TransactionalEmailProperties properties,
            ObjectMapper objectMapper) {
        this.masterOrderRepository = masterOrderRepository;
        this.subOrderRepository = subOrderRepository;
        this.serviceRepository = serviceRepository;
        this.serviceSlotRepository = serviceSlotRepository;
        this.refundRepository = refundRepository;
        this.proposalRepository = proposalRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean supports(TransactionalEmailType type) {
        return type == TransactionalEmailType.TRIP_RED_ALERT
                || type == TransactionalEmailType.TRIP_CANCELLED
                || type == TransactionalEmailType.TRIP_DISRUPTION_DECISION
                || type == TransactionalEmailType.TRIP_RESCHEDULE_PROPOSED
                || type == TransactionalEmailType.TRIP_RESCHEDULED
                || type == TransactionalEmailType.OPS_DISRUPTION_ALERT;
    }

    @Override
    public RenderedEmail render(TransactionalEmailDeliveryJpaEntity entity, ResolvedRecipient recipient) {
        SupportedLanguage lang = "en".equalsIgnoreCase(recipient.locale())
                ? SupportedLanguage.EN
                : SupportedLanguage.VI;

        Map<String, Object> payload = parsePayload(entity.getPayload());

        return switch (entity.getType()) {
            case TRIP_RED_ALERT -> renderRedAlert(entity, recipient, payload, lang);
            case TRIP_CANCELLED -> renderTripCancelled(entity, recipient, payload, lang);
            case TRIP_RESCHEDULE_PROPOSED -> renderRescheduleProposed(entity, recipient, payload, lang);
            case TRIP_RESCHEDULED -> renderRescheduled(entity, recipient, payload, lang);
            case OPS_DISRUPTION_ALERT -> renderOpsDisruptionAlert(entity, recipient, payload, lang);
            case TRIP_DISRUPTION_DECISION -> renderDisruptionDecision(entity, recipient, payload, lang);
            default -> throw new IllegalArgumentException("Unsupported email type for TripChangeEmailRenderer: " + entity.getType());
        };
    }

    private RenderedEmail renderRedAlert(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String slotIdStr = (String) payload.get("slotId");
        UUID slotId = (slotIdStr != null) ? UUID.fromString(slotIdStr) : parseSlotIdFromBusinessKey(entity.getBusinessKey());

        // Check if customer still has active (CONFIRMED) sub-orders on this slot
        UUID customerId = parseCustomerId(entity.getRecipientKey());
        if (slotId != null && customerId != null) {
            List<SubOrderJpaEntity> activeSubs = subOrderRepository.findBySlotId(slotId).stream()
                    .filter(s -> customerId.equals(resolveCustomerId(s)) && s.getStatus() == SubOrderStatus.CONFIRMED)
                    .toList();
            if (activeSubs.isEmpty()) {
                throw new SupersededEmailException(
                        "SUPERSEDED_BY_CANCELLATION",
                        "Customer " + customerId + " has no active sub-orders remaining on slot " + slotId
                );
            }
        }

        String serviceName = (String) payload.getOrDefault("serviceName", "Dịch vụ Danasea");
        String warning = (String) payload.getOrDefault("warning", "");
        String date = (String) payload.getOrDefault("date", "");
        String startTime = (String) payload.getOrDefault("startTime", "");

        String subject = messages.get("email.trip_red_alert.subject", lang, serviceName);
        String recipientName = resolveRecipientName(customerId, lang);

        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(messages.get("email.trip_red_alert.headline", lang, serviceName)).append("\n\n");
        if (!date.isBlank() || !startTime.isBlank()) {
            sb.append(messages.get("email.trip_red_alert.departure", lang, date, startTime)).append("\n");
        }
        if (!warning.isBlank()) {
            sb.append(messages.get("email.trip_red_alert.warning", lang, warning)).append("\n\n");
        }
        sb.append(messages.get("email.trip_red_alert.instructions", lang)).append("\n\n");
        sb.append(messages.get("email.trip_red_alert.cta", lang, properties.getPublicBaseUrl())).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String ctaUrl = properties.getPublicBaseUrl() + "/profile/orders";
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Red Weather Alert" : "Cảnh báo Thời tiết Đỏ",
                TransactionalEmailHtmlLayout.Theme.DANGER,
                body,
                lang == SupportedLanguage.EN ? "Check Trip Status" : "Kiểm tra trạng thái chuyến đi",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    private RenderedEmail renderTripCancelled(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String serviceName = (String) payload.getOrDefault("serviceName", "Dịch vụ Danasea");
        String reason = (String) payload.getOrDefault("reason", "");
        boolean isVendor = Boolean.TRUE.equals(payload.get("isVendor"));

        UUID customerId = parseCustomerId(entity.getRecipientKey());
        String recipientName = resolveRecipientName(customerId, lang);

        if (isVendor) {
            String subject = messages.get("email.vendor_trip_cancelled.subject", lang, serviceName);
            StringBuilder sb = new StringBuilder();
            sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
            sb.append(messages.get("email.vendor_trip_cancelled.headline", lang, serviceName)).append("\n\n");
            if (!reason.isBlank()) {
                sb.append(messages.get("email.trip_cancelled.reason", lang, reason)).append("\n\n");
            }
            String ctaUrl = properties.getPublicBaseUrl() + "/vendor/orders";
            sb.append(messages.get("email.vendor_trip_cancelled.cta", lang, ctaUrl)).append("\n\n");
            sb.append(messages.get("email.common.signature", lang));
            String body = sb.toString();
            String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                    subject,
                    lang == SupportedLanguage.EN ? "Trip Cancelled" : "Hủy chuyến",
                    TransactionalEmailHtmlLayout.Theme.DANGER,
                    body,
                    lang == SupportedLanguage.EN ? "View Affected Orders" : "Xem đơn hàng bị ảnh hưởng",
                    ctaUrl,
                    properties.getSupportPhone(),
                    lang
            );
            return new RenderedEmail(recipient.email(), subject, body, htmlBody);
        }

        // Customer cancellation email
        String subject = messages.get("email.trip_cancelled.subject", lang, serviceName);

        // Check refund status
        boolean hasProcessedRefund = false;
        boolean hasPendingRefund = false;
        Object subOrderIdsObj = payload.get("subOrderIds");
        if (subOrderIdsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    try {
                        UUID subId = UUID.fromString(item.toString());
                        var refunds = refundRepository.findBySubOrderId(subId);
                        for (var r : refunds) {
                            if (r.getStatus() == RefundStatus.PROCESSED) {
                                hasProcessedRefund = true;
                            } else if (r.getStatus() == RefundStatus.PENDING) {
                                hasPendingRefund = true;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        String payloadRefundId = (String) payload.get("refundId");
        if (payloadRefundId != null) {
            try {
                UUID rId = UUID.fromString(payloadRefundId);
                var rOpt = refundRepository.findById(rId);
                if (rOpt.isPresent()) {
                    if (rOpt.get().getStatus() == RefundStatus.PROCESSED) {
                        hasProcessedRefund = true;
                    } else if (rOpt.get().getStatus() == RefundStatus.PENDING) {
                        hasPendingRefund = true;
                    }
                }
            } catch (Exception ignored) {}
        }

        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(messages.get("email.trip_cancelled.headline", lang, serviceName)).append("\n\n");

        // Hướng dẫn quan trọng không đến điểm hẹn
        sb.append(messages.get("email.trip_cancelled.do_not_go", lang)).append("\n\n");

        if (!reason.isBlank()) {
            sb.append(messages.get("email.trip_cancelled.reason", lang, reason)).append("\n\n");
        }

        if (hasProcessedRefund) {
            sb.append(messages.get("email.trip_cancelled.refund_processed", lang)).append("\n\n");
        } else if (hasPendingRefund) {
            sb.append(messages.get("email.trip_cancelled.refund_processing", lang)).append("\n\n");
        } else {
            sb.append(messages.get("email.trip_cancelled.non_refundable", lang)).append("\n\n");
        }

        sb.append(messages.get("email.trip_cancelled.cta", lang, properties.getPublicBaseUrl() + "/profile/orders")).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String ctaUrl = properties.getPublicBaseUrl() + "/profile/orders";
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Trip Cancelled" : "Đã hủy chuyến",
                TransactionalEmailHtmlLayout.Theme.DANGER,
                body,
                lang == SupportedLanguage.EN ? "View Order & Refund Status" : "Xem chi tiết đơn & hoàn tiền",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    private RenderedEmail renderRescheduleProposed(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String proposalIdStr = (String) payload.get("proposalId");
        if (proposalIdStr != null) {
            try {
                UUID proposalId = UUID.fromString(proposalIdStr);
                var proposalOpt = proposalRepository.findById(proposalId);
                if (proposalOpt.isPresent()) {
                    var prop = proposalOpt.get();
                    if (!"PENDING".equalsIgnoreCase(prop.getStatus())) {
                        throw new SupersededEmailException("PROPOSAL_NO_LONGER_PENDING", "Proposal is in status " + prop.getStatus());
                    }
                    if (prop.getExpiresAt() != null && prop.getExpiresAt().isBefore(OffsetDateTime.now())) {
                        throw new SupersededEmailException("PROPOSAL_EXPIRED", "Proposal expired at " + prop.getExpiresAt());
                    }
                }
            } catch (SupersededEmailException e) {
                throw e;
            } catch (Exception ignored) {}
        }

        String serviceName = (String) payload.getOrDefault("serviceName", "Dịch vụ Danasea");
        String proposedDate = (String) payload.getOrDefault("proposedDate", "");
        String proposedTime = (String) payload.getOrDefault("proposedTime", "");
        String reason = (String) payload.getOrDefault("reason", "");

        UUID customerId = parseCustomerId(entity.getRecipientKey());
        String recipientName = resolveRecipientName(customerId, lang);

        String subject = messages.get("email.trip_reschedule_proposed.subject", lang, serviceName);
        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(messages.get("email.trip_reschedule_proposed.headline", lang, serviceName)).append("\n\n");
        if (!proposedDate.isBlank() || !proposedTime.isBlank()) {
            sb.append(messages.get("email.trip_reschedule_proposed.new_time", lang, proposedDate, proposedTime)).append("\n");
        }
        if (!reason.isBlank()) {
            sb.append(messages.get("email.trip_reschedule_proposed.reason", lang, reason)).append("\n\n");
        }
        sb.append(messages.get("email.trip_reschedule_proposed.cta", lang, properties.getPublicBaseUrl() + "/profile/orders")).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String ctaUrl = properties.getPublicBaseUrl() + "/profile/orders";
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Reschedule Proposal" : "Đề xuất đổi lịch",
                TransactionalEmailHtmlLayout.Theme.WARNING,
                body,
                lang == SupportedLanguage.EN ? "Review & Confirm New Schedule" : "Xem & Xác nhận lịch mới",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    private RenderedEmail renderRescheduled(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String serviceName = (String) payload.getOrDefault("serviceName", "Dịch vụ Danasea");
        String newDate = (String) payload.getOrDefault("newDate", "");
        String newTime = (String) payload.getOrDefault("newTime", "");

        UUID customerId = parseCustomerId(entity.getRecipientKey());
        String recipientName = resolveRecipientName(customerId, lang);

        String subject = messages.get("email.trip_rescheduled.subject", lang, serviceName);
        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(messages.get("email.trip_rescheduled.headline", lang, serviceName)).append("\n\n");
        if (!newDate.isBlank() || !newTime.isBlank()) {
            sb.append(messages.get("email.trip_rescheduled.new_time", lang, newDate, newTime)).append("\n\n");
        }
        sb.append(messages.get("email.trip_rescheduled.cta", lang, properties.getPublicBaseUrl() + "/profile/orders")).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String ctaUrl = properties.getPublicBaseUrl() + "/profile/orders";
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Schedule Updated" : "Đã đổi lịch",
                TransactionalEmailHtmlLayout.Theme.SUCCESS,
                body,
                lang == SupportedLanguage.EN ? "View Updated Ticket" : "Xem vé với lịch khởi hành mới",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    private RenderedEmail renderOpsDisruptionAlert(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String serviceName = (String) payload.getOrDefault("serviceName", "Dịch vụ Danasea");
        String stage = (String) payload.getOrDefault("stage", "SỰ CỐ");
        String slotId = (String) payload.getOrDefault("slotId", "");

        String subject = messages.get("email.ops_disruption_alert.subject", lang, serviceName);
        String ctaUrl = properties.getPublicBaseUrl() + "/admin/weather-alerts";
        StringBuilder sb = new StringBuilder();
        sb.append("[DANASEA OPERATIONS ALERT]\n\n");
        sb.append("Phát hiện sự cố ca khởi hành cần xử lý / theo dõi:\n");
        sb.append("- Dịch vụ: ").append(serviceName).append("\n");
        sb.append("- Mã ca (Slot ID): ").append(slotId).append("\n");
        sb.append("- Giai đoạn / Mức độ: ").append(stage).append("\n\n");
        sb.append("Vui lòng đăng nhập hệ thống quản trị Danasea để kiểm tra và xử lý.\n");
        sb.append(ctaUrl).append("\n");

        String body = sb.toString();
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                "OPS ALERT · " + stage,
                TransactionalEmailHtmlLayout.Theme.DANGER,
                body,
                "Mở trang Quản trị Cảnh báo Thời tiết",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    private RenderedEmail renderDisruptionDecision(
            TransactionalEmailDeliveryJpaEntity entity,
            ResolvedRecipient recipient,
            Map<String, Object> payload,
            SupportedLanguage lang
    ) {
        String serviceName = (String) payload.getOrDefault("serviceName", "Dịch vụ Danasea");
        String subject = messages.get("email.trip_disruption_decision.subject", lang, serviceName);
        UUID customerId = parseCustomerId(entity.getRecipientKey());
        String recipientName = resolveRecipientName(customerId, lang);

        StringBuilder sb = new StringBuilder();
        sb.append(messages.get("email.common.greeting", lang, recipientName)).append("\n\n");
        sb.append(messages.get("email.trip_disruption_decision.headline", lang, serviceName)).append("\n\n");
        sb.append(messages.get("email.trip_disruption_decision.cta", lang, properties.getPublicBaseUrl() + "/profile/orders")).append("\n\n");
        sb.append(messages.get("email.common.signature", lang));

        String body = sb.toString();
        String ctaUrl = properties.getPublicBaseUrl() + "/profile/orders";
        String htmlBody = TransactionalEmailHtmlLayout.fromPlainText(
                subject,
                lang == SupportedLanguage.EN ? "Trip Update" : "Cập nhật chuyến đi",
                TransactionalEmailHtmlLayout.Theme.WARNING,
                body,
                lang == SupportedLanguage.EN ? "View Trip Options" : "Xem phương án xử lý chuyến đi",
                ctaUrl,
                properties.getSupportPhone(),
                lang
        );
        return new RenderedEmail(recipient.email(), subject, body, htmlBody);
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON payload: {}", json, e);
            return Map.of();
        }
    }

    private UUID parseCustomerId(String recipientKey) {
        if (recipientKey == null || !recipientKey.startsWith("USER:")) return null;
        try {
            return UUID.fromString(recipientKey.substring("USER:".length()).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private UUID parseSlotIdFromBusinessKey(String businessKey) {
        if (businessKey == null) return null;
        String[] parts = businessKey.split(":");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("slot".equalsIgnoreCase(parts[i])) {
                try {
                    return UUID.fromString(parts[i + 1]);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private UUID resolveCustomerId(SubOrderJpaEntity subOrder) {
        if (subOrder.getMasterOrderId() != null) {
            return masterOrderRepository.findById(subOrder.getMasterOrderId())
                    .map(MasterOrderJpaEntity::getCustomerId)
                    .orElse(null);
        }
        return null;
    }

    private String resolveRecipientName(UUID userId, SupportedLanguage lang) {
        if (userId == null) {
            return lang == SupportedLanguage.EN ? "Customer" : "Quý khách";
        }
        return userRepository.findById(userId)
                .map(u -> u.getFullName())
                .filter(name -> name != null && !name.isBlank())
                .orElse(lang == SupportedLanguage.EN ? "Customer" : "Quý khách");
    }
}

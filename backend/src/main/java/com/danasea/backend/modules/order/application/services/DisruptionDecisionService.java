package com.danasea.backend.modules.order.application.services;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderDisruptionJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRescheduleProposalRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.weather.infrastructure.persistence.repositories.JpaSafetyRuleEvaluationRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageRef;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisruptionDecisionService {
    public static final String AWAITING = "AWAITING_DECISION";
    private final RescheduleSupport support;
    private final JpaSubOrderDisruptionRepository disruptions;
    private final JpaSubOrderRepository subOrders;
    private final JpaRefundRepository refunds;
    private final JpaDiscountCodeRepository discounts;
    private final JpaServiceSlotRepository slots;
    private final JpaSubOrderRescheduleProposalRepository proposals;
    private final JpaSafetyRuleEvaluationRepository evaluations;
    private final AuditLogInternalApi audit;
    private final SendNotificationUseCase notifications;
    private final VendorFaultCompensationService compensation;

    @Value("${app.refund.replacement-voucher-valid-days:30}")
    private int voucherValidDays = 30;

    @Transactional
    public SubOrderDisruptionJpaEntity record(
            UUID subId, RefundReason reason, String details, String sourceKey, UUID actor) {
        if (reason != RefundReason.WEATHER
                && reason != RefundReason.VENDOR_FAULT
                && reason != RefundReason.SYSTEM_FAULT) {
            throw new IllegalArgumentException("A verified disruption reason is required.");
        }
        var locked = support.lock(subId);
        var existing = disruptions.findBySubOrderIdAndSourceKey(subId, sourceKey);
        if (existing.isPresent()) {
            if (existing.get().getReason() != reason
                    || !existing.get().getDetails().equals(details))
                throw RescheduleSupport.conflict(
                        "Idempotency-Key was used for a different disruption.");
            return existing.get();
        }
        RescheduleSupport.eligible(locked.master(), locked.sub());
        var active = disruptions.findBySubOrderIdAndStatus(subId, AWAITING);
        if (active.isPresent()) {
            if (active.get().getReason() != reason && !sourceKey.startsWith("proposal-")) {
                throw RescheduleSupport.conflict(
                        "A different verified disruption is already active.");
            }
            if (!active.get().getDecisionDeadline().isAfter(OffsetDateTime.now()))
                requestRefund(active.get(), locked, actor);
            return active.get();
        }
        assertNoRefund(subId);
        var slot = slots.findById(locked.sub().getSlotId()).orElseThrow();
        var departure = slot.getDate().atTime(slot.getStartTime()).atOffset(ZoneOffset.ofHours(7));
        var disruption = new SubOrderDisruptionJpaEntity();
        disruption.setSubOrderId(subId);
        disruption.setOriginalSlotId(slot.getId());
        disruption.setOriginalDepartureAt(departure);
        disruption.setDecisionDeadline(departure.minusHours(12));
        disruption.setReason(reason);
        disruption.setDetails(details);
        disruption.setSourceKey(sourceKey);
        disruption.setVerifiedBy(actor);
        disruption.setStatus(AWAITING);
        disruption = disruptions.saveAndFlush(disruption);
        if (reason == RefundReason.VENDOR_FAULT) compensation.issue(disruption, locked);
        audit.recordTransactionalAuditLog(
                actor,
                "VERIFY_SUB_ORDER_DISRUPTION",
                "SUB_ORDER",
                subId,
                "{\"disruptionId\":\"" + disruption.getId() + "\",\"reason\":\"" + reason + "\"}");
        if (!disruption.getDecisionDeadline().isAfter(OffsetDateTime.now())) {
            requestRefund(disruption, locked, actor);
        } else {
            notifyCustomer(
                    disruption,
                    locked.master().getCustomerId(),
                    "decision",
                    new Object[] {subId, disruption.getDecisionDeadline()},
                    "decision");
            disruption.setNotificationSentAt(OffsetDateTime.now());
        }
        return disruptions.save(disruption);
    }

    @Transactional
    public SubOrderDisruptionJpaEntity chooseRefund(UUID subId, UUID customerId) {
        var locked = support.lock(subId);
        RescheduleSupport.owner(locked.master(), customerId);
        var previous = disruptions.findBySubOrderIdAndStatus(subId, "REFUND_REQUESTED");
        if (previous.isPresent()) return previous.get();
        var disruption = disruptions.findBySubOrderIdAndStatus(subId, AWAITING).orElse(null);
        if (disruption == null) {
            UUID slotId = locked.sub().getSlotId();
            var cancellation = slots.findVendorCancellation(slotId);
            if (cancellation.isPresent()) {
                var details = cancellation.get();
                disruption = record(subId, RefundReason.VENDOR_FAULT, details.getReason(),
                        "vendor-departure:" + slotId, details.getRequestedBy());
            } else {
                var alert =
                        evaluations
                                .findTopBySlotIdOrderByEvaluatedAtDesc(locked.sub().getSlotId())
                                .filter(
                                        a ->
                                                RescheduleSupport.weatherAlert(a)
                                                        && (Boolean.FALSE.equals(a.getIsSafe())
                                                                || "RED".equals(a.getAlertLevel())))
                                .orElseThrow(
                                        () ->
                                                RescheduleSupport.conflict(
                                                        "No verified disruption permits a full"
                                                                + " refund."));
                disruption =
                        record(
                                subId,
                                RefundReason.WEATHER,
                                "Verified unsafe weather",
                                "weather-" + alert.getId(),
                                null);
            }
        }
        if (AWAITING.equals(disruption.getStatus())) requestRefund(disruption, locked, customerId);
        return disruption;
    }

    @Transactional
    public boolean forceWeatherRefund(UUID subId, UUID expectedSlotId, UUID evaluationId) {
        var locked = support.lock(subId);
        if (!Objects.equals(locked.sub().getSlotId(), expectedSlotId)
                || locked.sub().getStatus() != SubOrderStatus.CONFIRMED
                || locked.sub().getCheckedInAt() != null
                || locked.master().getPaymentStatus() != PaymentOrderStatus.PAID
                || !refunds.findBySubOrderId(subId).isEmpty()) return false;
        var decision = disruptions.findBySubOrderIdAndStatus(subId, AWAITING).orElse(null);
        if (decision == null) {
            decision =
                    record(
                            subId,
                            RefundReason.WEATHER,
                            "Verified unsafe weather",
                            "weather-" + evaluationId,
                            null);
        }
        if (AWAITING.equals(decision.getStatus())) requestRefund(decision, locked, null);
        return true;
    }

    @Transactional
    public void recordWeather(UUID subId, UUID slotId, UUID evaluationId) {
        var locked = support.lock(subId);
        var sub = locked.sub();
        if (!Objects.equals(sub.getSlotId(), slotId)
                || sub.getStatus() != SubOrderStatus.CONFIRMED
                || sub.getCheckedInAt() != null
                || locked.master().getPaymentStatus() != PaymentOrderStatus.PAID) return;
        var alert = evaluations.findTopBySlotIdOrderByEvaluatedAtDesc(slotId).orElse(null);
        if (alert == null
                || !Objects.equals(alert.getId(), evaluationId)
                || !RescheduleSupport.weatherAlert(alert)
                || (!Boolean.FALSE.equals(alert.getIsSafe())
                        && !"RED".equals(alert.getAlertLevel()))) return;
        if (disruptions.findBySubOrderIdAndStatus(subId, AWAITING).isPresent()) return;
        record(
                subId,
                RefundReason.WEATHER,
                "Verified unsafe weather",
                "weather-" + evaluationId,
                null);
    }

    @Transactional
    public void expire(UUID id, OffsetDateTime now) {
        var found = disruptions.findById(id).orElseThrow();
        var locked = support.lock(found.getSubOrderId());
        // Re-read after the order lock: another decision may have committed while we waited.
        var disruption =
                disruptions.findBySubOrderIdAndStatus(found.getSubOrderId(), AWAITING).orElse(null);
        if (disruption == null
                || !id.equals(disruption.getId())
                || disruption.getDecisionDeadline().isAfter(now)) return;
        if (!Objects.equals(locked.sub().getSlotId(), disruption.getOriginalSlotId())
                || locked.sub().getStatus() != SubOrderStatus.CONFIRMED
                || locked.sub().getCheckedInAt() != null) {
            disruption.setStatus("CLOSED");
            disruptions.save(disruption);
            return;
        }
        if (disruption.getReason() == RefundReason.WEATHER
                && disruption.getSourceKey().startsWith("weather-")) {
            var latest =
                    evaluations
                            .findTopBySlotIdOrderByEvaluatedAtDesc(disruption.getOriginalSlotId())
                            .orElse(null);
            if (latest != null
                    && "RESOLVED_SAFE".equals(latest.getStatus())
                    && Boolean.TRUE.equals(latest.getIsSafe())) {
                disruption.setStatus("CLOSED");
                disruptions.save(disruption);
                return;
            }
        }
        RescheduleSupport.eligible(locked.master(), locked.sub());
        requestRefund(disruption, locked, null);
    }

    @Transactional
    public void completeReschedule(UUID subId) {
        disruptions
                .findBySubOrderIdAndStatus(subId, AWAITING)
                .ifPresent(
                        d -> {
                            d.setStatus("RESCHEDULED");
                            disruptions.save(d);
                        });
    }

    @Transactional(readOnly = true)
    public Optional<RefundReason> verifiedReason(UUID subId, UUID slotId) {
        var active = disruptions.findBySubOrderIdAndStatus(subId, AWAITING);
        if (active.isPresent() && Objects.equals(active.get().getOriginalSlotId(), slotId))
            return Optional.of(active.get().getReason());
        if (slots.findVendorCancellation(slotId).isPresent()) {
            return Optional.of(RefundReason.VENDOR_FAULT);
        }
        return evaluations
                .findTopBySlotIdOrderByEvaluatedAtDesc(slotId)
                .filter(
                        a ->
                                RescheduleSupport.weatherAlert(a)
                                        && (Boolean.FALSE.equals(a.getIsSafe())
                                                || "RED".equals(a.getAlertLevel())))
                .map(a -> RefundReason.WEATHER);
    }

    public void assertNoRefund(UUID subId) {
        if (!refunds.findBySubOrderId(subId).isEmpty())
            throw RescheduleSupport.conflict("A refund already exists for this sub-order.");
    }

    private void requestRefund(
            SubOrderDisruptionJpaEntity disruption,
            RescheduleSupport.LockedOrder locked,
            UUID actor) {
        if (!AWAITING.equals(disruption.getStatus())) return;
        RescheduleSupport.eligible(locked.master(), locked.sub());
        if (!Objects.equals(locked.sub().getSlotId(), disruption.getOriginalSlotId()))
            throw RescheduleSupport.conflict(
                    "The disruption no longer applies to the current departure.");
        assertNoRefund(disruption.getSubOrderId());
        var sub = locked.sub();
        slots.findByIdForUpdate(sub.getSlotId()).orElseThrow();
        RefundJpaEntity refund = null;
        if (sub.getFinalAmount().signum() > 0) {
            refund = new RefundJpaEntity();
            refund.setSubOrderId(sub.getId());
            refund.setAmount(sub.getFinalAmount());
            refund.setRefundPercentage(BigDecimal.valueOf(100.0));
            refund.setReason(disruption.getReason());
            refund.setStatus(RefundStatus.PENDING);
            refund.setRequestedBy(actor);
            refund.setIdempotencyKey("disruption-" + disruption.getId());
            refund = refunds.saveAndFlush(refund);
        }
        sub.setStatus(SubOrderStatus.CANCELLED);
        sub.setCancellationReason(disruption.getReason());
        subOrders.saveAndFlush(sub);
        if (sub.getBookingItemId() != null)
            slots.releaseBookingItemCapacity(sub.getBookingItemId());
        disruption.setStatus("REFUND_REQUESTED");
        disruption.setRefundId(refund == null ? null : refund.getId());
        disruptions.saveAndFlush(disruption);
        for (var proposal : proposals.findBySubOrderIdAndStatus(sub.getId(), "PENDING")) {
            proposal.setStatus("EXPIRED");
            proposals.save(proposal);
        }
        if (disruption.getReason() == RefundReason.SYSTEM_FAULT)
            issueReplacement(sub.getId(), locked.master().getCustomerId(), sub.getDiscountAmount());
        audit.recordTransactionalAuditLog(
                actor,
                "DISRUPTION_REFUND_REQUESTED",
                "SUB_ORDER",
                sub.getId(),
                "{\"refundId\":\""
                        + disruption.getRefundId()
                        + "\",\"reason\":\""
                        + disruption.getReason()
                        + "\"}");
        notifyCustomer(
                disruption,
                locked.master().getCustomerId(),
                refund == null ? "no_payment" : "refund",
                new Object[] {sub.getId()},
                "refund");
    }

    @Transactional
    public void recordVendorDeparture(UUID subId, UUID expectedSlotId, String reason, UUID actor) {
        var locked = support.lock(subId);
        if (!Objects.equals(locked.sub().getSlotId(), expectedSlotId)
                || locked.sub().getStatus() != SubOrderStatus.CONFIRMED
                || locked.sub().getCheckedInAt() != null
                || locked.master().getPaymentStatus() != PaymentOrderStatus.PAID) {
            return;
        }
        record(subId, RefundReason.VENDOR_FAULT, reason, "vendor-departure:" + expectedSlotId, actor);
    }

    public void notifyUnfulfilledPayment(UUID customerId, UUID subOrderId, UUID paymentId, boolean refundPending) {
        String action = refundPending ? "refund" : "no_payment";
        notifications.executeOnce(
                new NotificationCommand(customerId, "DISRUPTION_REFUND", NotificationChannel.IN_APP,
                        LocalizedMessageRef.of("notification.disruption." + action + ".title"),
                        LocalizedMessageRef.of("notification.disruption." + action + ".body", subOrderId),
                        "SUB_ORDER", subOrderId, null),
                "departure-payment:" + paymentId + ":" + subOrderId);
    }

    private void issueReplacement(UUID subId, UUID customerId, BigDecimal amount) {
        if (amount == null
                || amount.signum() <= 0
                || discounts.findByReplacementSubOrderId(subId).isPresent()) return;
        if (voucherValidDays <= 0)
            throw new IllegalStateException("Replacement voucher validity must be positive.");
        var now = OffsetDateTime.now();
        var code = new DiscountCodeJpaEntity();
        code.setCode(
                "SYS-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        code.setScope(DiscountScope.PLATFORM);
        code.setSponsorType(DiscountSponsorType.PLATFORM);
        code.setRecipientCustomerId(customerId);
        code.setReplacementSubOrderId(subId);
        code.setDiscountType(DiscountType.FIXED);
        code.setDiscountValue(amount);
        code.setMinOrderAmount(BigDecimal.ZERO);
        code.setMaxUses(1);
        code.setMaxUsesPerUser(1);
        code.setUsedCount(0);
        code.setValidFrom(now);
        code.setValidTo(now.plusDays(voucherValidDays));
        discounts.saveAndFlush(code);
    }

    private void notifyCustomer(
            SubOrderDisruptionJpaEntity disruption,
            UUID customerId,
            String message,
            Object[] args,
            String action) {
        notifications.executeOnce(
                new NotificationCommand(
                        customerId,
                        "DISRUPTION_" + action.toUpperCase(Locale.ROOT),
                        NotificationChannel.IN_APP,
                        LocalizedMessageRef.of("notification.disruption." + message + ".title"),
                        LocalizedMessageRef.of(
                                "notification.disruption." + message + ".body", args),
                        "SUB_ORDER",
                        disruption.getSubOrderId(),
                        null),
                "disruption:" + disruption.getId() + ":" + action);
    }
}

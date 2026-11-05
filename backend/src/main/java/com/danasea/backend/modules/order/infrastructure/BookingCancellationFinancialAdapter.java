package com.danasea.backend.modules.order.infrastructure;

import com.danasea.backend.modules.booking.domain.models.CancellationFinancialResult;
import com.danasea.backend.modules.booking.domain.ports.BookingCancellationFinancialPort;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.order.application.DiscountReservationService;
import com.danasea.backend.modules.order.application.RefundProcessingService;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundEvaluationResult;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class BookingCancellationFinancialAdapter implements BookingCancellationFinancialPort {

    private DisruptionDecisionService disruptionDecisions;

    @Autowired
    public void setDisruptionDecisions(DisruptionDecisionService disruptionDecisions) {
        this.disruptionDecisions = disruptionDecisions;
    }

    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaRefundRepository refundRepository;
    private final JpaServiceSlotRepository serviceSlotRepository;
    private final RefundPolicyEngine refundPolicyEngine;
    private final RefundProcessingService refundProcessingService;
    private final DiscountReservationService reservations;

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    @Autowired(required = false)
    private VendorInternalApi vendorInternalApi;

    @Autowired
    public BookingCancellationFinancialAdapter(
            JpaMasterOrderRepository masterOrderRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaRefundRepository refundRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            RefundPolicyEngine refundPolicyEngine,
            @Autowired(required = false) RefundProcessingService refundProcessingService,
            DiscountReservationService reservations) {
        this.masterOrderRepository = masterOrderRepository;
        this.subOrderRepository = subOrderRepository;
        this.refundRepository = refundRepository;
        this.serviceSlotRepository = serviceSlotRepository;
        this.refundPolicyEngine = refundPolicyEngine;
        this.refundProcessingService = refundProcessingService;
        this.reservations = reservations;
    }

    public BookingCancellationFinancialAdapter(
            JpaMasterOrderRepository orders,
            JpaSubOrderRepository subOrders,
            JpaRefundRepository refunds,
            JpaServiceSlotRepository slots,
            RefundPolicyEngine policy,
            RefundProcessingService processor) {
        this(orders, subOrders, refunds, slots, policy, processor, null);
    }

    public BookingCancellationFinancialAdapter(
            JpaMasterOrderRepository masterOrderRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaRefundRepository refundRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            RefundPolicyEngine refundPolicyEngine) {
        this(
                masterOrderRepository,
                subOrderRepository,
                refundRepository,
                serviceSlotRepository,
                refundPolicyEngine,
                null);
    }

    @Override
    @Transactional
    public CancellationFinancialResult requestRefund(
            UUID bookingId, UUID requestedBy, String idempotencyKey, OffsetDateTime requestedAt) {
        var order =
                masterOrderRepository
                        .findByBookingIdForUpdate(bookingId)
                        .orElseThrow(
                                () ->
                                        new InvalidOrderStateException(
                                                "Paid booking has no associated order."));
        if (!MasterOrderStatus.PAID.equals(order.getStatus())) {
            throw new InvalidOrderStateException(
                    "Only a paid order can enter the cancellation refund flow.");
        }

        List<SubOrderJpaEntity> subOrders = subOrderRepository.findByMasterOrderId(order.getId());
        BigDecimal originalTotal = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        for (SubOrderJpaEntity subOrder : subOrders) {
            originalTotal = originalTotal.add(subOrder.getFinalAmount());
            var existing =
                    refundRepository.findBySubOrderIdAndIdempotencyKey(
                            subOrder.getId(), idempotencyKey);
            if (existing.isPresent()) {
                refundTotal = refundTotal.add(existing.get().getAmount());
                subOrder.setStatus(SubOrderStatus.CANCELLED);
                subOrder.setCancellationReason(RefundReason.CUSTOMER_CANCEL);
                continue;
            }

            if (disruptionDecisions != null
                    && disruptionDecisions
                            .verifiedReason(subOrder.getId(), subOrder.getSlotId())
                            .isPresent()) {
                var decision =
                        disruptionDecisions.chooseRefund(subOrder.getId(), order.getCustomerId());
                if (decision.getRefundId() == null) continue;
                var stored = refundRepository.findById(decision.getRefundId()).orElseThrow();
                refundTotal = refundTotal.add(stored.getAmount());
                continue;
            }
            if (!refundRepository.findBySubOrderId(subOrder.getId()).isEmpty())
                throw new InvalidOrderStateException("A refund already exists for this sub-order.");

            LocalDateTime departure =
                    serviceSlotRepository
                            .findById(subOrder.getSlotId())
                            .filter(slot -> slot.getDate() != null && slot.getStartTime() != null)
                            .map(slot -> LocalDateTime.of(slot.getDate(), slot.getStartTime()))
                            .orElse(null);
            RefundEvaluationResult evaluation =
                    refundPolicyEngine.evaluate(
                            RefundReason.CUSTOMER_CANCEL,
                            departure,
                            requestedAt
                                    .atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
                                    .toLocalDateTime(),
                            subOrder.getFinalAmount(),
                            null,
                            subOrder.getCancellationPolicyVersion());
            refundTotal = refundTotal.add(evaluation.refundAmount());

            if (evaluation.refundAmount().compareTo(BigDecimal.ZERO) > 0) {
                RefundJpaEntity refund = new RefundJpaEntity();
                refund.setSubOrderId(subOrder.getId());
                refund.setAmount(evaluation.refundAmount());
                refund.setRefundPercentage(evaluation.refundPercentage());
                refund.setReason(RefundReason.CUSTOMER_CANCEL);
                refund.setStatus(RefundStatus.PENDING);
                refund.setRequestedBy(requestedBy);
                refund.setIdempotencyKey(idempotencyKey);
                refund = refundRepository.save(refund);
                if (transactionalEmailPort != null && refund != null && refund.getId() != null) {
                    transactionalEmailPort.enqueue(new EmailRequest(
                            TransactionalEmailType.REFUND_REQUESTED,
                            "refund:" + refund.getId() + ":requested",
                            "USER:" + order.getCustomerId(),
                            requestedAt,
                            requestedAt,
                            null,
                            1,
                            Map.of(
                                    "refundId", refund.getId().toString(),
                                    "subOrderId", subOrder.getId().toString(),
                                    "amount", refund.getAmount() != null ? refund.getAmount().doubleValue() : 0.0
                            )
                    ));
                }
            }
            subOrder.setStatus(SubOrderStatus.CANCELLED);
            subOrder.setCancellationReason(RefundReason.CUSTOMER_CANCEL);
        }
        subOrderRepository.saveAll(
                subOrders.stream().filter(s -> s.getStatus() == SubOrderStatus.CANCELLED).toList());
        order.setStatus(MasterOrderStatus.CANCELLED);
        order.setPaymentStatus(
                refundTotal.signum() == 0 ? PaymentOrderStatus.NO_REFUND : PaymentOrderStatus.PAID);
        masterOrderRepository.save(order);

        if (transactionalEmailPort != null) {
            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.TRIP_CANCELLED,
                    "booking:" + bookingId + ":cancelled",
                    "USER:" + order.getCustomerId(),
                    requestedAt,
                    requestedAt,
                    requestedAt.plusDays(7),
                    1,
                    Map.of(
                            "bookingId", bookingId.toString(),
                            "serviceName", "Dịch vụ Danasea",
                            "reason", "Khách hàng yêu cầu hủy đơn",
                            "subOrderIds", subOrders.stream().map(s -> s.getId().toString()).toList()
                    )
            ));
            if (vendorInternalApi != null) {
                Set<UUID> distinctVendorIds = subOrders.stream()
                        .map(SubOrderJpaEntity::getVendorId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                for (UUID vId : distinctVendorIds) {
                    UUID vendorOwnerUserId = vendorInternalApi.findById(vId)
                            .map(Vendor::getUserId)
                            .orElse(null);
                    if (vendorOwnerUserId != null) {
                        transactionalEmailPort.enqueue(new EmailRequest(
                                TransactionalEmailType.TRIP_CANCELLED,
                                "booking:" + bookingId + ":vendor:" + vId + ":cancelled",
                                "USER:" + vendorOwnerUserId,
                                requestedAt,
                                requestedAt,
                                requestedAt.plusDays(7),
                                1,
                                Map.of(
                                        "bookingId", bookingId.toString(),
                                        "serviceName", "Dịch vụ Danasea",
                                        "reason", "Khách hàng hủy đơn",
                                        "isVendor", true
                                )
                        ));
                    }
                }
            }
        }

        int percentage =
                originalTotal.compareTo(BigDecimal.ZERO) == 0
                        ? 0
                        : refundTotal
                                .multiply(BigDecimal.valueOf(100))
                                .divide(originalTotal, 0, RoundingMode.HALF_UP)
                                .intValue();
        return new CancellationFinancialResult(
                refundTotal.compareTo(BigDecimal.ZERO) > 0, percentage, refundTotal);
    }

    @Override
    @Transactional
    public void cancelUnpaidOrder(UUID bookingId) {
        masterOrderRepository
                .findByBookingIdForUpdate(bookingId)
                .ifPresent(
                        order -> {
                            if (order.getStatus() == MasterOrderStatus.CANCELLED) {
                                return;
                            }
                            if (!MasterOrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
                                throw new InvalidOrderStateException(
                                        "Only a pending-payment order can be cancelled without a"
                                            + " refund.");
                            }
                            List<SubOrderJpaEntity> subOrders =
                                    subOrderRepository.findByMasterOrderId(order.getId());
                            subOrders.forEach(
                                    subOrder -> {
                                        subOrder.setStatus(SubOrderStatus.CANCELLED);
                                        subOrder.setCancellationReason(
                                                RefundReason.CUSTOMER_CANCEL);
                                    });
                            subOrderRepository.saveAll(subOrders);
                            if (reservations != null) {
                                reservations.release(order.getId(), order.getDiscountCodeId());
                            }
                            order.setStatus(MasterOrderStatus.CANCELLED);
                            masterOrderRepository.save(order);
                        });
    }
}

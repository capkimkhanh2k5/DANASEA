package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.modules.order.application.RefundProcessingService;
import com.danasea.backend.modules.order.application.dtos.OrderRefundResult;
import com.danasea.backend.modules.order.application.dtos.RequestRefundCommand;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundEvaluationResult;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.ServiceSlotDepartureLookupPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.services.CustomerRefundEligibilityPolicy;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RequestRefundUseCase {

    private DisruptionDecisionService disruptionDecisions;

    @Autowired
    public void setDisruptionDecisions(DisruptionDecisionService disruptionDecisions) {
        this.disruptionDecisions = disruptionDecisions;
    }

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    public void setTransactionalEmailPort(TransactionalEmailPort transactionalEmailPort) {
        this.transactionalEmailPort = transactionalEmailPort;
    }

    private final MasterOrderRepositoryPort masterOrderRepository;
    private final SubOrderRepositoryPort subOrderRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final RefundPolicyEngine refundPolicyEngine;
    private final ServiceSlotDepartureLookupPort departureLookupPort;
    private final JpaRefundRepository refundRepository;
    private final JpaPaymentRepository paymentRepository;
    private final RefundProcessingService refundProcessingService;

    @Autowired
    public RequestRefundUseCase(
            MasterOrderRepositoryPort masterOrderRepository,
            SubOrderRepositoryPort subOrderRepository,
            PaymentGatewayPort paymentGatewayPort,
            RefundPolicyEngine refundPolicyEngine,
            @Autowired(required = false) ServiceSlotDepartureLookupPort departureLookupPort,
            @Autowired(required = false) JpaRefundRepository refundRepository,
            @Autowired(required = false) JpaPaymentRepository paymentRepository,
            @Autowired(required = false) RefundProcessingService refundProcessingService) {
        this.masterOrderRepository = masterOrderRepository;
        this.subOrderRepository = subOrderRepository;
        this.paymentGatewayPort = paymentGatewayPort;
        this.refundPolicyEngine = refundPolicyEngine;
        this.departureLookupPort = departureLookupPort;
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.refundProcessingService = refundProcessingService;
    }

    public RequestRefundUseCase(
            MasterOrderRepositoryPort masterOrderRepository,
            SubOrderRepositoryPort subOrderRepository,
            PaymentGatewayPort paymentGatewayPort,
            RefundPolicyEngine refundPolicyEngine) {
        this(
                masterOrderRepository,
                subOrderRepository,
                paymentGatewayPort,
                refundPolicyEngine,
                null,
                null,
                null,
                null);
    }

    @Transactional
    public List<OrderRefundResult> execute(RequestRefundCommand command) {
        return execute(command, null);
    }

    @Transactional
    public List<OrderRefundResult> execute(
            RequestRefundCommand command, LocalDateTime departureTime) {
        if (command == null
                || command.customerId() == null
                || command.orderOrSubOrderId() == null) {
            throw new IllegalArgumentException("Customer ID and Order ID are required.");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required.");
        }

        RefundReason reason =
                command.requestedReason() == null
                        ? RefundReason.CUSTOMER_REQUEST
                        : command.requestedReason();

        if (reason != RefundReason.CUSTOMER_REQUEST && reason != RefundReason.CUSTOMER_CANCEL) {
            throw new IllegalArgumentException(
                    "Customers may only request a customer cancellation refund.");
        }

        MasterOrder order;
        List<SubOrder> subOrders;

        Optional<MasterOrder> masterOpt =
                masterOrderRepository.findByIdForUpdate(command.orderOrSubOrderId());
        if (masterOpt.isPresent()) {
            order = masterOpt.get();
            subOrders = subOrderRepository.findByMasterOrderIdForUpdate(order.getId());
        } else {
            var parentId =
                    subOrderRepository
                            .findMasterOrderIdById(command.orderOrSubOrderId())
                            .orElseThrow(
                                    () -> new OrderNotFoundException(command.orderOrSubOrderId()));
            order =
                    masterOrderRepository
                            .findByIdForUpdate(parentId)
                            .orElseThrow(() -> new OrderNotFoundException(parentId));
            subOrders =
                    List.of(
                            subOrderRepository
                                    .findByIdForUpdate(command.orderOrSubOrderId())
                                    .orElseThrow());
        }

        // Chặn IDOR: Khách hàng chỉ được yêu cầu hoàn tiền cho đơn của chính mình
        if (!command.customerId().equals(order.getCustomerId())) {
            throw new UnauthorizedOrderAccessException(order.getId(), command.customerId());
        }

        // Kiểm tra Idempotency: Nếu request đã được xử lý thành công trước đó, trả về kết quả cũ
        if (refundRepository != null) {
            List<OrderRefundResult> existingResults = new ArrayList<>();
            for (SubOrder subOrder : subOrders) {
                var existingOpt =
                        refundRepository.findBySubOrderIdAndIdempotencyKey(
                                subOrder.getId(), command.idempotencyKey());
                existingOpt.ifPresent(
                        existing ->
                                existingResults.add(
                                        new OrderRefundResult(
                                                existing.getId(),
                                                subOrder.getId(),
                                                existing.getAmount(),
                                                existing.getRefundPercentage(),
                                                existing.getReason(),
                                                existing.getStatus(),
                                                "IDEMPOTENT_REPLAY")));
            }
            if (!existingResults.isEmpty() && existingResults.size() == subOrders.size()) {
                return existingResults;
            }
        }

        // Chỉ cho phép yêu cầu hoàn tiền khi đơn đã thanh toán
        if (!CustomerRefundEligibilityPolicy.paidOrder(order.getStatus())) {
            throw new InvalidOrderStateException(
                    "Refunds can only be requested for a paid order; current: "
                            + order.getStatus());
        }

        PaymentJpaEntity originalPayment = null;
        if (paymentRepository != null) {
            originalPayment =
                    paymentRepository
                            .findByMasterOrderIdOrderByCreatedAtDesc(order.getId())
                            .stream()
                            .filter(
                                    p ->
                                            p.getStatus() == PaymentStatus.SUCCESS
                                                    || p.getStatus() == PaymentStatus.REFUNDED)
                            .min(Comparator.comparing(p -> !Boolean.TRUE.equals(p.getIsCanonical())))
                            .orElse(null);
        }

        LocalDateTime cancelTime =
                command.cancelTime() != null
                        ? command.cancelTime()
                        : LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        List<OrderRefundResult> results = new ArrayList<>();

        for (SubOrder subOrder : subOrders) {
            if (!CustomerRefundEligibilityPolicy.refundableItem(subOrder.getStatus())) {
                throw new InvalidOrderStateException(
                        "Sub-order "
                                + subOrder.getId()
                                + " is already in terminal state: "
                                + subOrder.getStatus());
            }

            if (disruptionDecisions != null
                    && disruptionDecisions
                            .verifiedReason(subOrder.getId(), subOrder.getSlotId())
                            .isPresent()) {
                var decision =
                        disruptionDecisions.chooseRefund(subOrder.getId(), command.customerId());
                if (decision.getRefundId() == null) continue;
                var stored = refundRepository.findById(decision.getRefundId()).orElseThrow();
                stored.setIdempotencyKey(command.idempotencyKey());
                refundRepository.save(stored);
                results.add(
                        new OrderRefundResult(
                                stored.getId(),
                                subOrder.getId(),
                                stored.getAmount(),
                                stored.getRefundPercentage(),
                                stored.getReason(),
                                stored.getStatus(),
                                stored.getReason().name()));
                continue;
            }

            if (refundRepository != null
                    && !refundRepository.findBySubOrderId(subOrder.getId()).isEmpty()) {
                throw new InvalidOrderStateException("A refund already exists for this sub-order.");
            }

            LocalDateTime effectiveDeparture = departureTime;
            if (effectiveDeparture == null
                    && subOrder.getSlotId() != null
                    && departureLookupPort != null) {
                effectiveDeparture =
                        departureLookupPort.findDepartureTime(subOrder.getSlotId()).orElse(null);
            }

            BigDecimal refundableBase =
                    subOrder.getFinalAmount() != null
                            ? subOrder.getFinalAmount()
                            : subOrder.getSubtotalAmount();
            RefundEvaluationResult evaluation =
                    refundPolicyEngine.evaluate(
                            reason,
                            effectiveDeparture,
                            cancelTime,
                            refundableBase,
                            null,
                            subOrder.getCancellationPolicyVersion());

            if (evaluation.refundAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidOrderStateException(
                        "The cancellation policy does not allow a refund for sub-order "
                                + subOrder.getId()
                                + " (within 24 hours).");
            }

            if (refundRepository != null) {
                var existingOpt =
                        refundRepository.findBySubOrderIdAndIdempotencyKey(
                                subOrder.getId(), command.idempotencyKey());
                if (existingOpt.isPresent()) {
                    RefundJpaEntity existing = existingOpt.get();
                    results.add(
                            new OrderRefundResult(
                                    existing.getId(),
                                    subOrder.getId(),
                                    existing.getAmount(),
                                    existing.getRefundPercentage(),
                                    existing.getReason(),
                                    existing.getStatus(),
                                    evaluation.policyCode()));
                    continue;
                }

                RefundJpaEntity refund = new RefundJpaEntity();
                refund.setSubOrderId(subOrder.getId());
                refund.setAmount(evaluation.refundAmount());
                refund.setRefundPercentage(evaluation.refundPercentage());
                refund.setReason(reason);
                refund.setStatus(RefundStatus.PENDING);
                refund.setRequestedBy(command.customerId());
                refund.setIdempotencyKey(command.idempotencyKey());
                if (originalPayment != null) {
                    refund.setProvider(originalPayment.getProvider());
                    refund.setPaymentId(originalPayment.getId());
                    refund.setProviderTransactionId(originalPayment.getProviderTransactionId());
                }
                refund = refundRepository.save(refund);

                if (transactionalEmailPort != null) {
                    OffsetDateTime now = OffsetDateTime.now();
                    transactionalEmailPort.enqueue(new EmailRequest(
                            TransactionalEmailType.REFUND_REQUESTED,
                            "refund:" + refund.getId() + ":requested",
                            "USER:" + command.customerId(),
                            now,
                            now,
                            null,
                            1,
                            Map.of(
                                    "refundId", refund.getId().toString(),
                                    "subOrderId", subOrder.getId().toString(),
                                    "amount", refund.getAmount() != null ? refund.getAmount().doubleValue() : 0.0
                            )
                    ));
                }

                results.add(
                        new OrderRefundResult(
                                refund.getId(),
                                subOrder.getId(),
                                evaluation.refundAmount(),
                                evaluation.refundPercentage(),
                                reason,
                                RefundStatus.PENDING,
                                evaluation.policyCode()));
            } else {
                throw new IllegalStateException(
                        "Refund persistence is required before gateway processing.");
            }
        }

        return results;
    }
}

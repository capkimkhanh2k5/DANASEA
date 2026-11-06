package com.danasea.backend.modules.order.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.order.domain.events.RefundCompletedEvent;
import com.danasea.backend.modules.order.domain.exceptions.InvalidWebhookException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundRequest;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.RefundResult;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.dtos.RefundWebhookResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;

@Service
public class RefundProcessingService {
    public static final int MAX_RETRIES = 3;
    private static final String AWAITING = "GATEWAY_TIMEOUT_AWAITING_VERIFICATION";
    private enum Preparation { CREATE, RECONCILE, BLOCKED }

    private ApplicationEventPublisher eventPublisher;

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    public void setTransactionalEmailPort(TransactionalEmailPort transactionalEmailPort) {
        this.transactionalEmailPort = transactionalEmailPort;
    }

    private final JpaRefundRepository refundRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaPaymentRepository paymentRepository;
    private final JpaBookingRepository bookingRepository;
    private final JpaServiceSlotRepository serviceSlotRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final TransactionTemplate transaction;

    @Autowired
    public RefundProcessingService(JpaRefundRepository refundRepository, JpaSubOrderRepository subOrderRepository,
            JpaMasterOrderRepository masterOrderRepository, JpaPaymentRepository paymentRepository,
            JpaBookingRepository bookingRepository, JpaServiceSlotRepository serviceSlotRepository,
            PaymentGatewayPort paymentGatewayPort, PlatformTransactionManager transactionManager) {
        this.refundRepository = refundRepository;
        this.subOrderRepository = subOrderRepository;
        this.masterOrderRepository = masterOrderRepository;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.serviceSlotRepository = serviceSlotRepository;
        this.paymentGatewayPort = paymentGatewayPort;
        this.transaction = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        if (transaction != null) {
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
    }

    public RefundProcessingService(JpaRefundRepository refundRepository, JpaSubOrderRepository subOrderRepository,
            JpaMasterOrderRepository masterOrderRepository, JpaPaymentRepository paymentRepository,
            JpaBookingRepository bookingRepository, JpaServiceSlotRepository serviceSlotRepository,
            PaymentGatewayPort paymentGatewayPort) {
        this(refundRepository, subOrderRepository, masterOrderRepository, paymentRepository, bookingRepository,
                serviceSlotRepository, paymentGatewayPort, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean processRefund(UUID refundId) {
        if (transaction == null) {
            throw new IllegalStateException("Transactional refund processor is not configured.");
        }
        Preparation preparation = transaction.execute(tx -> {
            RefundJpaEntity refund = refundRepository.findByIdForUpdate(refundId).orElse(null);
            if (refund == null || refund.getStatus() != RefundStatus.PENDING) {
                return Preparation.RECONCILE;
            }
            try {
                RefundContext context = lockContext(refund);
                initializeContext(refund, context.payment());
                if (refund.getGatewayRequestedAt() != null || (refund.getLastError() != null
                        && !refund.getLastError().startsWith("REFUND_CONTEXT_REQUIRES_REVIEW"))) {
                    if (refund.getGatewayRequestedAt() == null) {
                        refund.setGatewayRequestedAt(OffsetDateTime.now());
                        refundRepository.saveAndFlush(refund);
                    }
                    return Preparation.RECONCILE;
                }
                refund.setGatewayRequestedAt(OffsetDateTime.now());
                refund.setLastError(AWAITING);
                refundRepository.saveAndFlush(refund);
                return Preparation.CREATE;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                configurationError(refund, ex.getMessage());
                return Preparation.BLOCKED;
            }
        });
        if (preparation == null || preparation == Preparation.BLOCKED) {
            return false;
        }
        boolean fresh = preparation == Preparation.CREATE;
        return Boolean.TRUE.equals(transaction.execute(tx -> {
            RefundJpaEntity refund = refundRepository.findByIdForUpdate(refundId).orElse(null);
            if (refund == null) {
                return false;
            }
            if (refund.getStatus() != RefundStatus.PENDING) {
                return refund.getStatus() == RefundStatus.PROCESSED;
            }
            if (refund.getGatewayRequestId() == null || refund.getProviderAmount() == null) {
                return false;
            }
            RefundContext context;
            try {
                context = lockContext(refund);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                configurationError(refund, ex.getMessage());
                return false;
            }
            GatewayRefundRequest request = toGatewayRequest(refund, context.payment());
            RefundResult result;
            try {
                result = fresh ? paymentGatewayPort.requestRefund(request)
                        : paymentGatewayPort.queryRefund(request, refund.getProviderRefundId());
            } catch (Exception ex) {
                awaitingVerification(refund, ex.getMessage());
                return false;
            }
            return acceptResult(refund, context, result);
        }));
    }

    @Transactional
    public RefundWebhookResponse processPayPalNotification(String eventId, String providerRefundId, String operationId) {
        if (eventId == null || eventId.isBlank() || providerRefundId == null || providerRefundId.isBlank() || operationId == null) {
            throw new InvalidWebhookException("PayPal event and refund identifiers are required.");
        }
        RefundJpaEntity candidate = refundRepository.findByProviderAndProviderRefundId(PaymentProvider.PAYPAL, providerRefundId)
                .orElseGet(() -> refundRepository.findByGatewayRequestId(operationId).orElse(null));
        if (candidate == null) {
            throw new InvalidWebhookException("PayPal refund operation was not found.");
        }
        RefundJpaEntity refund = refundRepository.findByIdForUpdate(candidate.getId()).orElseThrow();
        if (refund.getProvider() != PaymentProvider.PAYPAL
                || (refund.getProviderRefundId() != null && !providerRefundId.equals(refund.getProviderRefundId()))
                || (!operationId.isBlank() && !operationId.equals(refund.getGatewayRequestId()))) {
            throw new InvalidWebhookException("PayPal refund ID does not match the persisted operation.");
        }
        if (refund.getStatus() != RefundStatus.PENDING) {
            return new RefundWebhookResponse(eventId, refund.getId(), refund.getStatus(), true);
        }
        RefundContext context = lockContext(refund);
        RefundResult result = paymentGatewayPort.queryRefund(toGatewayRequest(refund, context.payment()), providerRefundId);
        acceptResult(refund, context, result);
        refund.setWebhookEventId(eventId);
        refundRepository.save(refund);
        return new RefundWebhookResponse(eventId, refund.getId(), refund.getStatus(), false);
    }

    private record RefundContext(SubOrderJpaEntity subOrder, MasterOrderJpaEntity order, PaymentJpaEntity payment) { }

    private RefundContext lockContext(RefundJpaEntity refund) {
        SubOrderJpaEntity subOrder = subOrderRepository.findById(refund.getSubOrderId())
                .orElseThrow(() -> new IllegalStateException("Refund sub-order not found."));
        UUID paymentId = refund.getPaymentId();
        if (paymentId == null) {
            paymentId = paymentRepository.findByMasterOrderIdOrderByCreatedAtDesc(subOrder.getMasterOrderId()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS || p.getStatus() == PaymentStatus.REFUNDED)
                    .map(PaymentJpaEntity::getId).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No successful original payment found."));
        }
        PaymentJpaEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalStateException("Original payment not found."));
        MasterOrderJpaEntity order = masterOrderRepository.findByIdForUpdate(subOrder.getMasterOrderId())
                .orElseThrow(() -> new IllegalStateException("Refund master order not found."));
        if (!order.getId().equals(payment.getMasterOrderId())
                || (payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.REFUNDED)
                || (refund.getProvider() != null && refund.getProvider() != payment.getProvider())) {
            throw new IllegalStateException("Refund does not match the original successful payment.");
        }
        return new RefundContext(subOrder, order, payment);
    }

    private void initializeContext(RefundJpaEntity refund, PaymentJpaEntity payment) {
        if (payment.getProviderTransactionId() == null || payment.getProviderTransactionId().isBlank()
                || payment.getProviderAmount() == null || payment.getProviderAmount().signum() <= 0
                || payment.getAmount() == null || payment.getAmount().signum() <= 0
                || payment.getProviderCurrency() == null || refund.getAmount() == null || refund.getAmount().signum() <= 0) {
            throw new IllegalStateException("Original gateway transaction, amount, and currency are required.");
        }
        if (payment.getProvider() != PaymentProvider.PAYPAL && payment.getProvider() != PaymentProvider.VNPAY) {
            throw new IllegalStateException("Refund provider is not supported.");
        }
        if (payment.getProvider() == PaymentProvider.VNPAY
                && (payment.getProviderOrderId() == null || payment.getProviderTransactionDate() == null)) {
            throw new IllegalStateException("Original VNPay reference and transaction date are required.");
        }
        List<UUID> subOrderIds = subOrderRepository.findByMasterOrderId(payment.getMasterOrderId()).stream()
                .map(SubOrderJpaEntity::getId).toList();
        List<RefundJpaEntity> others = refundRepository.findBySubOrderIdIn(subOrderIds).stream()
                .filter(r -> !r.getId().equals(refund.getId()) && r.getStatus() != RefundStatus.FAILED).toList();
        BigDecimal otherAmount = others.stream().map(RefundJpaEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (otherAmount.add(refund.getAmount()).compareTo(payment.getAmount()) > 0) {
            throw new IllegalStateException("Refund reservations exceed the original payment amount.");
        }
        if (refund.getGatewayRequestId() != null) {
            return;
        }
        BigDecimal providerAmount = payment.getProviderAmount().multiply(refund.getAmount())
                .divide(payment.getAmount(), 2, RoundingMode.HALF_UP);
        BigDecimal allocated = others.stream().filter(r -> r.getProviderAmount() != null)
                .map(RefundJpaEntity::getProviderAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (otherAmount.add(refund.getAmount()).compareTo(payment.getAmount()) == 0
                && others.stream().allMatch(r -> r.getProviderAmount() != null)) {
            providerAmount = payment.getProviderAmount().subtract(allocated);
        }
        if (providerAmount.signum() <= 0 || providerAmount.add(allocated).compareTo(payment.getProviderAmount()) > 0) {
            throw new IllegalStateException("Refund amount cannot be represented in the original gateway currency.");
        }
        refund.setPaymentId(payment.getId());
        refund.setProvider(payment.getProvider());
        refund.setProviderTransactionId(payment.getProviderTransactionId());
        refund.setProviderAmount(providerAmount);
        refund.setProviderCurrency(payment.getProviderCurrency());
        refund.setGatewayRequestId(UUID.randomUUID().toString());
        refundRepository.saveAndFlush(refund);
    }

    private GatewayRefundRequest toGatewayRequest(RefundJpaEntity refund, PaymentJpaEntity payment) {
        return new GatewayRefundRequest(refund.getProvider(), payment.getProviderTransactionId(), payment.getProviderOrderId(),
                payment.getProviderTransactionDate(), refund.getProviderAmount(), refund.getProviderCurrency(),
                refund.getAmount().compareTo(payment.getAmount()) == 0, refund.getGatewayRequestId());
    }

    private boolean acceptResult(RefundJpaEntity refund, RefundContext context, RefundResult result) {
        if (result == null || result.status() == GatewayRefundStatus.UNKNOWN) {
            awaitingVerification(refund, result == null ? "No gateway result" : result.message());
            return false;
        }
        if (result.providerRefundId() == null || result.providerRefundId().isBlank() || result.amount() == null
                || result.amount().compareTo(refund.getProviderAmount()) != 0
                || !refund.getProviderCurrency().equals(result.currency())
                || (refund.getProviderRefundId() != null && !refund.getProviderRefundId().equals(result.providerRefundId()))) {
            awaitingVerification(refund, "Gateway refund ID, amount, or currency does not match.");
            return false;
        }
        refund.setProviderRefundId(result.providerRefundId());
        if (result.status() == GatewayRefundStatus.COMPLETED && result.success()) {
            refund.setStatus(RefundStatus.PROCESSED);
            refund.setProcessedAt(OffsetDateTime.now());
            refund.setLastError(null);
            refund.setNextAttemptAt(null);
            refundRepository.saveAndFlush(refund);
            applyRefundSuccess(refund, context.subOrder(), context.order(), context.payment());
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new RefundCompletedEvent(refund.getId(), context.subOrder().getId(),
                        context.order().getId(), context.order().getCustomerId(), refund.getAmount(), refund.getStatus()));
            }
            return true;
        }
        if (result.status() == GatewayRefundStatus.FAILED) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setLastError("GATEWAY_REFUND_FAILED: " + result.message());
            refundRepository.save(refund);
            if (transactionalEmailPort != null) {
                OffsetDateTime now = OffsetDateTime.now();
                transactionalEmailPort.enqueue(new EmailRequest(
                        TransactionalEmailType.REFUND_NEEDS_ATTENTION,
                        "refund:" + refund.getId() + ":failed",
                        "OPS:FINANCE",
                        now,
                        now,
                        null,
                        1,
                        Map.of(
                                "refundId", refund.getId().toString(),
                                "reason", "FAILED",
                                "lastError", refund.getLastError() != null ? refund.getLastError() : "GATEWAY_REFUND_FAILED",
                                "amount", refund.getAmount() != null ? refund.getAmount().doubleValue() : 0.0
                        )
                ));
            }
            return false;
        }
        awaitingVerification(refund, "Gateway refund remains pending.");
        return false;
    }

    private void awaitingVerification(RefundJpaEntity refund, String detail) {
        refund.setLastError(AWAITING + ": " + detail);
        int attempts = Math.min(20, (refund.getVerificationAttempts() == null ? 0 : refund.getVerificationAttempts()) + 1);
        refund.setVerificationAttempts(attempts);
        refund.setNextAttemptAt(OffsetDateTime.now().plusSeconds(Math.min(300, 30L << Math.min(4, attempts - 1))));
        refundRepository.save(refund);
    }

    private void configurationError(RefundJpaEntity refund, String detail) {
        if (refund.getGatewayRequestedAt() != null || (refund.getLastError() != null && refund.getLastError().contains(AWAITING))) {
            awaitingVerification(refund, detail);
            return;
        }
        refund.setLastError("REFUND_CONTEXT_REQUIRES_REVIEW: " + detail);
        refund.setRetryCount((refund.getRetryCount() == null ? 0 : refund.getRetryCount()) + 1);
        refund.setNextAttemptAt(OffsetDateTime.now().plusMinutes(5));
        refundRepository.save(refund);
    }

    private void applyRefundSuccess(RefundJpaEntity refund, SubOrderJpaEntity subOrder,
            MasterOrderJpaEntity masterOrder, PaymentJpaEntity originalPayment) {
        boolean alreadyReleased = subOrder.getStatus() == SubOrderStatus.CANCELLED || subOrder.getStatus() == SubOrderStatus.REJECTED
                || subOrder.getStatus() == SubOrderStatus.REFUNDED || subOrder.getStatus() == SubOrderStatus.PARTIALLY_REFUNDED;
        if (subOrder.getCancellationReason() == null && refund.getReason() != RefundReason.COMPENSATION
                && refund.getReason() != RefundReason.DISPUTE) {
            subOrder.setCancellationReason(refund.getReason());
        }
        subOrder.setStatus(refund.getRefundPercentage() != null && refund.getRefundPercentage().compareTo(BigDecimal.valueOf(100)) == 0
                ? SubOrderStatus.REFUNDED : SubOrderStatus.PARTIALLY_REFUNDED);
        subOrderRepository.save(subOrder);
        if (!alreadyReleased && subOrder.getSlotId() != null && subOrder.getQuantity() != null && subOrder.getQuantity() > 0) {
            if (subOrder.getBookingItemId() != null) {
                serviceSlotRepository.releaseBookingItemCapacity(subOrder.getBookingItemId());
            } else {
                serviceSlotRepository.decrementBookedCount(subOrder.getSlotId(), subOrder.getQuantity());
            }
        }
        List<SubOrderJpaEntity> allSubOrders = subOrderRepository.findByMasterOrderId(masterOrder.getId());
        List<UUID> subOrderIds = allSubOrders.stream().map(SubOrderJpaEntity::getId).toList();
        BigDecimal processedTotal = refundRepository.findBySubOrderIdInAndStatus(subOrderIds, RefundStatus.PROCESSED).stream()
                .filter(r -> originalPayment.getId().equals(r.getPaymentId()))
                .map(RefundJpaEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (processedTotal.compareTo(originalPayment.getAmount()) >= 0) {
            originalPayment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(originalPayment);
            masterOrder.setPaymentStatus(PaymentOrderStatus.REFUNDED);
            masterOrderRepository.save(masterOrder);
        }
        boolean allTerminal = !allSubOrders.isEmpty() && allSubOrders.stream().allMatch(s -> s.getStatus() == SubOrderStatus.REFUNDED
                || s.getStatus() == SubOrderStatus.CANCELLED || s.getStatus() == SubOrderStatus.REJECTED);
        if (allTerminal) {
            masterOrder.setStatus(MasterOrderStatus.CANCELLED);
            masterOrderRepository.save(masterOrder);
            if (masterOrder.getBookingId() != null) {
                bookingRepository.findById(masterOrder.getBookingId()).ifPresent(b -> {
                    b.setStatus(BookingStatus.CANCELLED);
                    bookingRepository.save(b);
                });
            }
        }
    }
}

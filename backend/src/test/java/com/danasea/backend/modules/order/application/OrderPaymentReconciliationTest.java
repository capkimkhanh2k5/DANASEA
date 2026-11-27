package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.danasea.backend.configs.properties.PaymentProperties;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.booking.application.dtos.ConfirmBookingCommand;
import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.usecases.CreatePaymentIntentUseCase;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPaymentService Reconciliation Unit Tests")
class OrderPaymentReconciliationTest {

    @Mock private JpaBookingRepository bookingRepository;
    @Mock private JpaMasterOrderRepository masterOrderRepository;
    @Mock private JpaSubOrderRepository subOrderRepository;
    @Mock private JpaPaymentRepository paymentRepository;
    @Mock private JpaRefundRepository refundRepository;
    @Mock private JpaServiceSlotRepository serviceSlotRepository;
    @Mock private VendorInternalApi vendorInternalApi;
    @Mock private ConfirmBookingUseCase confirmBookingUseCase;
    @Mock private CreatePaymentIntentUseCase createPaymentIntentUseCase;
    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private AuditLogInternalApi auditLogInternalApi;
    @Mock private PlatformTransactionManager transactionManager;

    private OrderPaymentService service;

    @BeforeEach
    void setUp() {
        PaymentWebhookSigner signer = new PaymentWebhookSigner(new PaymentProperties(
                "test-payment-webhook-secret-minimum-256-bits-long"));
        service = new OrderPaymentService(
                bookingRepository,
                masterOrderRepository,
                subOrderRepository,
                paymentRepository,
                refundRepository,
                serviceSlotRepository,
                vendorInternalApi,
                new RefundPolicyEngine(),
                signer,
                confirmBookingUseCase,
                new ObjectMapper(),
                createPaymentIntentUseCase,
                null,
                paymentGatewayPort,
                transactionManager,
                auditLogInternalApi
        );
    }

    @Test
    @DisplayName("Should return false when payment not found or not pending")
    void shouldReturnFalseWhenPaymentNotFoundOrNotPending() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.empty());

        boolean result = service.reconcilePayment(paymentId);
        assertThat(result).isFalse();

        PaymentJpaEntity successPayment = new PaymentJpaEntity();
        successPayment.setId(paymentId);
        successPayment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(successPayment));

        result = service.reconcilePayment(paymentId);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("VNPay: queryPayment returns COMPLETED -> should update to SUCCESS and record audit")
    void reconcileVNPay_WhenCompleted_ShouldUpdateToSuccess() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setId(paymentId);
        payment.setMasterOrderId(orderId);
        payment.setAmount(BigDecimal.valueOf(500000));
        payment.setProviderAmount(BigDecimal.valueOf(500000));
        payment.setProviderCurrency("VND");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setProviderOrderId("VNP-ORD-001");
        payment.setProviderTransactionDate("20261009100000");

        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(orderId);
        order.setBookingId(bookingId);
        order.setCustomerId(customerId);
        order.setTotalAmount(BigDecimal.valueOf(500000));
        order.setStatus(MasterOrderStatus.PENDING_PAYMENT);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setId(UUID.randomUUID());
        subOrder.setMasterOrderId(orderId);
        subOrder.setStatus(SubOrderStatus.PENDING);

        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentGatewayPort.queryPayment(eq(PaymentProvider.VNPAY), eq("VNP-ORD-001"), eq("20261009100000")))
                .thenReturn(new PaymentCaptureResult(true, "VNP-TXN-8888", BigDecimal.valueOf(500000), "VND", "COMPLETED", "Success"));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of(subOrder));

        boolean reconciled = service.reconcilePayment(paymentId);

        assertThat(reconciled).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getProviderTransactionId()).isEqualTo("VNP-TXN-8888");
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);

        verify(paymentRepository).save(payment);
        verify(masterOrderRepository).save(order);
        verify(confirmBookingUseCase).execute(new ConfirmBookingCommand(bookingId, customerId));
        verify(auditLogInternalApi).recordTransactionalAuditLog(
                isNull(),
                eq("RECONCILE_PAYMENT_SUCCESS"),
                eq("PAYMENT"),
                eq(paymentId),
                anyString()
        );
    }

    @Test
    @DisplayName("VNPay: queryPayment returns FAILED -> should update to FAILED and record audit")
    void reconcileVNPay_WhenFailed_ShouldUpdateToFailed() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setId(paymentId);
        payment.setMasterOrderId(orderId);
        payment.setAmount(BigDecimal.valueOf(500000));
        payment.setProviderAmount(BigDecimal.valueOf(500000));
        payment.setProviderCurrency("VND");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setProviderOrderId("VNP-ORD-002");
        payment.setProviderTransactionDate("20261009100000");

        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(orderId);
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentGatewayPort.queryPayment(eq(PaymentProvider.VNPAY), eq("VNP-ORD-002"), eq("20261009100000")))
                .thenReturn(new PaymentCaptureResult(false, null, null, null, "FAILED", "Payment cancelled by customer"));

        boolean reconciled = service.reconcilePayment(paymentId);

        assertThat(reconciled).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getLastError()).contains("GATEWAY_PAYMENT_FAILED");

        verify(paymentRepository).save(payment);
        verify(auditLogInternalApi).recordTransactionalAuditLog(
                isNull(),
                eq("RECONCILE_PAYMENT_FAILED"),
                eq("PAYMENT"),
                eq(paymentId),
                anyString()
        );
    }

    @Test
    @DisplayName("PayPal: queryPayment returns COMPLETED -> should update to SUCCESS and record audit")
    void reconcilePayPal_WhenCompleted_ShouldUpdateToSuccess() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setId(paymentId);
        payment.setMasterOrderId(orderId);
        payment.setAmount(BigDecimal.valueOf(50));
        payment.setProviderAmount(BigDecimal.valueOf(50));
        payment.setProviderCurrency("USD");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(PaymentProvider.PAYPAL);
        payment.setProviderOrderId("PAYPAL-ORDER-999");

        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(orderId);
        order.setBookingId(bookingId);
        order.setCustomerId(customerId);
        order.setTotalAmount(BigDecimal.valueOf(50));
        order.setStatus(MasterOrderStatus.PENDING_PAYMENT);

        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(paymentGatewayPort.queryPayment(eq(PaymentProvider.PAYPAL), eq("PAYPAL-ORDER-999"), isNull()))
                .thenReturn(new PaymentCaptureResult(true, "PAYPAL-CAPTURE-123", BigDecimal.valueOf(50), "USD", "COMPLETED", "Captured"));
        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByMasterOrderId(orderId)).thenReturn(List.of());

        boolean reconciled = service.reconcilePayment(paymentId);

        assertThat(reconciled).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.PAID);

        verify(paymentRepository).save(payment);
        verify(masterOrderRepository).save(order);
        verify(auditLogInternalApi).recordTransactionalAuditLog(
                isNull(),
                eq("RECONCILE_PAYMENT_SUCCESS"),
                eq("PAYMENT"),
                eq(paymentId),
                anyString()
        );
    }

    @Test
    @DisplayName("Local expiry without gateway verification must remain pending")
    void expiredPaymentWithoutVerifiedGatewayOutcomeRemainsPending() {
        UUID paymentId = UUID.randomUUID();
        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setExpiresAt(OffsetDateTime.now().minusMinutes(5));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        assertThat(service.reconcilePayment(paymentId)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getLastError()).contains("AWAITING_VERIFICATION");
        assertThat(payment.getReconciliationNextAttemptAt()).isAfter(OffsetDateTime.now());
        verify(auditLogInternalApi).recordTransactionalAuditLog(isNull(), eq("RECONCILE_PAYMENT_PENDING"),
                eq("PAYMENT"), eq(paymentId), anyString());
    }
    private PaymentJpaEntity seedReviewPayment() {
        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setId(UUID.randomUUID());
        payment.setMasterOrderId(UUID.randomUUID());
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setProviderOrderId("review-original-payment");
        payment.setProviderTransactionDate("20261009100000");
        payment.setProviderAmount(new BigDecimal("500000"));
        payment.setProviderCurrency("VND");
        payment.setAmount(new BigDecimal("500000"));
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        return payment;
    }

    private void stubReviewOrder(PaymentJpaEntity payment) {
        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setId(payment.getMasterOrderId());
        order.setCustomerId(UUID.randomUUID());
        order.setBookingId(UUID.randomUUID());
        order.setTotalAmount(payment.getAmount());
        order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        when(masterOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    @Test
    void reviewRejectsWrongProviderAmount() {
        var payment = seedReviewPayment();
        stubReviewOrder(payment);
        when(paymentGatewayPort.queryPayment(any(), anyString(), anyString()))
            .thenReturn(new PaymentCaptureResult(true, "review-txn", new BigDecimal("1"), "VND", "COMPLETED", "Success"));
        service.reconcilePayment(payment.getId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void reviewRejectsWrongProviderCurrency() {
        var payment = seedReviewPayment();
        stubReviewOrder(payment);
        when(paymentGatewayPort.queryPayment(any(), anyString(), anyString()))
            .thenReturn(new PaymentCaptureResult(true, "review-txn", payment.getProviderAmount(), "USD", "COMPLETED", "Success"));
        service.reconcilePayment(payment.getId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void reviewTimeoutAfterLocalExpiryRemainsPendingVerification() {
        var payment = seedReviewPayment();
        payment.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(paymentGatewayPort.queryPayment(any(), anyString(), anyString()))
            .thenThrow(new ResourceAccessException("Read timed out"));
        service.reconcilePayment(payment.getId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getLastError()).contains("AWAITING_VERIFICATION");
    }
}

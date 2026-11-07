package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.*;
import com.danasea.backend.modules.order.domain.ports.*;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.*;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.*;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@ActiveProfiles("test")
class PaymentCaptureConcurrencyIntegrationTest extends BaseSecurityIntegrationTest {
    @Autowired private OrderPaymentService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JpaPaymentRepository payments;
    @Autowired private JpaMasterOrderRepository orders;
    @Autowired private RefundProcessingService worker;
    @Autowired private JpaSubOrderRepository subs;
    @Autowired private JpaRefundRepository refunds;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean(name="payPalPaymentAdapter") private PaymentGatewayPort gateway;
    @MockitoBean private ConfirmBookingUseCase confirmation;

    @Test
    void captureRacingWithWebhookConfirmsExactlyOnceUsingPostgresLocks() throws Exception {
        var fixture = newPayment();
        UUID customerId = fixture.customerId(); UUID paymentId = fixture.paymentId();
        String providerOrderId = fixture.providerOrderId(); UUID orderId = fixture.orderId();
        var captureStarted = new CountDownLatch(1); var releaseCapture = new CountDownLatch(1); var verificationStarted = new CountDownLatch(1);
        when(gateway.captureOrder(any(),any(),any())).thenAnswer(inv -> {
            captureStarted.countDown(); assertThat(releaseCapture.await(10,TimeUnit.SECONDS)).isTrue();
            return new PaymentCaptureResult(true,"CAPTURECONCURRENT",new BigDecimal("19.31"),"USD","COMPLETED","completed");
        });
        when(gateway.verifyWebhookSignature(any(),any())).thenAnswer(inv -> {verificationStarted.countDown();return true;});
        String payload="{\"id\":\"WHAUDIT"+paymentId+"\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":{\"id\":\"CAPTURECONCURRENT\",\"status\":\"COMPLETED\",\"custom_id\":\""+paymentId+"\",\"amount\":{\"value\":\"19.31\",\"currency_code\":\"USD\"},\"supplementary_data\":{\"related_ids\":{\"order_id\":\""+providerOrderId+"\"}}}}";
        try(var executor=Executors.newFixedThreadPool(2)) {
            var capture=executor.submit(() -> service.capturePayPalOrder(customerId,paymentId,providerOrderId,"capture-audit-stable-key"));
            assertThat(captureStarted.await(10,TimeUnit.SECONDS)).isTrue();
            var webhook=executor.submit(() -> service.processPayPalWebhook(payload,"transmission-id","timestamp","signature","https://api.paypal.com/cert","SHA256withRSA"));
            assertThat(verificationStarted.await(10,TimeUnit.SECONDS)).isTrue();
            releaseCapture.countDown();
            assertThat(capture.get(10,TimeUnit.SECONDS).status()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(webhook.get(10,TimeUnit.SECONDS).alreadyProcessed()).isTrue();
        }
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(MasterOrderStatus.PAID);
        verify(gateway,times(1)).captureOrder(any(),any(),any());
        verify(confirmation,times(1)).execute(any());
    }

    @Test
    void captureOperationIsCommittedBeforeRequestAndSurvivesTimeout() throws Exception {
        var fixture = newPayment();
        when(gateway.captureOrder(any(), any(), any())).thenAnswer(inv -> {
            try (var reader = Executors.newSingleThreadExecutor()) {
                String committedRequest = reader.submit(() -> jdbc.queryForObject(
                        "SELECT capture_request_id FROM payments WHERE id = ?", String.class, fixture.paymentId())).get(5, TimeUnit.SECONDS);
                assertThat(committedRequest).isEqualTo(inv.getArgument(2));
            }
            throw new PaymentGatewayException("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        });
        assertThatThrownBy(() -> service.capturePayPalOrder(fixture.customerId(), fixture.paymentId(), fixture.providerOrderId(), "first-client-key"))
                .isInstanceOf(PaymentGatewayException.class);
        var persisted = payments.findById(fixture.paymentId()).orElseThrow();
        assertThat(persisted.getCaptureRequestId()).isNotBlank();
        assertThat(persisted.getLastError()).contains("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.PENDING);
        when(gateway.queryCapture(PaymentProvider.PAYPAL, fixture.providerOrderId())).thenReturn(
                new PaymentCaptureResult(true, "CAPTURE-RECONCILED-" + fixture.paymentId(), new BigDecimal("19.31"), "USD", "COMPLETED", null));
        service.reconcilePayPalCapture(fixture.paymentId());
        assertThat(payments.findById(fixture.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(gateway, times(1)).captureOrder(any(), any(), any());
        verify(gateway).queryCapture(PaymentProvider.PAYPAL, fixture.providerOrderId());
        verify(confirmation).execute(any());
    }

    @Test
    void legacyRefundOperationIsCommittedBeforeQueryAndNeverResentWhenQueryFails() {
        var fixture = newPayment();
        var payment = payments.findById(fixture.paymentId()).orElseThrow();
        payment.setStatus(PaymentStatus.SUCCESS); payment.setProviderTransactionId("CAPTURE-REFUND-" + fixture.paymentId());
        payments.saveAndFlush(payment);
        var order = orders.findById(fixture.orderId()).orElseThrow(); order.setStatus(MasterOrderStatus.PAID); orders.saveAndFlush(order);
        var sub = new SubOrderJpaEntity(); sub.setMasterOrderId(order.getId()); sub.setSubtotalAmount(payment.getAmount());
        sub.setStatus(SubOrderStatus.CONFIRMED); sub = subs.saveAndFlush(sub);
        var refund = new RefundJpaEntity(); refund.setSubOrderId(sub.getId()); refund.setAmount(payment.getAmount());
        refund.setRefundPercentage(new BigDecimal("100")); refund.setReason(RefundReason.CUSTOMER_REQUEST); refund.setStatus(RefundStatus.PENDING);
        refund.setGatewayRequestedAt(OffsetDateTime.now());
        refund = refunds.saveAndFlush(refund); UUID refundId = refund.getId();
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenAnswer(inv -> {
            GatewayRefundRequest request = inv.getArgument(0);
            try (var reader = Executors.newSingleThreadExecutor()) {
                String committedRequest = reader.submit(() -> jdbc.queryForObject(
                        "SELECT gateway_request_id FROM refunds WHERE id = ?", String.class, refundId)).get(5, TimeUnit.SECONDS);
                assertThat(committedRequest).isEqualTo(request.requestId());
            }
            throw new PaymentGatewayException("Read timeout");
        });
        assertThat(worker.processRefund(refundId)).isFalse();
        String operationId = refunds.findById(refundId).orElseThrow().getGatewayRequestId();
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenThrow(new PaymentGatewayException("Lookup unavailable"));
        assertThat(worker.processRefund(refundId)).isFalse();
        var persisted = refunds.findById(refundId).orElseThrow();
        assertThat(persisted.getGatewayRequestId()).isEqualTo(operationId);
        assertThat(persisted.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(persisted.getLastError()).contains("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        verify(gateway, never()).requestRefund(any(GatewayRefundRequest.class));
        verify(gateway, times(2)).queryRefund(any(GatewayRefundRequest.class), any());
    }

    @Test
    void rolledBackRefundRequestCannotReachTheGateway() {
        var refundId = new AtomicReference<UUID>();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(tx -> {
            // The worker runs in separate committed transactions and cannot see an uncommitted request.
            var refund = new RefundJpaEntity(); refund.setStatus(RefundStatus.PENDING);
            refund.setAmount(new BigDecimal("100000")); refund.setRefundPercentage(new BigDecimal("100"));
            refund.setReason(RefundReason.CUSTOMER_REQUEST); refundId.set(refunds.saveAndFlush(refund).getId());
            tx.setRollbackOnly();
        });
        assertThat(worker.processRefund(refundId.get())).isFalse();
        assertThat(refunds.findById(refundId.get())).isEmpty();
        verifyNoInteractions(gateway);
    }

    private record Fixture(UUID customerId, UUID paymentId, String providerOrderId, UUID orderId) { }

    private Fixture newPayment() {
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,role,locale,created_at,updated_at,session_version) VALUES (?,?,?,'en',now(),now(),0)",customerId,"capture-audit-"+customerId+"@example.com","CUSTOMER");
        jdbc.update("INSERT INTO bookings(id,customer_id,status,total_amount,hold_expires_at,created_at,updated_at) VALUES (?,?,'PENDING_PAYMENT',500000,now()+interval '15 minutes',now(),now())",bookingId,customerId);
        var order = new MasterOrderJpaEntity();
        order.setCustomerId(customerId); order.setBookingId(bookingId); order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("500000")); order.setDiscountAmount(BigDecimal.ZERO);
        order.setIdempotencyKey("order-"+UUID.randomUUID()); order.setPaymentDeadline(OffsetDateTime.now().plusMinutes(15));
        order = orders.saveAndFlush(order);
        var payment = new PaymentJpaEntity(); payment.setMasterOrderId(order.getId());payment.setProvider(PaymentProvider.PAYPAL);
        payment.setProviderAmount(new BigDecimal("19.31"));payment.setProviderCurrency("USD");
        payment.setProviderOrderId("AUDITORDER"+UUID.randomUUID().toString().replace("-",""));
        payment.setStatus(PaymentStatus.PENDING);payment.setAmount(order.getTotalAmount());payment.setIdempotencyKey("payment-"+UUID.randomUUID());
        payment = payments.saveAndFlush(payment);
        return new Fixture(customerId, payment.getId(), payment.getProviderOrderId(), order.getId());
    }
}

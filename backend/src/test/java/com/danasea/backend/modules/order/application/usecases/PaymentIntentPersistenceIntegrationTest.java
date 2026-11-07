package com.danasea.backend.modules.order.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.PaymentWebhookSigner;
import com.danasea.backend.modules.order.application.dtos.CreatePaymentIntentCommand;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.infrastructure.adapters.VNPayPaymentAdapter;
import com.danasea.backend.modules.order.infrastructure.adapters.payos.PayOSSignatureHelper;
import com.danasea.backend.configs.properties.PayOSProperties;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PaymentIntentPersistenceIntegrationTest extends BaseSecurityIntegrationTest {

    @Autowired private CreatePaymentIntentUseCase useCase;
    @Autowired private JpaPaymentRepository payments;
    @Autowired private JpaMasterOrderRepository orders;
    @Autowired private OrderPaymentService paymentService;
    @Autowired private PaymentWebhookSigner signer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private PayOSProperties payOSProperties;

    @MockitoBean(name = "payPalPaymentAdapter") private PaymentGatewayPort gateway;
    @MockitoSpyBean private VNPayPaymentAdapter vnPay;
    @MockitoBean private ConfirmBookingUseCase bookingConfirmation;

    @BeforeEach
    void setUp() {
        when(gateway.createPaymentIntent(any(), any(), any(), any())).thenAnswer(invocation ->
                new PaymentIntentResult(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(3), invocation.getArgument(2),
                        "https://gateway.example/" + invocation.getArgument(0), null,
                        OffsetDateTime.now().plusMinutes(10)));
    }

    @Test
    void internalWebhookRequiresAdminAndItsHmacEvenInTestProfile() throws Exception {
        mockMvc.perform(post("/api/payments/webhook/internal/VNPAY").contentType(MediaType.APPLICATION_JSON)
                .content("{}").header("X-Payment-Signature", "invalid")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/payments/webhook/internal/VNPAY").with(user("customer").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}").header("X-Payment-Signature", "invalid"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payments/webhook/internal/VNPAY").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}").header("X-Payment-Signature", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicRefundCallbackCannotUseInternalHmacAsPayPalVerification() throws Exception {
        String payload = "{\"id\":\"WH-REFUND\",\"event_type\":\"PAYMENT.CAPTURE.REFUNDED\"}";
        mockMvc.perform(post("/api/payments/webhook/paypal/refund").contentType(MediaType.APPLICATION_JSON)
                .content(payload).header("X-Payment-Signature", signer.sign(payload)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/payments/webhook/vnpay/refund").contentType(MediaType.APPLICATION_JSON)
                .content(payload).header("X-Payment-Signature", signer.sign(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    void persistedIntentReplaysAndWebhookFindsTheSamePayment() {
        var order = newOrder();
        var command = command(order, "audit-key-1");
        var first = useCase.execute(command);
        var repeated = useCase.execute(command);
        assertThat(repeated.paymentId()).isEqualTo(first.paymentId());
        assertThat(repeated.paymentUrl()).isEqualTo(first.paymentUrl());
        assertThat(payments.findByMasterOrderIdOrderByCreatedAtDesc(order.getId())).hasSize(1);
        verify(gateway, times(1)).createPaymentIntent(any(), any(), any(), any());

        var payload = webhook(first.paymentId(), "audit-event-" + first.paymentId(), "audit-tx-" + first.paymentId());
        var result = paymentService.processWebhook(PaymentProvider.VNPAY, payload, signer.sign(payload));
        assertThat(result.paymentId()).isEqualTo(first.paymentId());
        assertThat(payments.findById(first.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(paymentService.processWebhook(PaymentProvider.VNPAY, payload, signer.sign(payload)).alreadyProcessed()).isTrue();
    }

    @Test
    void concurrentSameKeyCreatesOnlyOnePaymentAndOneGatewayRequest() throws Exception {
        var order = newOrder();
        var command = command(order, "audit-key-2");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return useCase.execute(command); });
            var second = executor.submit(() -> { start.await(); return useCase.execute(command); });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS).paymentId()).isEqualTo(second.get(20, TimeUnit.SECONDS).paymentId());
        }
        assertThat(payments.findByMasterOrderIdOrderByCreatedAtDesc(order.getId())).hasSize(1);
        verify(gateway, times(1)).createPaymentIntent(any(), any(), any(), any());
    }

    @Test
    void expiredPaymentStatusShouldRemainFailedAfterRejection() {
        var order = newOrder();
        var payment = seededPayment(order, "audit-key-3", PaymentStatus.PENDING, OffsetDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> useCase.execute(command(order, "audit-key-3")))
                .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("expired");
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "SUCCESS", "REFUNDED"})
    void terminalPaymentMustNotBeReissuedAsAPendingIntent(PaymentStatus status) {
        var order = newOrder();
        seededPayment(order, "audit-key-4", status, OffsetDateTime.now().plusMinutes(10));
        assertThatThrownBy(() -> useCase.execute(command(order, "audit-key-4")))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void retryAfterAmbiguousGatewayTimeoutShouldKeepTheOriginalPaymentIdentity() {
        var order = newOrder();
        List<UUID> externalPaymentIds = new ArrayList<>();
        when(gateway.createPaymentIntent(any(), any(), any(), any())).thenAnswer(invocation -> {
            UUID paymentId = invocation.getArgument(0);
            externalPaymentIds.add(paymentId);
            if (externalPaymentIds.size() == 1) throw new IllegalStateException("Gateway created intent, then response timed out");
            return new PaymentIntentResult(paymentId, invocation.getArgument(1), invocation.getArgument(3),
                    invocation.getArgument(2), "https://gateway.example/" + paymentId, null,
                    OffsetDateTime.now().plusMinutes(10));
        });
        assertThatThrownBy(() -> useCase.execute(command(order, "audit-key-5"))).isInstanceOf(IllegalStateException.class);
        var pending = payments.findByMasterOrderIdAndIdempotencyKey(order.getId(), "audit-key-5").orElseThrow();
        assertThat(pending.getId()).isEqualTo(externalPaymentIds.getFirst());
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(pending.getPaymentUrl()).isNull();
        useCase.execute(command(order, "audit-key-5"));
        assertThat(externalPaymentIds).hasSize(2);
        assertThat(externalPaymentIds.get(1)).isEqualTo(externalPaymentIds.get(0));
    }

    @Test
    void gatewayFailureReturns502AndPreservesThePaymentForRetry() throws Exception {
        var order = newOrder();
        doThrow(new PaymentGatewayException("Sandbox gateway timed out.")).doCallRealMethod()
                .when(vnPay).createPaymentIntent(any(), any(), any(), eq(PaymentProvider.VNPAY), isNull(), any(), isNull());
        mockMvc.perform(post("/api/payments/{id}/create-intent", order.getId())
                        .with(user(order.getCustomerId().toString()).roles("CUSTOMER"))
                        .header("Idempotency-Key", "http-timeout-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"));
        var payment = payments.findByMasterOrderIdAndIdempotencyKey(order.getId(), "http-timeout-key").orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentUrl()).isNull();

        mockMvc.perform(post("/api/payments/{id}/create-intent", order.getId())
                        .with(user(order.getCustomerId().toString()).roles("CUSTOMER"))
                        .header("Idempotency-Key", "http-timeout-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payment.getId().toString()));
        assertThat(payments.findByMasterOrderIdOrderByCreatedAtDesc(order.getId())).hasSize(1);
        assertThat(payments.findById(payment.getId()).orElseThrow().getIsActive()).isTrue();
    }

    @Test
    void failedPaymentReturns409InsteadOfAPendingIntent() throws Exception {
        var order = newOrder();
        seededPayment(order, "http-failed-key", PaymentStatus.FAILED, OffsetDateTime.now().plusMinutes(10));
        mockMvc.perform(post("/api/payments/{id}/create-intent", order.getId())
                        .with(user(order.getCustomerId().toString()).roles("CUSTOMER"))
                        .header("Idempotency-Key", "http-failed-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ATTEMPT_CLOSED"));
        verify(gateway, never()).createPaymentIntent(any(), any(), any(), any());
    }

    @Test
    void newGatewayMustBeEnabledToCreateAnIntent() throws Exception {
        var order = newOrder();
        mockMvc.perform(post("/api/payments/{id}/create-intent", order.getId())
                        .with(user(order.getCustomerId().toString()).roles("CUSTOMER"))
                        .header("Idempotency-Key", "http-disabled-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"PAYOS\"}"))
                .andExpect(status().isConflict());
        assertThat(payments.findByMasterOrderIdOrderByCreatedAtDesc(order.getId())).isEmpty();
    }

    @Test
    void validButUnmatchedPayosCallbackIsNotAcknowledgedAsProcessed() throws Exception {
        String signature = PayOSSignatureHelper.hmacSha256(payOSProperties.checksumKey(),
                "amount=500000&code=00&currency=VND&orderCode=929292&reference=bank-reference");
        mockMvc.perform(post("/api/payments/webhook/payos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"signature":"%s","data":{"amount":500000,"code":"00","currency":"VND",
                                "orderCode":929292,"reference":"bank-reference"}}
                                """.formatted(signature)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("01"));
    }

    private MasterOrderJpaEntity newOrder() {
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,role,locale,created_at,updated_at,session_version) VALUES (?,?,?,'en',now(),now(),0)",
                customerId, "audit-" + customerId + "@example.com", "CUSTOMER");
        jdbc.update("INSERT INTO bookings(id,customer_id,status,total_amount,hold_expires_at,created_at,updated_at) VALUES (?,?,'PENDING_PAYMENT',500000,now()+interval '15 minutes',now(),now())",
                bookingId, customerId);
        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setCustomerId(customerId);
        order.setBookingId(bookingId);
        order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("500000.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setIdempotencyKey("order-" + UUID.randomUUID());
        order.setPaymentDeadline(OffsetDateTime.now().plusMinutes(15));
        return orders.saveAndFlush(order);
    }

    private CreatePaymentIntentCommand command(MasterOrderJpaEntity order, String key) {
        return new CreatePaymentIntentCommand(order.getCustomerId(), order.getId(), PaymentProvider.VNPAY, key);
    }

    private PaymentJpaEntity seededPayment(MasterOrderJpaEntity order, String key, PaymentStatus status, OffsetDateTime expiry) {
        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setMasterOrderId(order.getId());
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setAmount(order.getTotalAmount());
        payment.setIdempotencyKey(key);
        payment.setStatus(status);
        payment.setExpiresAt(expiry);
        payment.setPaymentUrl("https://gateway.example/seeded");
        return payments.saveAndFlush(payment);
    }

    private String webhook(UUID paymentId, String eventId, String transactionId) {
        return "{\"eventId\":\"" + eventId + "\",\"paymentId\":\"" + paymentId
                + "\",\"providerTransactionId\":\"" + transactionId + "\",\"status\":\"SUCCESS\",\"amount\":500000.00}";
    }
}

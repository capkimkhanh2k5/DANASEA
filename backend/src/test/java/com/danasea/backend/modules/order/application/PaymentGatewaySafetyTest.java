package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;

import com.danasea.backend.configs.properties.PayPalProperties;
import com.danasea.backend.configs.properties.VNPayProperties;
import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.usecases.CreatePaymentIntentUseCase;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.*;
import com.danasea.backend.modules.order.domain.ports.*;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.adapters.*;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.*;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.*;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.fasterxml.jackson.databind.ObjectMapper;

class PaymentGatewaySafetyTest {
    private static final String PAYPAL = "https://api-m.sandbox.paypal.com";
    private static final String VNPAY = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private final BigDecimal amount = new BigDecimal("500000");
    private JpaRefundRepository refunds;
    private JpaSubOrderRepository subs;
    private JpaMasterOrderRepository orders;
    private JpaPaymentRepository payments;
    private PaymentGatewayPort gateway;
    private RefundProcessingService worker;
    private OrderPaymentService service;
    private RefundJpaEntity refund;
    private SubOrderJpaEntity sub;
    private MasterOrderJpaEntity order;
    private PaymentJpaEntity payment;
    private ConfirmBookingUseCase confirmation;

    @BeforeEach
    void setup() {
        refunds = mock(JpaRefundRepository.class);
        subs = mock(JpaSubOrderRepository.class);
        orders = mock(JpaMasterOrderRepository.class);
        payments = mock(JpaPaymentRepository.class);
        gateway = mock(PaymentGatewayPort.class);
        var bookings = mock(JpaBookingRepository.class);
        var slots = mock(JpaServiceSlotRepository.class);
        confirmation = mock(ConfirmBookingUseCase.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        worker = new RefundProcessingService(refunds, subs, orders, payments, bookings, slots, gateway, transactions);
        service = new OrderPaymentService(bookings, orders, subs, payments, refunds, slots,
                mock(VendorInternalApi.class), new RefundPolicyEngine(), mock(PaymentWebhookSigner.class),
                confirmation, new ObjectMapper(), mock(CreatePaymentIntentUseCase.class), worker, gateway, transactions);
        order = new MasterOrderJpaEntity();
        order.setId(UUID.randomUUID()); order.setCustomerId(UUID.randomUUID());
        order.setBookingId(UUID.randomUUID()); order.setStatus(MasterOrderStatus.PAID); order.setTotalAmount(amount);
        sub = new SubOrderJpaEntity(); sub.setId(UUID.randomUUID()); sub.setMasterOrderId(order.getId());
        sub.setStatus(SubOrderStatus.CONFIRMED);
        payment = new PaymentJpaEntity(); payment.setId(UUID.randomUUID()); payment.setMasterOrderId(order.getId());
        payment.setProvider(PaymentProvider.PAYPAL); payment.setProviderTransactionId("CAPTURE-ORIGINAL");
        payment.setProviderAmount(new BigDecimal("19.31")); payment.setProviderCurrency("USD");
        payment.setProviderOrderId("ORDER-REGISTERED"); payment.setStatus(PaymentStatus.SUCCESS); payment.setAmount(amount);
        refund = new RefundJpaEntity(); refund.setId(UUID.randomUUID()); refund.setSubOrderId(sub.getId());
        refund.setAmount(new BigDecimal("100000")); refund.setRefundPercentage(new BigDecimal("50"));
        refund.setStatus(RefundStatus.PENDING); refund.setRetryCount(0); refund.setIdempotencyKey("refund-stable-key");
        when(refunds.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
        when(subs.findById(sub.getId())).thenReturn(Optional.of(sub));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.findByMasterOrderIdOrderByCreatedAtDesc(order.getId())).thenReturn(List.of(payment));
        when(subs.findByMasterOrderId(order.getId())).thenReturn(List.of(sub));
    }

    @Test
    void paypalPendingRefundMustNotBeReportedAsCompleted() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = paypal(builder);
        server.expect(requestTo(PAYPAL + "/v1/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"audit-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(PAYPAL + "/v2/payments/captures/CAPTURE-ORIGINAL/refund"))
                .andRespond(withSuccess("{\"id\":\"REFUND-PENDING\",\"status\":\"PENDING\",\"amount\":{\"value\":\"19.31\",\"currency_code\":\"USD\"}}", MediaType.APPLICATION_JSON));
        var result = adapter.requestRefund(new GatewayRefundRequest(PaymentProvider.PAYPAL, "CAPTURE-ORIGINAL", "ORDER-REGISTERED", null, new BigDecimal("19.31"), "USD", true, "refund-stable-key"));
        server.verify();
        assertThat(result.success()).as("PENDING must not mean funds refunded").isFalse();
    }

    @Test
    void failedReconciliationMustNotResubmitAnAmbiguousRefund() {
        refund.setLastError("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenThrow(new PaymentGatewayException("Gateway lookup unavailable"));
        worker.processRefund(refund.getId());
        verify(gateway, never()).requestRefund(any(GatewayRefundRequest.class));
    }

    @Test
    void timeoutMarkerMustSurviveAnUnresolvedReconciliation() {
        refund.setLastError("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenThrow(new PaymentGatewayException("Gateway lookup unavailable"));
        worker.processRefund(refund.getId());
        assertThat(refund.getLastError()).contains("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
    }

    @Test
    void captureMustRejectWrongAmountOrCurrency() {
        prepareCapture();
        when(gateway.captureOrder(any(), any(), any())).thenReturn(new PaymentCaptureResult(true, "CAPTURE-TINY", new BigDecimal("0.01"), "EUR", "COMPLETED", "completed"));
        assertThatThrownBy(() -> service.capturePayPalOrder(order.getCustomerId(), payment.getId(), "ORDER-REGISTERED", "capture-stable-key"))
                .as("500000 VND order must not be paid by 0.01 EUR").isInstanceOf(PaymentGatewayException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(confirmation);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCaptureResults")
    void captureRejectsEachInvalidResultIndependently(String scenario, PaymentCaptureResult result) {
        prepareCapture();
        when(gateway.captureOrder(any(), any(), any())).thenReturn(result);

        assertThatThrownBy(() -> service.capturePayPalOrder(order.getCustomerId(), payment.getId(),
                "ORDER-REGISTERED", "capture-stable-key"))
                .isInstanceOf(PaymentGatewayException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.PENDING_PAYMENT);
        assertThat(payment.getProviderTransactionId()).isEqualTo("CAPTURE-ORIGINAL");
        assertThat(payment.getLastError()).contains("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        verifyNoInteractions(confirmation);
        verify(subs, never()).saveAll(any());
    }

    private static Stream<Arguments> invalidCaptureResults() {
        BigDecimal validAmount = new BigDecimal("19.31");
        return Stream.of(
                Arguments.of("wrong amount only", new PaymentCaptureResult(true, "CAPTURE-NEW",
                        new BigDecimal("0.01"), "USD", "COMPLETED", null)),
                Arguments.of("wrong currency only", new PaymentCaptureResult(true, "CAPTURE-NEW",
                        validAmount, "EUR", "COMPLETED", null)),
                Arguments.of("missing capture ID", new PaymentCaptureResult(true, null,
                        validAmount, "USD", "COMPLETED", null)),
                Arguments.of("blank capture ID", new PaymentCaptureResult(true, " ",
                        validAmount, "USD", "COMPLETED", null)),
                Arguments.of("missing amount", new PaymentCaptureResult(true, "CAPTURE-NEW",
                        null, "USD", "COMPLETED", null)),
                Arguments.of("pending provider status", new PaymentCaptureResult(true, "CAPTURE-NEW",
                        validAmount, "USD", "PENDING", null)),
                Arguments.of("unsuccessful completed result", new PaymentCaptureResult(false, "CAPTURE-NEW",
                        validAmount, "USD", "COMPLETED", null)),
                Arguments.of("missing provider result", null));
    }

    @Test
    void captureRejectsADifferentNonNullPersistedProviderOrderBeforeGatewayAccess() {
        prepareCapture();

        assertThatThrownBy(() -> service.capturePayPalOrder(order.getCustomerId(), payment.getId(),
                "ANOTHER-ORDER", "capture-stable-key"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("does not match");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(gateway, confirmation);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentProvider.class, names = {"VNPAY", "MOMO", "SEPAY"})
    void captureRejectsANonPayPalPaymentBeforeGatewayAccess(PaymentProvider provider) {
        prepareCapture();
        payment.setProvider(provider);

        assertThatThrownBy(() -> service.capturePayPalOrder(order.getCustomerId(), payment.getId(),
                "ORDER-REGISTERED", "capture-stable-key"))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(gateway, confirmation);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "REFUNDED"})
    void captureCannotResurrectATerminalPaymentWithOtherwiseValidBinding(PaymentStatus status) {
        prepareCapture();
        payment.setStatus(status);

        assertThatThrownBy(() -> service.capturePayPalOrder(order.getCustomerId(), payment.getId(),
                "ORDER-REGISTERED", "capture-stable-key"))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(payment.getStatus()).isEqualTo(status);
        verifyNoInteractions(gateway, confirmation);
    }

    @Test
    void captureMustRejectUnboundProviderOrderId() {
        prepareCapture(); payment.setProviderOrderId(null);
        when(gateway.captureOrder(any(), any(), any())).thenReturn(validCapture());
        assertThatThrownBy(() -> service.capturePayPalOrder(order.getCustomerId(), payment.getId(), "UNRELATED-ORDER", "capture-stable-key"))
                .isInstanceOf(InvalidOrderStateException.class);
        verify(gateway, never()).captureOrder(any(), any(), any());
    }

    @Test
    void completedWebhookMustNotResurrectFailedPayment() {
        payment.setStatus(PaymentStatus.FAILED); order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        when(gateway.verifyWebhookSignature(any(), any())).thenReturn(true);
        String payload = "{\"id\":\"WH-FAILED-PAYMENT\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":{\"id\":\"CAPTURE-NEW\",\"status\":\"COMPLETED\",\"custom_id\":\""+payment.getId()+"\",\"amount\":{\"value\":\"0.01\",\"currency_code\":\"EUR\"},\"supplementary_data\":{\"related_ids\":{\"order_id\":\"UNRELATED-ORDER\"}}}}";
        assertThatThrownBy(() -> service.processPayPalWebhook(payload,"trans-id","timestamp","signature","https://api.paypal.com/cert","SHA256withRSA"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void captureAfterTimeoutMustReconcileBeforeAnotherCaptureRequest() {
        prepareCapture();
        when(gateway.captureOrder(any(), any(), any())).thenThrow(new PaymentGatewayException("GATEWAY_TIMEOUT_AWAITING_VERIFICATION"));
        assertThatThrownBy(() -> service.capturePayPalOrder(order.getCustomerId(), payment.getId(), "ORDER-REGISTERED", "capture-first-key"))
                .isInstanceOf(PaymentGatewayException.class);
        var pending = service.capturePayPalOrder(order.getCustomerId(), payment.getId(), "ORDER-REGISTERED", "capture-second-key");
        assertThat(pending.status()).isEqualTo(PaymentStatus.PENDING);
        verify(gateway).queryCapture(PaymentProvider.PAYPAL, "ORDER-REGISTERED");
        assertThat(payment.getLastError()).contains("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        verify(gateway, times(1)).captureOrder(any(), any(), any());
    }

    @Test
    void approvedWebhookRemainsPendingWithoutCapture() {
        when(gateway.verifyWebhookSignature(any(), any())).thenReturn(true);
        var response = service.processPayPalWebhook("{\"id\":\"WH-APPROVED\",\"event_type\":\"CHECKOUT.ORDER.APPROVED\"}", "trans-id", "timestamp", "signature", "https://api.paypal.com/cert", "SHA256withRSA");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        verify(gateway, never()).captureOrder(any(), any(), any());
    }

    @Test
    void completedPaymentReplayDoesNotCaptureOrConfirmAgain() {
        var response = service.capturePayPalOrder(order.getCustomerId(), payment.getId(), "ORDER-REGISTERED", "capture-stable-key");
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(gateway, never()).captureOrder(any(), any(), any());
        verifyNoInteractions(confirmation);
    }

    @Test
    void reconciledCaptureConfirmsWithoutResendingTheCapture() {
        prepareCapture();
        payment.setCaptureRequestId("persisted-operation");
        payment.setLastError("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        when(gateway.queryCapture(PaymentProvider.PAYPAL, "ORDER-REGISTERED")).thenReturn(validCapture());
        var result = service.capturePayPalOrder(order.getCustomerId(), payment.getId(), "ORDER-REGISTERED", "another-client-key");
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(gateway, never()).captureOrder(any(), any(), any());
        verify(confirmation).execute(any());
    }

    @Test
    void completedWebhookRejectsTerminalPaymentEvenWithCorrectMoneyAndOrder() {
        payment.setStatus(PaymentStatus.FAILED); order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        when(gateway.verifyWebhookSignature(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> webhook("19.31", "USD", "ORDER-REGISTERED"))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verifyNoInteractions(confirmation);
    }

    @Test
    void completedWebhookRejectsIncorrectMoneyOnPendingPayment() {
        prepareCapture();
        when(gateway.verifyWebhookSignature(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> webhook("0.01", "USD", "ORDER-REGISTERED")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> webhook("19.31", "EUR", "ORDER-REGISTERED")).isInstanceOf(RuntimeException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(confirmation);
    }

    @Test
    void completedWebhookRejectsIncorrectOrderAndProvider() {
        prepareCapture();
        when(gateway.verifyWebhookSignature(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> webhook("19.31", "USD", "ANOTHER-ORDER")).isInstanceOf(RuntimeException.class);
        payment.setProvider(PaymentProvider.VNPAY);
        assertThatThrownBy(() -> webhook("19.31", "USD", "ORDER-REGISTERED")).isInstanceOf(RuntimeException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(confirmation);
    }

    @Test
    void paypalRefundCallbackRequiresEveryOfficialHeaderAndHasNoHmacFallback() {
        assertThatThrownBy(() -> service.processPayPalRefundWebhook("{}", null, null, "valid-paypal-sig", null, null))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(gateway);
    }

    private void webhook(String value, String currency, String providerOrder) {
        String payload = "{\"id\":\"WH-VALIDATED\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":{"
                + "\"id\":\"CAPTURE-NEW\",\"status\":\"COMPLETED\",\"custom_id\":\"" + payment.getId()
                + "\",\"amount\":{\"value\":\"" + value + "\",\"currency_code\":\"" + currency
                + "\"},\"supplementary_data\":{\"related_ids\":{\"order_id\":\"" + providerOrder + "\"}}}}";
        service.processPayPalWebhook(payload, "transmission", "timestamp", "signature", "https://api.paypal.com/cert", "SHA256withRSA");
    }

    private void prepareCapture() {
        payment.setStatus(PaymentStatus.PENDING); order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
    }
    private PaymentCaptureResult validCapture() {
        return new PaymentCaptureResult(true,"CAPTURE-NEW",new BigDecimal("19.31"),"USD","COMPLETED","completed");
    }
    private PayPalPaymentAdapter paypal(RestClient.Builder builder) {
        return new PayPalPaymentAdapter(builder,new PayPalProperties("sandbox","audit-id","audit-secret","WH-CONFIGURED",PAYPAL,null,null),new ObjectMapper(),mock(VNPayPaymentAdapter.class));
    }
}

package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.usecases.CreatePaymentIntentUseCase;
import com.danasea.backend.modules.order.domain.exceptions.InvalidWebhookException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.dtos.RefundWebhookResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.fasterxml.jackson.databind.ObjectMapper;

class PayPalRefundWebhookDispatchTest {

    private static final String EVENT_ID = "WH-REFUND-EVENT";
    private static final String PROVIDER_REFUND_ID = "PAYPAL-REFUND-ORIGINAL";
    private static final String OPERATION_ID = "persisted-refund-operation";
    private static final UUID REFUND_ID = UUID.fromString("ee7aeb96-bab0-4740-823f-f8aaea539f54");
    private static final UUID PAYMENT_ID = UUID.fromString("313e64a7-e41e-43c9-9385-625cf557ed30");
    private static final String CERT_URL = "https://api.paypal.com/cert.pem";

    private PaymentGatewayPort gateway;
    private RefundProcessingService processor;
    private JpaRefundRepository refunds;
    private JpaPaymentRepository payments;
    private PaymentWebhookSigner internalSigner;
    private ConfirmBookingUseCase confirmation;
    private OrderPaymentService service;

    @BeforeEach
    void setUp() {
        gateway = mock(PaymentGatewayPort.class);
        processor = mock(RefundProcessingService.class);
        refunds = mock(JpaRefundRepository.class);
        payments = mock(JpaPaymentRepository.class);
        internalSigner = mock(PaymentWebhookSigner.class);
        confirmation = mock(ConfirmBookingUseCase.class);
        service = serviceWith(processor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAYMENT.CAPTURE.REFUNDED", "PAYMENT.REFUND.COMPLETED", "PAYMENT.REFUND.FAILED"})
    void verifiedRefundEventsDelegateExactIdentityAndKeepTheProcessorsAuthoritativeStatus(String eventType) {
        String payload = refundEvent(eventType);
        var expected = new RefundWebhookResponse(EVENT_ID, REFUND_ID, RefundStatus.PENDING, false);
        when(gateway.verifyWebhookSignature(verificationHeaders(payload), "official-signature")).thenReturn(true);
        when(processor.processPayPalNotification(EVENT_ID, PROVIDER_REFUND_ID, OPERATION_ID)).thenReturn(expected);

        var response = service.processPayPalRefundWebhook(payload, "transmission-id", "transmission-time",
                "official-signature", CERT_URL, "SHA256withRSA");

        assertThat(response).isSameAs(expected);
        assertThat(response.status()).isEqualTo(RefundStatus.PENDING);
        verify(gateway).verifyWebhookSignature(verificationHeaders(payload), "official-signature");
        verify(processor).processPayPalNotification(EVENT_ID, PROVIDER_REFUND_ID, OPERATION_ID);
        verifyNoMoreInteractions(gateway, processor);
        verifyNoInteractions(refunds, payments, internalSigner, confirmation);
    }

    @Test
    void theSharedPayPalEndpointRoutesRefundsThroughTheSameProcessorWithoutCapturingAgain() {
        String payload = refundEvent("PAYMENT.CAPTURE.REFUNDED");
        when(gateway.verifyWebhookSignature(verificationHeaders(payload), "official-signature")).thenReturn(true);
        when(processor.processPayPalNotification(EVENT_ID, PROVIDER_REFUND_ID, OPERATION_ID))
                .thenReturn(new RefundWebhookResponse(EVENT_ID, REFUND_ID, RefundStatus.PROCESSED, true));
        var refund = new RefundJpaEntity();
        refund.setId(REFUND_ID);
        refund.setPaymentId(PAYMENT_ID);
        var payment = new PaymentJpaEntity();
        payment.setId(PAYMENT_ID);
        payment.setProvider(PaymentProvider.PAYPAL);
        payment.setStatus(PaymentStatus.REFUNDED);
        when(refunds.findById(REFUND_ID)).thenReturn(Optional.of(refund));
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        var response = service.processPayPalWebhook(payload, "transmission-id", "transmission-time",
                "official-signature", CERT_URL, "SHA256withRSA");

        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(response.alreadyProcessed()).isTrue();
        verify(gateway).verifyWebhookSignature(verificationHeaders(payload), "official-signature");
        verify(processor).processPayPalNotification(EVENT_ID, PROVIDER_REFUND_ID, OPERATION_ID);
        verifyNoMoreInteractions(gateway, processor);
        verifyNoInteractions(internalSigner, confirmation);
    }

    @Test
    void rejectedOfficialVerificationCannotReachTheRefundProcessorOrInternalHmacFallback() {
        String payload = refundEvent("PAYMENT.CAPTURE.REFUNDED");
        when(gateway.verifyWebhookSignature(verificationHeaders(payload), "official-signature")).thenReturn(false);

        assertThatThrownBy(() -> service.processPayPalRefundWebhook(payload, "transmission-id", "transmission-time",
                "official-signature", CERT_URL, "SHA256withRSA"))
                .isInstanceOf(InvalidWebhookException.class).hasMessageContaining("signature");

        verifyNoInteractions(processor, refunds, payments, internalSigner, confirmation);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void eachOfficialHeaderIsRequiredIndependentlyBeforeVerificationOrRefundDispatch(int missingHeader) {
        String[] headers = {"transmission-id", "transmission-time", "official-signature", CERT_URL, "SHA256withRSA"};
        headers[missingHeader] = null;

        assertThatThrownBy(() -> service.processPayPalRefundWebhook(refundEvent("PAYMENT.CAPTURE.REFUNDED"),
                headers[0], headers[1], headers[2], headers[3], headers[4]))
                .isInstanceOf(InvalidWebhookException.class).hasMessageContaining("headers are required");

        verifyNoInteractions(gateway, processor, refunds, payments, internalSigner, confirmation);
    }

    @Test
    void aVerifiedPaymentCaptureEventCannotBeAppliedAsARefund() {
        String payload = refundEvent("PAYMENT.CAPTURE.COMPLETED");
        when(gateway.verifyWebhookSignature(verificationHeaders(payload), "official-signature")).thenReturn(true);

        assertThatThrownBy(() -> service.processPayPalRefundWebhook(payload, "transmission-id", "transmission-time",
                "official-signature", CERT_URL, "SHA256withRSA"))
                .isInstanceOf(InvalidWebhookException.class).hasMessageContaining("refund event is required");

        verifyNoInteractions(processor, refunds, payments, internalSigner, confirmation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "{\"id\":\"WH-REFUND-EVENT\"}",
            "{\"event_type\":\"PAYMENT.CAPTURE.REFUNDED\"}"})
    void verifiedButMalformedOrUnidentifiedEventsCannotReachRefundProcessing(String payload) {
        when(gateway.verifyWebhookSignature(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.processPayPalRefundWebhook(payload, "transmission-id", "transmission-time",
                "official-signature", CERT_URL, "SHA256withRSA"))
                .isInstanceOf(InvalidWebhookException.class);

        verifyNoInteractions(processor, refunds, payments, internalSigner, confirmation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"provider", "refund-id", "operation-id"})
    void verifiedRefundRoutingStillRejectsEachPersistedIdentityMismatchInTheRealProcessor(String mismatch) {
        var candidate = new RefundJpaEntity();
        candidate.setId(REFUND_ID);
        candidate.setStatus(RefundStatus.PENDING);
        candidate.setProvider("provider".equals(mismatch) ? PaymentProvider.VNPAY : PaymentProvider.PAYPAL);
        candidate.setProviderRefundId(PROVIDER_REFUND_ID);
        candidate.setGatewayRequestId(OPERATION_ID);
        String resourceId = "refund-id".equals(mismatch) ? "UNRELATED-PROVIDER-REFUND" : PROVIDER_REFUND_ID;
        String operationId = "operation-id".equals(mismatch) ? "unrelated-refund-operation" : OPERATION_ID;
        String payload = refundEvent("PAYMENT.CAPTURE.REFUNDED")
                .replace(PROVIDER_REFUND_ID, resourceId).replace(OPERATION_ID, operationId);
        when(gateway.verifyWebhookSignature(verificationHeaders(payload), "official-signature")).thenReturn(true);
        if ("operation-id".equals(mismatch)) {
            when(refunds.findByProviderAndProviderRefundId(PaymentProvider.PAYPAL, PROVIDER_REFUND_ID))
                    .thenReturn(Optional.of(candidate));
        } else {
            when(refunds.findByGatewayRequestId(OPERATION_ID)).thenReturn(Optional.of(candidate));
        }
        when(refunds.findByIdForUpdate(REFUND_ID)).thenReturn(Optional.of(candidate));
        var realProcessor = new RefundProcessingService(refunds, mock(JpaSubOrderRepository.class),
                mock(JpaMasterOrderRepository.class), payments, mock(JpaBookingRepository.class),
                mock(JpaServiceSlotRepository.class), gateway);
        service = serviceWith(realProcessor);

        assertThatThrownBy(() -> service.processPayPalRefundWebhook(payload, "transmission-id", "transmission-time",
                "official-signature", CERT_URL, "SHA256withRSA"))
                .isInstanceOf(InvalidWebhookException.class).hasMessageContaining("does not match");

        assertThat(candidate.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(candidate.getProviderRefundId()).isEqualTo(PROVIDER_REFUND_ID);
        assertThat(candidate.getGatewayRequestId()).isEqualTo(OPERATION_ID);
        assertThat(candidate.getWebhookEventId()).isNull();
        verify(gateway).verifyWebhookSignature(verificationHeaders(payload), "official-signature");
        verifyNoMoreInteractions(gateway);
        verifyNoInteractions(payments, internalSigner, confirmation);
    }

    private OrderPaymentService serviceWith(RefundProcessingService refundProcessor) {
        return new OrderPaymentService(mock(JpaBookingRepository.class), mock(JpaMasterOrderRepository.class),
                mock(JpaSubOrderRepository.class), payments, refunds, mock(JpaServiceSlotRepository.class),
                mock(VendorInternalApi.class), new RefundPolicyEngine(), internalSigner, confirmation,
                new ObjectMapper(), mock(CreatePaymentIntentUseCase.class), refundProcessor, gateway);
    }

    private static String refundEvent(String eventType) {
        return "{\"id\":\"WH-REFUND-EVENT\",\"event_type\":\"" + eventType + "\",\"resource\":{"
                + "\"id\":\"PAYPAL-REFUND-ORIGINAL\",\"invoice_id\":\"persisted-refund-operation\"}}";
    }

    private static Map<String, String> verificationHeaders(String payload) {
        return Map.of("paypal-transmission-id", "transmission-id", "paypal-transmission-time", "transmission-time",
                "paypal-cert-url", CERT_URL, "paypal-auth-algo", "SHA256withRSA", "rawPayload", payload);
    }
}

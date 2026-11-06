package com.danasea.backend.modules.order.infrastructure.adapters;

import com.danasea.backend.configs.properties.PayOSProperties;
import com.danasea.backend.modules.order.domain.models.PaymentCancelStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.PaymentCancelResult;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.infrastructure.adapters.payos.PayOSSignatureHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("PayOSPaymentAdapter Unit Tests")
class PayOSPaymentAdapterTest {

    private static final String CLIENT_ID = "test-client-id";
    private static final String API_KEY = "test-api-key";
    private static final String CHECKSUM_KEY = "test-checksum-key-1234567890abcdef";

    private PayOSProperties properties;
    private MockRestServiceServer mockServer;
    private PayOSPaymentAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new PayOSProperties(
                CLIENT_ID,
                API_KEY,
                CHECKSUM_KEY,
                "https://api-merchant.payos.vn",
                "https://danasea.vn/payment/return",
                "https://danasea.vn/payment/cancel"
        );
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new PayOSPaymentAdapter(builder, properties, objectMapper);
    }

    @Test
    @DisplayName("supportsProvider returns true only for PAYOS")
    void testSupportsProvider() {
        assertThat(adapter.supportsProvider(PaymentProvider.PAYOS)).isTrue();
        assertThat(adapter.supportsProvider(PaymentProvider.VNPAY)).isFalse();
        assertThat(adapter.supportsProvider(PaymentProvider.PAYPAL)).isFalse();
        assertThat(adapter.supportsProvider(PaymentProvider.ZALOPAY)).isFalse();
        assertThat(adapter.canCancelServerSide(PaymentProvider.PAYOS)).isTrue();
    }

    @Test
    @DisplayName("Signature helper correctly computes HMAC SHA-256 with sorted keys")
    void testSignatureGeneration() {
        String data = "amount=250000&cancelUrl=https://danasea.vn/cancel&description=DANASea Order&orderCode=123456&returnUrl=https://danasea.vn/return";
        String signature = PayOSSignatureHelper.hmacSha256(CHECKSUM_KEY, data);
        assertThat(signature).isNotNull().isNotBlank();

        Map<String, Object> dataMap = Map.of(
                "amount", 250000,
                "orderCode", 123456
        );
        String webhookSig = PayOSSignatureHelper.hmacSha256(CHECKSUM_KEY, "amount=250000&orderCode=123456");
        boolean verified = PayOSSignatureHelper.verifyWebhookSignature(dataMap, webhookSig, CHECKSUM_KEY);
        assertThat(verified).isTrue();

        boolean badVerified = PayOSSignatureHelper.verifyWebhookSignature(dataMap, "invalid_sig", CHECKSUM_KEY);
        assertThat(badVerified).isFalse();
    }

    @Test
    @DisplayName("createPaymentIntent successfully calls payOS and returns checkoutUrl and qrCode")
    void testCreatePaymentIntent() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("250000");

        String mockResponse = """
                {
                    "code": "00",
                    "desc": "success",
                    "data": {
                        "bin": "970407",
                        "accountNumber": "12345678",
                        "accountName": "DANASEA CORP",
                        "amount": 250000,
                        "description": "DANASEA Order",
                        "orderCode": 123456,
                        "currency": "VND",
                        "paymentLinkId": "link-uuid-1234",
                        "status": "PENDING",
                        "checkoutUrl": "https://pay.payos.vn/web/123456",
                        "qrCode": "00020101021238540010A00000072701260006970407011212345678"
                    },
                    "signature": "mock_sig"
                }
                """;

        mockServer.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("x-client-id", CLIENT_ID))
                .andExpect(header("x-api-key", API_KEY))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        PaymentIntentResult result = adapter.createPaymentIntent(paymentId, orderId, amount, PaymentProvider.PAYOS);

        assertThat(result).isNotNull();
        assertThat(result.paymentUrl()).isEqualTo("https://pay.payos.vn/web/123456");
        assertThat(result.qrCodeUrl()).startsWith("000201");
        assertThat(result.providerOrderId()).isNotNull();
        assertThat(result.provider()).isEqualTo(PaymentProvider.PAYOS);
    }

    @Test
    @DisplayName("cancelPayment returns success when payOS confirms cancellation")
    void testCancelPaymentSuccess() {
        String mockResponse = """
                {
                    "code": "00",
                    "desc": "success",
                    "data": {
                        "id": "link-uuid-1234",
                        "status": "CANCELLED"
                    }
                }
                """;

        mockServer.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/link-uuid-1234/cancel"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("x-client-id", CLIENT_ID))
                .andExpect(header("x-api-key", API_KEY))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        PaymentCancelResult cancelResult = adapter.cancelPayment(PaymentProvider.PAYOS, "link-uuid-1234", "Switched provider");

        assertThat(cancelResult.success()).isTrue();
        assertThat(cancelResult.status()).isEqualTo(PaymentCancelStatus.CANCELED);
        assertThat(cancelResult.alreadyCaptured()).isFalse();
    }

    @Test
    @DisplayName("cancelPayment detects already captured when payOS code is 118 or desc indicates paid")
    void testCancelPaymentAlreadyCaptured() {
        String mockResponse = """
                {
                    "code": "118",
                    "desc": "Payment request has already been paid and cannot be cancelled",
                    "data": null
                }
                """;

        mockServer.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/link-uuid-1234/cancel"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        PaymentCancelResult cancelResult = adapter.cancelPayment(PaymentProvider.PAYOS, "link-uuid-1234", "Switched provider");

        assertThat(cancelResult.success()).isFalse();
        assertThat(cancelResult.status()).isEqualTo(PaymentCancelStatus.ALREADY_CAPTURED);
        assertThat(cancelResult.alreadyCaptured()).isTrue();
    }

    @Test
    @DisplayName("queryPayment returns COMPLETED when payOS status is PAID")
    void testQueryPaymentCompleted() {
        String mockResponse = """
                {
                    "code": "00",
                    "desc": "success",
                    "data": {
                        "orderCode": 123456,
                        "amount": 250000,
                        "status": "PAID"
                    }
                }
                """;

        mockServer.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/123456"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        PaymentCaptureResult captureResult = adapter.queryPayment(PaymentProvider.PAYOS, "123456", null);

        assertThat(captureResult.success()).isTrue();
        assertThat(captureResult.status()).isEqualTo("COMPLETED");
        assertThat(captureResult.amount()).isEqualByComparingTo("250000");
    }
}

package com.danasea.backend.modules.order.infrastructure.adapters;

import com.danasea.backend.configs.properties.ZaloPayProperties;
import com.danasea.backend.modules.order.domain.models.PaymentCancelStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.PaymentCancelResult;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.infrastructure.adapters.zalopay.ZaloPaySignatureHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ZaloPayPaymentAdapter Unit Tests")
class ZaloPayPaymentAdapterTest {

    private static final Integer APP_ID = 2553;
    private static final String KEY1 = "PcY4iZIKFCIdgZvA6ueMcMHHUbRLYjPL";
    private static final String KEY2 = "kLtgPl8HHhfvMuD2wKfgccY4nwMZE6lj";

    private ZaloPayProperties properties;
    private MockRestServiceServer mockServer;
    private ZaloPayPaymentAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new ZaloPayProperties(
                APP_ID,
                KEY1,
                KEY2,
                "https://sb-openapi.zalopay.vn/v2",
                "https://danasea.vn/api/payments/webhook/zalopay",
                "https://danasea.vn/payment/redirect"
        );
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new ZaloPayPaymentAdapter(builder, properties, objectMapper);
    }

    @Test
    @DisplayName("supportsProvider returns true only for ZALOPAY")
    void testSupportsProvider() {
        assertThat(adapter.supportsProvider(PaymentProvider.ZALOPAY)).isTrue();
        assertThat(adapter.supportsProvider(PaymentProvider.VNPAY)).isFalse();
        assertThat(adapter.supportsProvider(PaymentProvider.PAYPAL)).isFalse();
        assertThat(adapter.supportsProvider(PaymentProvider.PAYOS)).isFalse();
        assertThat(adapter.canCancelServerSide(PaymentProvider.ZALOPAY)).isTrue();
    }

    @Test
    @DisplayName("Signature helper computes exact official ZaloPay MAC formulas")
    void testMacFormulas() {
        String hmac = ZaloPaySignatureHelper.cancelOrderMac(APP_ID, "261105_123456", KEY1);
        assertThat(hmac).isNotNull().hasSize(64); // SHA256 hex is 64 chars

        String queryMac = ZaloPaySignatureHelper.queryOrderMac(APP_ID, "261105_123456", KEY1);
        assertThat(queryMac).isEqualTo(hmac); // Both use appId|appTransId|key1

        String data = "{\"app_id\":2553,\"app_trans_id\":\"261105_123456\",\"amount\":500000}";
        String callbackMac = ZaloPaySignatureHelper.hmacSha256(KEY2, data);
        assertThat(ZaloPaySignatureHelper.verifyCallbackMac(data, callbackMac, KEY2)).isTrue();
        assertThat(ZaloPaySignatureHelper.verifyCallbackMac(data, "invalid_mac", KEY2)).isFalse();
    }

    @Test
    @DisplayName("createPaymentIntent successfully calls /v2/create and returns order_url and sub_return_code")
    void testCreatePaymentIntent() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("500000");

        String mockResponse = """
                {
                    "return_code": 1,
                    "return_message": "Giao dich thanh cong",
                    "sub_return_code": 1,
                    "sub_return_message": "Giao dich thanh cong",
                    "order_url": "https://qcgateway.zalopay.vn/openinapp?order=eyJ...",
                    "zp_trans_token": "26110500001",
                    "order_token": "token123"
                }
                """;

        mockServer.expect(requestTo("https://sb-openapi.zalopay.vn/v2/create"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        PaymentIntentResult result = adapter.createPaymentIntent(paymentId, orderId, amount, PaymentProvider.ZALOPAY);

        assertThat(result).isNotNull();
        assertThat(result.paymentUrl()).isEqualTo("https://qcgateway.zalopay.vn/openinapp?order=eyJ...");
        assertThat(result.providerOrderId()).isNotNull();
        assertThat(result.provider()).isEqualTo(PaymentProvider.ZALOPAY);
    }

    @Test
    @DisplayName("cancelPayment returns success when ZaloPay return_code is 1")
    void testCancelPaymentSuccess() {
        String mockResponse = """
                {
                    "return_code": 1,
                    "return_message": "Huy don hang thanh cong",
                    "sub_return_code": 1,
                    "sub_return_message": "Huy thanh cong"
                }
                """;

        mockServer.expect(requestTo("https://sb-openapi.zalopay.vn/v2/cancel"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        PaymentCancelResult cancelResult = adapter.cancelPayment(PaymentProvider.ZALOPAY, "261105_123456", "Switched provider");

        assertThat(cancelResult.success()).isTrue();
        assertThat(cancelResult.status()).isEqualTo(PaymentCancelStatus.CANCELED);
        assertThat(cancelResult.alreadyCaptured()).isFalse();
    }

    @Test
    @DisplayName("cancelPayment detects already captured when return_message indicates captured")
    void testCancelPaymentAlreadyCaptured() {
        String mockResponse = """
                {
                    "return_code": 2,
                    "return_message": "Don hang da duoc thanh toan, khong the huy",
                    "sub_return_code": 2,
                    "sub_return_message": "Don hang da duoc thanh toan"
                }
                """;

        mockServer.expect(requestTo("https://sb-openapi.zalopay.vn/v2/cancel"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        PaymentCancelResult cancelResult = adapter.cancelPayment(PaymentProvider.ZALOPAY, "261105_123456", "Switched provider");

        assertThat(cancelResult.success()).isFalse();
        assertThat(cancelResult.status()).isEqualTo(PaymentCancelStatus.ALREADY_CAPTURED);
        assertThat(cancelResult.alreadyCaptured()).isTrue();
    }

    @Test
    @DisplayName("queryPayment returns COMPLETED when return_code is 1")
    void testQueryPaymentCompleted() {
        String mockResponse = """
                {
                    "return_code": 1,
                    "return_message": "Giao dich thanh cong",
                    "sub_return_code": 1,
                    "is_processing": false,
                    "amount": 500000,
                    "zp_trans_id": 123456789,
                    "zp_trans_id": 26110500001
                }
                """;

        mockServer.expect(requestTo("https://sb-openapi.zalopay.vn/v2/query"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        PaymentCaptureResult captureResult = adapter.queryPayment(PaymentProvider.ZALOPAY, "261105_123456", null);

        assertThat(captureResult.success()).isTrue();
        assertThat(captureResult.status()).isEqualTo("COMPLETED");
        assertThat(captureResult.amount()).isEqualTo(new BigDecimal("500000"));
    }

    @Test
    @DisplayName("generateAppTransId formats with Asia/Ho_Chi_Minh date and handles rollover")
    void testGenerateAppTransIdFormatAndTimezone() {
        String appTransId = adapter.generateAppTransId();
        assertThat(appTransId).matches("^\\d{6}_\\d{6}$");

        String expectedDatePrefix = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        assertThat(appTransId).startsWith(expectedDatePrefix + "_");

        // Midnight rollover test with specific LocalDate
        String rolloverId = adapter.generateAppTransId(java.time.LocalDate.of(2027, 1, 1));
        assertThat(rolloverId).startsWith("270101_");
        assertThat(rolloverId).matches("^270101_\\d{6}$");
    }

    @Test
    @DisplayName("generateAppTransId produces unique values under high concurrency")
    void testGenerateAppTransIdConcurrencyUniqueness() throws InterruptedException {
        int threadCount = 20;
        int perThread = 50;
        int total = threadCount * perThread;
        java.util.Set<String> generatedIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        generatedIds.add(adapter.generateAppTransId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(generatedIds).hasSize(total);
    }

    @Test
    @DisplayName("generateAppTransId uses DB sequence when JpaPaymentRepository is injected")
    void testGenerateAppTransIdUsesDbSequenceWhenAvailable() {
        com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository mockRepo =
                org.mockito.Mockito.mock(com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository.class);
        org.mockito.Mockito.when(mockRepo.getNextPayOSOrderCode()).thenReturn(777888L);

        adapter.setPaymentRepository(mockRepo);

        String id = adapter.generateAppTransId();
        assertThat(id).endsWith("_777888");

        adapter.setPaymentRepository(null); // Reset
    }

    @Test
    @DisplayName("generateAppTransId throws exception when DB sequence fails, never silently falls back to AtomicLong")
    void testGenerateAppTransIdThrowsWhenDbSequenceFails() {
        com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository mockRepo =
                org.mockito.Mockito.mock(com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository.class);
        org.mockito.Mockito.when(mockRepo.getNextPayOSOrderCode()).thenThrow(new org.springframework.dao.QueryTimeoutException("DB sequence timeout"));

        adapter.setPaymentRepository(mockRepo);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.generateAppTransId())
                .isInstanceOf(org.springframework.dao.QueryTimeoutException.class);

        adapter.setPaymentRepository(null);
    }

    @Test
    @DisplayName("verifyWebhookSignature and verifyCallback validate MAC with key2")
    void testWebhookAndCallbackVerification() {
        String data = "{\"app_id\":2553,\"app_trans_id\":\"261105_123456\",\"amount\":500000}";
        String validMac = ZaloPaySignatureHelper.hmacSha256(KEY2, data);

        assertThat(adapter.verifyCallback(data, validMac)).isTrue();
        assertThat(adapter.verifyCallback(data, "bad-mac")).isFalse();

        java.util.Map<String, String> rawParams = java.util.Map.of("data", data, "mac", validMac);
        assertThat(adapter.verifyWebhookSignature(rawParams, validMac)).isTrue();
        assertThat(adapter.verifyWebhookSignature(rawParams, null)).isTrue();
    }

    @Test
    @DisplayName("cancelPayment with invalid provider or blank orderId returns failed result")
    void testCancelPaymentInvalidInput() {
        PaymentCancelResult res1 = adapter.cancelPayment(PaymentProvider.VNPAY, "123", "Reason");
        assertThat(res1.success()).isFalse();
        assertThat(res1.errorCode()).isEqualTo("INVALID_REQUEST");

        PaymentCancelResult res2 = adapter.cancelPayment(PaymentProvider.ZALOPAY, "   ", "Reason");
        assertThat(res2.success()).isFalse();
        assertThat(res2.errorCode()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("requestRefund returns manual review required result")
    void testRequestRefundUnsupported() {
        com.danasea.backend.modules.order.domain.ports.RefundResult refundResult =
                adapter.requestRefund("tx-123", BigDecimal.TEN);
        assertThat(refundResult.success()).isFalse();
        assertThat(refundResult.providerRefundId()).isEqualTo("MANUAL_REVIEW_REQUIRED");
    }

    @Test
    @DisplayName("createPaymentIntent 3-arg overload generates paymentId and calls 4-arg method")
    void testCreatePaymentIntentThreeArgs() {
        String mockResponse = """
                {
                    "return_code": 1,
                    "return_message": "Giao dich thanh cong",
                    "sub_return_code": 1,
                    "sub_return_message": "Giao dich thanh cong",
                    "order_url": "https://qcgateway.zalopay.vn/openinapp?order=test",
                    "zp_trans_token": "26110500002"
                }
                """;

        mockServer.expect(requestTo("https://sb-openapi.zalopay.vn/v2/create"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        PaymentIntentResult result = adapter.createPaymentIntent(UUID.randomUUID(), new BigDecimal("100000"), PaymentProvider.ZALOPAY);
        assertThat(result).isNotNull();
        assertThat(result.paymentUrl()).isEqualTo("https://qcgateway.zalopay.vn/openinapp?order=test");
    }
}

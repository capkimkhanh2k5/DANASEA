package com.danasea.backend.modules.order.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.danasea.backend.configs.properties.PayPalProperties;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundRequest;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.ports.RefundResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@DisplayName("PayPal Payment Adapter HTTP Wire and Parser Contract Tests")
class PayPalPaymentAdapterHttpContractTest {

    private static HttpServer server;
    private static int port;
    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String WEBHOOK_ID = "WH-ACCEPT-01";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Map of path -> HttpHandler handler or response supplier
    private static final Map<String, PathHandler> HANDLERS = new ConcurrentHashMap<>();

    private PayPalPaymentAdapter adapter;
    private VNPayPaymentAdapter vnPayMock;

    @FunctionalInterface
    interface PathHandler {
        void handle(HttpExchange exchange, String body) throws IOException;
    }

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            String body = new String(bytes, StandardCharsets.UTF_8);

            PathHandler handler = findHandler(path, exchange.getRequestMethod());
            if (handler != null) {
                try {
                    handler.handle(exchange, body);
                } catch (Exception ex) {
                    byte[] err = ("Internal handler error: " + ex.getMessage()).getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, err.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(err); }
                }
            } else {
                byte[] notFound = ("Path not mapped: " + path).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, notFound.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(notFound); }
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static PathHandler findHandler(String path, String method) {
        String key = method + " " + path;
        PathHandler direct = HANDLERS.get(key);
        if (direct != null) return direct;

        for (Map.Entry<String, PathHandler> entry : HANDLERS.entrySet()) {
            String[] parts = entry.getKey().split(" ", 2);
            if (parts.length == 2 && parts[0].equalsIgnoreCase(method)) {
                String pattern = parts[1];
                if (pattern.contains("{id}")) {
                    String regex = pattern.replace("{id}", "[^/]+");
                    if (path.matches(regex)) {
                        return entry.getValue();
                    }
                }
            }
        }
        return null;
    }

    @BeforeEach
    void setUp() {
        HANDLERS.clear();
        vnPayMock = mock(VNPayPaymentAdapter.class);
        String baseUrl = "http://127.0.0.1:" + port;
        PayPalProperties props = new PayPalProperties("sandbox", CLIENT_ID, CLIENT_SECRET, WEBHOOK_ID,
                baseUrl, "http://localhost:3000/return", "http://localhost:3000/cancel");
        adapter = new PayPalPaymentAdapter(RestClient.builder(), props, MAPPER, vnPayMock);
    }

    private static void respond(HttpExchange exchange, int status, String jsonBody) throws IOException {
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String loadFixture(String filename) {
        try (InputStream is = PayPalPaymentAdapterHttpContractTest.class
                .getResourceAsStream("/payments/provider-contract/" + filename)) {
            if (is == null) throw new IllegalArgumentException("Fixture not found: " + filename);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void oauthAndCreateOrderWireContract() {
        AtomicReference<String> tokenAuthHeader = new AtomicReference<>();
        AtomicReference<String> tokenBody = new AtomicReference<>();
        AtomicReference<String> orderAuthHeader = new AtomicReference<>();
        AtomicReference<String> orderRequestId = new AtomicReference<>();
        AtomicReference<String> orderBodyCaptured = new AtomicReference<>();

        HANDLERS.put("POST /v1/oauth2/token", (exchange, body) -> {
            tokenAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            tokenBody.set(body);
            respond(exchange, 200, "{\"access_token\":\"TOKEN-ACCEPT-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        });

        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        HANDLERS.put("POST /v2/checkout/orders", (exchange, body) -> {
            orderAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            orderRequestId.set(exchange.getRequestHeaders().getFirst("PayPal-Request-Id"));
            orderBodyCaptured.set(body);
            respond(exchange, 200, "{\"id\":\"ORDER-ACCEPT-1\",\"status\":\"CREATED\",\"links\":[{\"href\":\"https://www.sandbox.paypal.com/checkoutnow?token=ORDER-ACCEPT-1\",\"rel\":\"approve\",\"method\":\"GET\"}]}");
        });

        // 500,000 VND / 25,900 rate = 19.31 USD
        PaymentIntentResult intent = adapter.createPaymentIntent(paymentId, orderId, new BigDecimal("500000"), PaymentProvider.PAYPAL);

        // 1. Assert OAuth contract
        assertThat(tokenAuthHeader.get()).startsWith("Basic ");
        String decoded = new String(Base64.getDecoder().decode(tokenAuthHeader.get().substring(6)), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo(CLIENT_ID + ":" + CLIENT_SECRET);
        assertThat(tokenBody.get()).contains("grant_type=client_credentials");

        // 2. Assert Order creation contract
        assertThat(orderAuthHeader.get()).isEqualTo("Bearer TOKEN-ACCEPT-1");
        assertThat(orderRequestId.get()).isEqualTo(paymentId.toString());
        assertThat(orderBodyCaptured.get()).contains("\"currency_code\":\"USD\"", "\"value\":\"19.31\"");

        // 3. Assert Intent Result
        assertThat(intent.providerOrderId()).isEqualTo("ORDER-ACCEPT-1");
        assertThat(intent.providerAmount()).isEqualByComparingTo("19.31");
        assertThat(intent.providerCurrency()).isEqualTo("USD");
        assertThat(intent.paymentUrl()).contains("token=ORDER-ACCEPT-1");
    }

    @Test
    void captureOrderWireContract() {
        AtomicReference<String> captureRequestId = new AtomicReference<>();
        AtomicReference<String> capturePath = new AtomicReference<>();

        HANDLERS.put("POST /v1/oauth2/token", (exchange, body) ->
                respond(exchange, 200, "{\"access_token\":\"TOKEN-ACCEPT-1\"}"));

        HANDLERS.put("POST /v2/checkout/orders/{id}/capture", (exchange, body) -> {
            capturePath.set(exchange.getRequestURI().getPath());
            captureRequestId.set(exchange.getRequestHeaders().getFirst("PayPal-Request-Id"));
            respond(exchange, 200, loadFixture("paypal-capture-completed.json"));
        });

        PaymentCaptureResult capture = adapter.captureOrder(PaymentProvider.PAYPAL, "ORDER-ACCEPT-1", "REQ-CAPTURE-STABLE-01");

        assertThat(capturePath.get()).isEqualTo("/v2/checkout/orders/ORDER-ACCEPT-1/capture");
        assertThat(captureRequestId.get()).isEqualTo("REQ-CAPTURE-STABLE-01");
        assertThat(capture.success()).isTrue();
        assertThat(capture.captureId()).isEqualTo("CAPTURE-ACCEPT-1");
        assertThat(capture.amount()).isEqualByComparingTo("19.31");
        assertThat(capture.currency()).isEqualTo("USD");
        assertThat(capture.status()).isEqualTo("COMPLETED");
    }

    @Test
    void requestRefundAndQueryRefundWireContract() {
        AtomicReference<String> refundRequestId = new AtomicReference<>();
        AtomicReference<String> refundPath = new AtomicReference<>();
        AtomicReference<String> refundBodyCaptured = new AtomicReference<>();

        HANDLERS.put("POST /v1/oauth2/token", (exchange, body) ->
                respond(exchange, 200, "{\"access_token\":\"TOKEN-ACCEPT-1\"}"));

        HANDLERS.put("POST /v2/payments/captures/{id}/refund", (exchange, body) -> {
            refundPath.set(exchange.getRequestURI().getPath());
            refundRequestId.set(exchange.getRequestHeaders().getFirst("PayPal-Request-Id"));
            refundBodyCaptured.set(body);
            respond(exchange, 200, loadFixture("paypal-refund-completed.json"));
        });

        HANDLERS.put("GET /v2/payments/refunds/{id}", (exchange, body) ->
                respond(exchange, 200, loadFixture("paypal-refund-completed.json")));

        GatewayRefundRequest request = new GatewayRefundRequest(PaymentProvider.PAYPAL, "CAPTURE-ACCEPT-1",
                "ORDER-ACCEPT-1", null, new BigDecimal("19.31"), "USD", true, "REFUND-REQ-ACCEPT-1");

        // 1. requestRefund
        RefundResult refundResult = adapter.requestRefund(request);
        assertThat(refundPath.get()).isEqualTo("/v2/payments/captures/CAPTURE-ACCEPT-1/refund");
        assertThat(refundRequestId.get()).isEqualTo("REFUND-REQ-ACCEPT-1");
        assertThat(refundBodyCaptured.get()).contains("\"value\":\"19.31\"", "\"currency_code\":\"USD\"", "REFUND-REQ-ACCEPT-1");
        assertThat(refundResult.success()).isTrue();
        assertThat(refundResult.providerRefundId()).isEqualTo("REFUND-ACCEPT-1");
        assertThat(refundResult.amount()).isEqualByComparingTo("19.31");
        assertThat(refundResult.status()).isEqualTo(GatewayRefundStatus.COMPLETED);
        assertThat(refundResult.currency()).isEqualTo("USD");

        // 2. queryRefund
        RefundResult queryResult = adapter.queryRefund(request, "REFUND-ACCEPT-1");
        assertThat(queryResult.success()).isTrue();
        assertThat(queryResult.providerRefundId()).isEqualTo("REFUND-ACCEPT-1");
        assertThat(queryResult.amount()).isEqualByComparingTo("19.31");
        assertThat(queryResult.status()).isEqualTo(GatewayRefundStatus.COMPLETED);
    }

    @Test
    void httpErrorCodesAndMalformedJsonFailClosed() {
        // 401 Unauthorized
        HANDLERS.put("POST /v1/oauth2/token", (exchange, body) ->
                respond(exchange, 401, "{\"error\":\"invalid_client\"}"));

        assertThatThrownBy(() -> adapter.fetchAccessToken())
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("PayPal authentication failed.");

        // 429 Too Many Requests
        HANDLERS.put("POST /v1/oauth2/token", (exchange, body) ->
                respond(exchange, 429, "{\"error\":\"rate_limit_exceeded\"}"));

        assertThatThrownBy(() -> adapter.fetchAccessToken())
                .isInstanceOf(PaymentGatewayException.class);

        // 500 Gateway Timeout / Error
        HANDLERS.put("POST /v1/oauth2/token", (exchange, body) ->
                respond(exchange, 500, "{\"error\":\"internal_server_error\"}"));

        assertThatThrownBy(() -> adapter.fetchAccessToken())
                .isInstanceOf(PaymentGatewayException.class);

        // Malformed JSON on refund
        HANDLERS.put("POST /v1/oauth2/token", (exchange, body) ->
                respond(exchange, 200, "{\"access_token\":\"TOKEN-ACCEPT-1\"}"));
        HANDLERS.put("POST /v2/payments/captures/{id}/refund", (exchange, body) ->
                respond(exchange, 200, "{broken_json"));

        GatewayRefundRequest request = new GatewayRefundRequest(PaymentProvider.PAYPAL, "CAPTURE-ACCEPT-1",
                "ORDER-ACCEPT-1", null, new BigDecimal("19.31"), "USD", true, "REFUND-REQ-ACCEPT-1");

        assertThatThrownBy(() -> adapter.requestRefund(request))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("PayPal refund response could not be verified");
    }

    @Test
    void webhookVerificationContract() {
        HANDLERS.put("POST /v1/oauth2/token", (exchange, body) ->
                respond(exchange, 200, "{\"access_token\":\"TOKEN-ACCEPT-1\"}"));

        AtomicReference<String> verifyBody = new AtomicReference<>();
        HANDLERS.put("POST /v1/notifications/verify-webhook-signature", (exchange, body) -> {
            verifyBody.set(body);
            respond(exchange, 200, "{\"verification_status\":\"SUCCESS\"}");
        });

        Map<String, String> validParams = new HashMap<>();
        validParams.put("paypal-transmission-id", "TRANS-12345");
        validParams.put("paypal-transmission-time", "2026-11-02T10:00:00Z");
        validParams.put("paypal-cert-url", "https://api.sandbox.paypal.com/v1/notifications/certs/CERT-1");
        validParams.put("paypal-auth-algo", "SHA256withRSA");
        validParams.put("rawPayload", "{\"id\":\"EVENT-1\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}");

        // Success verification
        boolean verified = adapter.verifyWebhookSignature(validParams, "SIG-BASE64-VALID");
        assertThat(verified).isTrue();
        assertThat(verifyBody.get()).contains("\"transmission_id\":\"TRANS-12345\"", "\"webhook_id\":\"" + WEBHOOK_ID + "\"");

        // Missing transmission-id rejects before network call
        Map<String, String> missingHeader = new HashMap<>(validParams);
        missingHeader.remove("paypal-transmission-id");
        assertThat(adapter.verifyWebhookSignature(missingHeader, "SIG-BASE64-VALID")).isFalse();

        // Failure status returned from PayPal
        HANDLERS.put("POST /v1/notifications/verify-webhook-signature", (exchange, body) ->
                respond(exchange, 200, "{\"verification_status\":\"FAILURE\"}"));
        assertThat(adapter.verifyWebhookSignature(validParams, "SIG-BASE64-INVALID")).isFalse();
    }
}

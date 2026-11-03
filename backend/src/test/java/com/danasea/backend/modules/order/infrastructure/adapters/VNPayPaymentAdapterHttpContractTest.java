package com.danasea.backend.modules.order.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.danasea.backend.configs.properties.VNPayProperties;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundRequest;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.ports.RefundResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@DisplayName("VNPay Payment Adapter HTTP Wire and Signature Contract Tests")
class VNPayPaymentAdapterHttpContractTest {

    private static HttpServer server;
    private static int port;
    private static final String TMN_CODE = "TESTCODE";
    private static final String SECRET = "test-only-vnpay-signing-secret-32-chars";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Map<String, PathHandler> HANDLERS = new ConcurrentHashMap<>();

    private VNPayPaymentAdapter adapter;

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

            PathHandler handler = HANDLERS.get(exchange.getRequestMethod() + " " + path);
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

    @BeforeEach
    void setUp() {
        HANDLERS.clear();
        String apiUrl = "http://127.0.0.1:" + port + "/merchant_webapi/api/transaction";
        VNPayProperties props = new VNPayProperties(TMN_CODE, SECRET,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", null, apiUrl);
        adapter = new VNPayPaymentAdapter(props, RestClient.builder(), MAPPER);
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
        try (InputStream is = VNPayPaymentAdapterHttpContractTest.class
                .getResourceAsStream("/payments/provider-contract/" + filename)) {
            if (is == null) throw new IllegalArgumentException("Fixture not found: " + filename);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Independent test calculation of HMAC-SHA512. */
    private static String computeHmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * bytes.length);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createIntentWireContract() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        BigDecimal amountVnd = new BigDecimal("1000000");

        PaymentIntentResult intent = adapter.createPaymentIntent(paymentId, orderId, amountVnd, PaymentProvider.VNPAY);

        // 1. Amount x100 check
        assertThat(intent.paymentUrl()).contains("vnp_Amount=100000000");

        // 2. Vietnam zone date check
        String createDate = intent.providerTransactionDate();
        assertThat(createDate).matches("^[0-9]{14}$");
        ZonedDateTime nowVietnam = ZonedDateTime.now(VIETNAM_ZONE);
        assertThat(createDate.substring(0, 8)).isEqualTo(DateTimeFormatter.ofPattern("yyyyMMdd").format(nowVietnam));

        // 3. TxnRef and TmnCode check
        assertThat(intent.paymentUrl()).contains("vnp_TxnRef=" + paymentId);
        assertThat(intent.paymentUrl()).contains("vnp_TmnCode=" + TMN_CODE);

        // 4. Independent HMAC check
        String paymentUrl = intent.paymentUrl();
        int queryStart = paymentUrl.indexOf("?") + 1;
        int hashParamIndex = paymentUrl.indexOf("&vnp_SecureHash=");
        assertThat(hashParamIndex).isGreaterThan(queryStart);

        String dataHashed = paymentUrl.substring(queryStart, hashParamIndex);
        String secureHashInUrl = paymentUrl.substring(hashParamIndex + "&vnp_SecureHash=".length());

        String expectedHash = computeHmacSha512(SECRET, dataHashed);
        assertThat(secureHashInUrl).isEqualToIgnoringCase(expectedHash);
    }

    @Test
    void verifyWebhookSignatureContract() {
        Map<String, String> params = new HashMap<>(Map.of(
                "vnp_Amount", "100000000",
                "vnp_TmnCode", TMN_CODE,
                "vnp_TxnRef", "ORDER-ACCEPT-1",
                "vnp_ResponseCode", "00"
        ));

        // Calculate valid signature using independent test vector
        String hashData = "vnp_Amount=100000000&vnp_ResponseCode=00&vnp_TmnCode=" + TMN_CODE + "&vnp_TxnRef=ORDER-ACCEPT-1";
        String validSignature = computeHmacSha512(SECRET, hashData);

        // Valid signature accepted
        assertThat(adapter.verifyWebhookSignature(params, validSignature)).isTrue();

        // Tampered amount rejected
        Map<String, String> tamperedAmount = new HashMap<>(params);
        tamperedAmount.put("vnp_Amount", "50000000");
        assertThat(adapter.verifyWebhookSignature(tamperedAmount, validSignature)).isFalse();

        // Tampered merchant code rejected
        Map<String, String> tamperedMerchant = new HashMap<>(params);
        tamperedMerchant.put("vnp_TmnCode", "OTHER_MERCHANT");
        assertThat(adapter.verifyWebhookSignature(tamperedMerchant, validSignature)).isFalse();

        // Invalid signature rejected
        assertThat(adapter.verifyWebhookSignature(params, "0000000000000000000000000000000000000000000000000000000000000000")).isFalse();
    }

    @Test
    void requestRefundAndQueryRefundWireContract() throws Exception {
        AtomicReference<String> requestBodyCaptured = new AtomicReference<>();

        HANDLERS.put("POST /merchant_webapi/api/transaction", (exchange, body) -> {
            requestBodyCaptured.set(body);
            if (body.contains("\"vnp_Command\":\"refund\"")) {
                respond(exchange, 200, loadFixture("vnpay-refund-pending.json"));
            } else if (body.contains("\"vnp_Command\":\"querydr\"")) {
                respond(exchange, 200, loadFixture("vnpay-query-completed.json"));
            } else {
                respond(exchange, 400, "{\"error\":\"Unknown command\"}");
            }
        });

        GatewayRefundRequest request = new GatewayRefundRequest(PaymentProvider.VNPAY, "987654321", "ORDER-ACCEPT-1",
                "20261102120000", new BigDecimal("1000000"), "VND", true, "REFUND-REQ-ACCEPT-1");

        // 1. requestRefund
        RefundResult refundResult = adapter.requestRefund(request);
        JsonNode refundPayload = MAPPER.readTree(requestBodyCaptured.get());
        assertThat(refundPayload.path("vnp_Command").asText()).isEqualTo("refund");
        assertThat(refundPayload.path("vnp_Amount").asText()).isEqualTo("100000000");
        assertThat(refundPayload.path("vnp_TmnCode").asText()).isEqualTo(TMN_CODE);
        assertThat(refundPayload.path("vnp_TxnRef").asText()).isEqualTo("ORDER-ACCEPT-1");

        assertThat(refundResult.status()).isEqualTo(GatewayRefundStatus.PENDING);
        assertThat(refundResult.amount()).isEqualByComparingTo("1000000");
        assertThat(refundResult.providerRefundId()).isEqualTo("REFUND-PENDING-1");

        // 2. queryRefund
        RefundResult queryResult = adapter.queryRefund(request, "REFUND-ACCEPT-1");
        JsonNode queryPayload = MAPPER.readTree(requestBodyCaptured.get());
        assertThat(queryPayload.path("vnp_Command").asText()).isEqualTo("querydr");
        assertThat(queryPayload.path("vnp_TxnRef").asText()).isEqualTo("ORDER-ACCEPT-1");

        assertThat(queryResult.status()).isEqualTo(GatewayRefundStatus.COMPLETED);
        assertThat(queryResult.amount()).isEqualByComparingTo("1000000");
        assertThat(queryResult.providerRefundId()).isEqualTo("REFUND-ACCEPT-1");
    }

    @Test
    void tamperedResponseFieldsAreRejected() throws Exception {
        String querydrJson = loadFixture("vnpay-query-completed.json");
        ObjectNode rootNode = (ObjectNode) MAPPER.readTree(querydrJson);

        GatewayRefundRequest request = new GatewayRefundRequest(PaymentProvider.VNPAY, "987654321", "ORDER-ACCEPT-1",
                "20261102120000", new BigDecimal("1000000"), "VND", true, "REFUND-REQ-ACCEPT-1");

        // A. Tampered SecureHash -> throws PaymentGatewayException
        ObjectNode tamperedHash = rootNode.deepCopy();
        tamperedHash.put("vnp_SecureHash", "deadbeef000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
        HANDLERS.put("POST /merchant_webapi/api/transaction", (exchange, body) ->
                respond(exchange, 200, tamperedHash.toString()));

        assertThatThrownBy(() -> adapter.queryRefund(request, "REFUND-ACCEPT-1"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Invalid VNPay refund response checksum");

        // B. Tampered TmnCode -> throws PaymentGatewayException
        ObjectNode tamperedTmn = rootNode.deepCopy();
        tamperedTmn.put("vnp_TmnCode", "WRONG_TMN");
        // Recompute hash for the tampered body to verify TmnCode check catches it
        rehash(tamperedTmn, true);
        HANDLERS.put("POST /merchant_webapi/api/transaction", (exchange, body) ->
                respond(exchange, 200, tamperedTmn.toString()));

        assertThatThrownBy(() -> adapter.queryRefund(request, "REFUND-ACCEPT-1"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Invalid VNPay refund response checksum or merchant");

        // C. Tampered Amount -> returns UNKNOWN
        ObjectNode tamperedAmount = rootNode.deepCopy();
        tamperedAmount.put("vnp_Amount", "50000000"); // 500,000 VND instead of 1,000,000
        rehash(tamperedAmount, true);
        HANDLERS.put("POST /merchant_webapi/api/transaction", (exchange, body) ->
                respond(exchange, 200, tamperedAmount.toString()));

        RefundResult amountMismatch = adapter.queryRefund(request, "REFUND-ACCEPT-1");
        assertThat(amountMismatch.success()).isFalse();
        assertThat(amountMismatch.status()).isEqualTo(GatewayRefundStatus.UNKNOWN);
        assertThat(amountMismatch.message()).contains("The refund amount or transaction ID does not match");

        // D. Tampered TxnRef -> returns UNKNOWN
        ObjectNode tamperedTxnRef = rootNode.deepCopy();
        tamperedTxnRef.put("vnp_TxnRef", "DIFFERENT-ORDER-ID");
        rehash(tamperedTxnRef, true);
        HANDLERS.put("POST /merchant_webapi/api/transaction", (exchange, body) ->
                respond(exchange, 200, tamperedTxnRef.toString()));

        RefundResult refMismatch = adapter.queryRefund(request, "REFUND-ACCEPT-1");
        assertThat(refMismatch.success()).isFalse();
        assertThat(refMismatch.status()).isEqualTo(GatewayRefundStatus.UNKNOWN);
        assertThat(refMismatch.message()).contains("The response does not identify the requested refund");
    }

    private static void rehash(ObjectNode node, boolean query) {
        List<String> fields = new ArrayList<>(List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode",
                "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
                "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo"));
        if (query) fields.addAll(List.of("vnp_PromotionCode", "vnp_PromotionAmount"));
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            sb.append(node.path(field).asText("")).append("|");
        }
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        String hash = computeHmacSha512(SECRET, sb.toString());
        node.put("vnp_SecureHash", hash);
    }
}

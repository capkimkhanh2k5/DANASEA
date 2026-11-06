package com.danasea.backend.modules.order.infrastructure.adapters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.danasea.backend.configs.properties.VNPayProperties;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundRequest;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.ports.RefundResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Adapter triển khai cổng thanh toán nội địa VNPay.
 * Hỗ trợ tạo URL thanh toán VNPay-QR / ATM nội địa và xác thực chữ ký HMAC-SHA512.
 */
@Component
@Slf4j
public class VNPayPaymentAdapter implements PaymentGatewayPort {

    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VNPayProperties vnpayProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public VNPayPaymentAdapter(
            VNPayProperties vnpayProperties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.vnpayProperties = vnpayProperties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.restClient = (restClientBuilder != null
                ? restClientBuilder
                : RestClient.builder())
                .baseUrl(vnpayProperties.apiUrl())
                .build();
    }

    @Override
    public boolean supportsProvider(PaymentProvider provider) {
        return provider == PaymentProvider.VNPAY;
    }

    public VNPayPaymentAdapter(VNPayProperties vnpayProperties) {
        this(vnpayProperties, RestClient.builder(), new ObjectMapper());
    }

    @Override
    public PaymentIntentResult createPaymentIntent(UUID paymentId, UUID orderId, BigDecimal amount, PaymentProvider provider) {
        UUID effectivePaymentId = paymentId != null ? paymentId : UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(15);

        BigDecimal vnpAmount = (amount != null ? amount : BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP);

        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        String createDate = VNPAY_DATE_FORMAT.format(now);
        String expireDate = VNPAY_DATE_FORMAT.format(now.plusMinutes(15));

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnpayProperties.tmnCode());
        vnpParams.put("vnp_Amount", vnpAmount.toPlainString());
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", effectivePaymentId.toString());
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang DANASea " + orderId);
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnpayProperties.returnUrl());
        vnpParams.put("vnp_IpAddr", vnpayProperties.ipAddress());
        vnpParams.put("vnp_CreateDate", createDate);
        vnpParams.put("vnp_ExpireDate", expireDate);

        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isBlank()) {
                String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII);
                String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII);

                hashData.append(encodedKey).append('=').append(encodedValue).append('&');
                query.append(encodedKey).append('=').append(encodedValue).append('&');
            }
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
            query.setLength(query.length() - 1);
        }

        String secureHash = hmacSHA512(vnpayProperties.hashSecret(), hashData.toString());
        String paymentUrl = vnpayProperties.payUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;
        String qrCodeUrl = paymentUrl;

        return new PaymentIntentResult(
                effectivePaymentId,
                orderId,
                PaymentProvider.VNPAY,
                amount,
                paymentUrl,
                qrCodeUrl,
                expiresAt, effectivePaymentId.toString(), amount, "VND", createDate
        );
    }

    @Override
    public boolean verifyWebhookSignature(Map<String, String> rawParams, String signature) {
        if (signature == null || signature.isBlank() || rawParams == null || rawParams.isEmpty()
                || !vnpayProperties.tmnCode().equals(rawParams.get("vnp_TmnCode"))) {
            return false;
        }

        Map<String, String> fields = new HashMap<>(rawParams);
        fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isBlank()) {
                String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII);
                String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII);
                hashData.append(encodedKey).append('=').append(encodedValue).append('&');
            }
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
        }

        try {
            String expected = hmacSHA512(vnpayProperties.hashSecret(), hashData.toString());
            byte[] expectedBytes = expected.toLowerCase().getBytes(StandardCharsets.US_ASCII);
            byte[] suppliedBytes = signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expectedBytes, suppliedBytes);
        } catch (Exception ex) {
            log.warn("VNPay signature verification error: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public RefundResult requestRefund(GatewayRefundRequest request) {
        requireContext(request);
        String createDate = VNPAY_DATE_FORMAT.format(ZonedDateTime.now(VIETNAM_ZONE));
        String requestId = request.requestId().replace("-", "");
        String type = request.fullRefund() ? "02" : "03";
        String amount = request.amount().multiply(BigDecimal.valueOf(100)).toBigIntegerExact().toString();
        String info = "DANASea refund " + requestId;
        Map<String, Object> payload = new HashMap<>();
        payload.put("vnp_RequestId", requestId);
        payload.put("vnp_Version", "2.1.0");
        payload.put("vnp_Command", "refund");
        payload.put("vnp_TmnCode", vnpayProperties.tmnCode());
        payload.put("vnp_TransactionType", type);
        payload.put("vnp_TxnRef", request.orderId());
        payload.put("vnp_Amount", amount);
        payload.put("vnp_OrderInfo", info);
        payload.put("vnp_TransactionNo", request.transactionId());
        payload.put("vnp_TransactionDate", request.transactionDate());
        payload.put("vnp_CreateBy", "DANASEA_SYSTEM");
        payload.put("vnp_CreateDate", createDate);
        payload.put("vnp_IpAddr", vnpayProperties.ipAddress());
        payload.put("vnp_SecureHash", hmacSHA512(vnpayProperties.hashSecret(), String.join("|", requestId,
                "2.1.0", "refund", vnpayProperties.tmnCode(), type, request.orderId(), amount,
                request.transactionId(), request.transactionDate(), "DANASEA_SYSTEM", createDate,
                vnpayProperties.ipAddress(), info)));
        JsonNode root = exchange(payload);
        verifyApiResponse(root, false);
        return verifiedRefund(root, request, null);
    }

    @Override
    public PaymentCaptureResult queryPayment(PaymentProvider provider, String providerOrderId, String transactionDate) {
        if (provider != PaymentProvider.VNPAY || providerOrderId == null || transactionDate == null) {
            throw new PaymentGatewayException("VNPay reference and transaction date are required for query.");
        }
        String createDate = VNPAY_DATE_FORMAT.format(ZonedDateTime.now(VIETNAM_ZONE));
        String queryId = UUID.randomUUID().toString().replace("-", "");
        String info = "DANASea payment lookup " + providerOrderId;
        Map<String, Object> payload = new HashMap<>();
        payload.put("vnp_RequestId", queryId);
        payload.put("vnp_Version", "2.1.0");
        payload.put("vnp_Command", "querydr");
        payload.put("vnp_TmnCode", vnpayProperties.tmnCode());
        payload.put("vnp_TxnRef", providerOrderId);
        payload.put("vnp_TransactionDate", transactionDate);
        payload.put("vnp_CreateDate", createDate);
        payload.put("vnp_IpAddr", vnpayProperties.ipAddress());
        payload.put("vnp_OrderInfo", info);
        payload.put("vnp_SecureHash", hmacSHA512(vnpayProperties.hashSecret(), String.join("|", queryId,
                "2.1.0", "querydr", vnpayProperties.tmnCode(), providerOrderId, transactionDate,
                createDate, vnpayProperties.ipAddress(), info)));

        JsonNode root = exchange(payload);
        verifyApiResponse(root, true);

        String code = root.path("vnp_ResponseCode").asText("");
        if (!"00".equals(code)) {
            return new PaymentCaptureResult(false, null, null, "VND", "UNKNOWN", "VNPay lookup failed: " + code);
        }
        if (!providerOrderId.equals(root.path("vnp_TxnRef").asText())) {
            throw new PaymentGatewayException("VNPay transaction reference does not match the requested payment.");
        }
        if (!"01".equals(root.path("vnp_TransactionType").asText())) {
            return new PaymentCaptureResult(false, null, null, "VND", "UNKNOWN", "VNPay response is not a payment transaction.");
        }
        String state = root.path("vnp_TransactionStatus").asText("");
        String transactionNo = root.path("vnp_TransactionNo").asText(null);
        BigDecimal amount = root.hasNonNull("vnp_Amount")
                ? new BigDecimal(root.path("vnp_Amount").asText()).divide(BigDecimal.valueOf(100))
                : null;

        boolean success = "00".equals(code) && "00".equals(state);
        String status;
        if (success) {
            status = "COMPLETED";
        } else if ("02".equals(state)) {
            status = "FAILED";
        } else if ("01".equals(state) || "05".equals(state) || "06".equals(state)) {
            status = "PENDING";
        } else {
            status = "UNKNOWN";
        }

        return new PaymentCaptureResult(
                success,
                transactionNo,
                amount,
                "VND",
                status,
                "VNPay querydr responseCode: " + code + ", status: " + state
        );
    }

    @Override
    public RefundResult queryRefund(GatewayRefundRequest request, String providerRefundId) {
        requireContext(request);
        String createDate = VNPAY_DATE_FORMAT.format(ZonedDateTime.now(VIETNAM_ZONE));
        String queryId = UUID.randomUUID().toString().replace("-", "");
        String info = "DANASea refund lookup " + request.requestId().replace("-", "");
        Map<String, Object> payload = new HashMap<>();
        payload.put("vnp_RequestId", queryId);
        payload.put("vnp_Version", "2.1.0");
        payload.put("vnp_Command", "querydr");
        payload.put("vnp_TmnCode", vnpayProperties.tmnCode());
        payload.put("vnp_TxnRef", request.orderId());
        payload.put("vnp_TransactionNo", request.transactionId());
        payload.put("vnp_TransactionDate", request.transactionDate());
        payload.put("vnp_CreateDate", createDate);
        payload.put("vnp_IpAddr", vnpayProperties.ipAddress());
        payload.put("vnp_OrderInfo", info);
        payload.put("vnp_SecureHash", hmacSHA512(vnpayProperties.hashSecret(), String.join("|", queryId,
                "2.1.0", "querydr", vnpayProperties.tmnCode(), request.orderId(), request.transactionDate(),
                createDate, vnpayProperties.ipAddress(), info)));
        JsonNode root = exchange(payload);
        verifyApiResponse(root, true);
        return verifiedRefund(root, request, providerRefundId);
    }

    private RefundResult verifiedRefund(JsonNode root, GatewayRefundRequest request, String expectedRefundId) {
        String code = root.path("vnp_ResponseCode").asText();
        String type = root.path("vnp_TransactionType").asText();
        String id = root.path("vnp_TransactionNo").asText(null);
        String state = root.path("vnp_TransactionStatus").asText();
        if (!request.orderId().equals(root.path("vnp_TxnRef").asText())
                || !(request.fullRefund() ? "02" : "03").equals(type)
                || !root.hasNonNull("vnp_Amount")) {
            return unknown("The response does not identify the requested refund.");
        }
        BigDecimal amount = new BigDecimal(root.path("vnp_Amount").asText()).divide(BigDecimal.valueOf(100));
        if (amount.compareTo(request.amount()) != 0 || id == null || id.isBlank()
                || (expectedRefundId != null && !expectedRefundId.equals(id))) {
            return unknown("The refund amount or transaction ID does not match.");
        }
        if ("querydr".equals(root.path("vnp_Command").asText()) && expectedRefundId == null && !request.fullRefund()) {
            return unknown("A partial refund cannot be correlated without its provider refund ID.");
        }
        GatewayRefundStatus status;
        if ("00".equals(code) && "00".equals(state)) status = GatewayRefundStatus.COMPLETED;
        else if ("05".equals(state) || "06".equals(state) || "94".equals(code)) status = GatewayRefundStatus.PENDING;
        else if ("09".equals(state) || "95".equals(code)) status = GatewayRefundStatus.FAILED;
        else status = GatewayRefundStatus.UNKNOWN;
        return new RefundResult(status == GatewayRefundStatus.COMPLETED, id, amount,
                "VNPay refund status: " + state, status, "VND");
    }

    private JsonNode exchange(Map<String, Object> payload) {
        try {
            return objectMapper.readTree(restClient.post().uri(vnpayProperties.apiUrl())
                    .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(String.class));
        } catch (Exception ex) {
            throw new PaymentGatewayException("GATEWAY_TIMEOUT_AWAITING_VERIFICATION", ex);
        }
    }

    private void verifyApiResponse(JsonNode root, boolean query) {
        List<String> fields = new ArrayList<>(List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode",
                "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
                "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo"));
        if (query) fields.addAll(List.of("vnp_PromotionCode", "vnp_PromotionAmount"));
        String raw = fields.stream().map(key -> root.path(key).asText("")).collect(Collectors.joining("|"));
        String expected = hmacSHA512(vnpayProperties.hashSecret(), raw);
        if (!vnpayProperties.tmnCode().equals(root.path("vnp_TmnCode").asText())
                || !(query ? "querydr" : "refund").equals(root.path("vnp_Command").asText())
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    root.path("vnp_SecureHash").asText("").toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new PaymentGatewayException("Invalid VNPay refund response checksum or merchant.");
        }
    }

    private void requireContext(GatewayRefundRequest request) {
        if (request.provider() != PaymentProvider.VNPAY || request.orderId() == null || request.transactionId() == null
                || request.transactionDate() == null || !request.transactionDate().matches("[0-9]{14}")
                || request.amount() == null || request.amount().signum() <= 0 || !"VND".equals(request.currency())
                || request.requestId() == null || !request.requestId().replace("-", "").matches("[A-Za-z0-9]{1,32}")) {
            throw new PaymentGatewayException("The original VNPay reference, date, money and persisted request ID are required.");
        }
    }

    private RefundResult unknown(String message) {
        return new RefundResult(false, null, null, message, GatewayRefundStatus.UNKNOWN, null);
    }

    @Override
    public RefundResult requestRefund(String transactionId, BigDecimal amount) {
        throw new PaymentGatewayException("The original VNPay refund context is required.");
    }

    @Override
    public RefundResult requestRefund(PaymentProvider provider, String transactionId, BigDecimal amount, String key) {
        throw new PaymentGatewayException("The original VNPay reference and transaction date are required.");
    }

    @Override
    public RefundResult queryRefund(PaymentProvider provider, String transactionId, String refundId) {
        return unknown("The original VNPay context is required for reconciliation.");
    }

    public static String hmacSHA512(String key, String data) {
        if (key == null) {
            key = "";
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] result = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA512", ex);
        }
    }
}

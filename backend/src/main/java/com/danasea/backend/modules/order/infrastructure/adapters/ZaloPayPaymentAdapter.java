package com.danasea.backend.modules.order.infrastructure.adapters;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.danasea.backend.configs.properties.ZaloPayProperties;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentCancelResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.ports.RefundResult;
import com.danasea.backend.modules.order.infrastructure.adapters.zalopay.ZaloPaySignatureHelper;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ZaloPayPaymentAdapter implements PaymentGatewayPort {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    private final AtomicLong transCounter = new AtomicLong(100000L + Math.abs(System.nanoTime() % 800000L));

    private final RestClient restClient;
    private final ZaloPayProperties zaloPayProperties;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private JpaPaymentRepository paymentRepository;

    public void setPaymentRepository(JpaPaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Autowired
    public ZaloPayPaymentAdapter(
            RestClient.Builder restClientBuilder,
            ZaloPayProperties zaloPayProperties,
            ObjectMapper objectMapper) {
        this.zaloPayProperties = zaloPayProperties;
        this.objectMapper = objectMapper;
        this.restClient = (restClientBuilder != null ? restClientBuilder : RestClient.builder())
                .baseUrl(zaloPayProperties.endpoint())
                .build();
    }

    @Override
    public boolean supportsProvider(PaymentProvider provider) {
        return provider == PaymentProvider.ZALOPAY;
    }

    @Override
    public boolean canCancelServerSide(PaymentProvider provider) {
        return provider == PaymentProvider.ZALOPAY;
    }

    @Override
    public PaymentIntentResult createPaymentIntent(UUID paymentId, UUID orderId, BigDecimal amount,
            PaymentProvider provider) {
        return createPaymentIntent(paymentId, orderId, amount, provider, (String) null, null);
    }

    @Override
    public PaymentIntentResult createPaymentIntent(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            PaymentProvider provider,
            Long specificOrderCode,
            OffsetDateTime expiresAt) {
        String customAppTransId = specificOrderCode != null
                ? (DATE_FORMATTER.format(LocalDate.now(VIETNAM_ZONE)) + "_" + specificOrderCode)
                : null;
        return createPaymentIntent(paymentId, orderId, amount, provider, customAppTransId, expiresAt);
    }

    public PaymentIntentResult createPaymentIntent(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            PaymentProvider provider,
            String customAppTransId,
            OffsetDateTime expiresAt) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        String appTransId = customAppTransId != null ? customAppTransId : generateAppTransId();
        long appTime = System.currentTimeMillis();
        long amountLong = amount.longValue();
        String appUser = "DANASEA_CUSTOMER";

        String baseReturnUrl = PaymentUrlHelper.resolveRedirectUrl(zaloPayProperties.returnUrl(), "/checkout/result");
        String returnUrl = PaymentUrlHelper.appendQueryParams(baseReturnUrl,
                Map.of("orderId", String.valueOf(orderId), "paymentId", String.valueOf(paymentId)));
        String embedData = "{\"redirecturl\":\"" + returnUrl + "\"}";
        String item = "[]";

        String mac = ZaloPaySignatureHelper.createOrderMac(
                zaloPayProperties.appId(),
                appTransId,
                appUser,
                amountLong,
                appTime,
                embedData,
                item,
                zaloPayProperties.key1());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("app_id", zaloPayProperties.appId());
        requestBody.put("app_trans_id", appTransId);
        requestBody.put("app_user", appUser);
        requestBody.put("app_time", appTime);
        requestBody.put("amount", amountLong);
        requestBody.put("item", item);
        requestBody.put("description", "DANASEA Order " + appTransId);
        requestBody.put("embed_data", embedData);
        requestBody.put("bank_code", "");
        requestBody.put("expire_duration_seconds", 300);
        requestBody.put("mac", mac);

        try {
            String response = restClient.post()
                    .uri("/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            int returnCode = root.path("return_code").asInt();
            if (returnCode != 1) {
                String returnMsg = root.path("return_message").asText("ZaloPay order creation failed");
                throw new PaymentGatewayException("ZaloPay rejected order: " + returnMsg);
            }

            String orderUrl = root.path("order_url").asText();
            String zpTransToken = root.path("zp_trans_token").asText(null);

            return new PaymentIntentResult(
                    paymentId,
                    orderId,
                    PaymentProvider.ZALOPAY,
                    amount,
                    orderUrl,
                    orderUrl,
                    expiresAt,
                    appTransId,
                    amount,
                    "VND",
                    null);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to create ZaloPay order for appTransId={}", appTransId, ex);
            throw new PaymentGatewayException("Failed to communicate with ZaloPay.", ex);
        }
    }

    @Override
    public boolean verifyWebhookSignature(Map<String, String> rawParams, String signature) {
        String data = rawParams.get("data");
        String mac = signature != null ? signature : rawParams.get("mac");
        return ZaloPaySignatureHelper.verifyCallbackMac(data, mac, zaloPayProperties.key2());
    }

    public boolean verifyCallback(String data, String mac) {
        return ZaloPaySignatureHelper.verifyCallbackMac(data, mac, zaloPayProperties.key2());
    }

    @Override
    public PaymentCancelResult cancelPayment(PaymentProvider provider, String providerOrderId, String reason) {
        if (provider != PaymentProvider.ZALOPAY || providerOrderId == null || providerOrderId.isBlank()) {
            return PaymentCancelResult.failed("Invalid provider or orderId", "INVALID_REQUEST");
        }

        String mac = ZaloPaySignatureHelper.cancelOrderMac(
                zaloPayProperties.appId(),
                providerOrderId,
                zaloPayProperties.key1());

        Map<String, Object> body = Map.of(
                "app_id", zaloPayProperties.appId(),
                "app_trans_id", providerOrderId,
                "mac", mac);

        try {
            String response = restClient.post()
                    .uri("/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            int returnCode = root.path("return_code").asInt();
            int subReturnCode = root.path("sub_return_code").asInt();

            if (returnCode == 1) {
                return PaymentCancelResult.success("ZaloPay order canceled successfully");
            }

            // In ZaloPay, sub_return_code = -403 or already captured indicates payment
            // completed
            String returnMsg = root.path("return_message").asText("");
            String subMsg = root.path("sub_return_message").asText("");
            if (subReturnCode == -403 || "ORDER_ALREADY_CAPTURED".equalsIgnoreCase(subMsg)
                    || returnMsg.toLowerCase().contains("thanh toan")
                    || returnMsg.toLowerCase().contains("already captured")) {
                return PaymentCancelResult.alreadyCaptured("ZaloPay order was already captured");
            }

            // If return_code is 2 or 3, query order to check actual capture status
            PaymentCaptureResult queryResult = queryPayment(PaymentProvider.ZALOPAY, providerOrderId, null);
            if (queryResult.success() && "COMPLETED".equalsIgnoreCase(queryResult.status())) {
                return PaymentCancelResult.alreadyCaptured("ZaloPay order captured prior to cancel");
            }

            return PaymentCancelResult.failed(root.path("return_message").asText("Cancel failed"),
                    String.valueOf(returnCode));
        } catch (RestClientResponseException ex) {
            log.warn("ZaloPay cancel HTTP error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return PaymentCancelResult.unknown("ZaloPay cancel HTTP error: " + ex.getMessage(),
                    String.valueOf(ex.getStatusCode().value()));
        } catch (Exception ex) {
            log.error("Exception calling ZaloPay cancel for appTransId={}", providerOrderId, ex);
            return PaymentCancelResult.unknown("Network exception calling ZaloPay cancel: " + ex.getMessage(),
                    "NETWORK_ERROR");
        }
    }

    @Override
    public PaymentCaptureResult queryPayment(PaymentProvider provider, String providerOrderId, String transactionDate) {
        String mac = ZaloPaySignatureHelper.queryOrderMac(
                zaloPayProperties.appId(),
                providerOrderId,
                zaloPayProperties.key1());

        Map<String, Object> body = Map.of(
                "app_id", zaloPayProperties.appId(),
                "app_trans_id", providerOrderId,
                "mac", mac);

        try {
            String response = restClient.post()
                    .uri("/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            int returnCode = root.path("return_code").asInt();
            BigDecimal amount = root.has("amount") && !root.path("amount").isNull()
                    ? new BigDecimal(root.path("amount").asText())
                    : null;
            if (returnCode == 1) {
                return new PaymentCaptureResult(true, providerOrderId, amount, "VND", "COMPLETED",
                        "Payment captured on ZaloPay");
            } else if (returnCode == 2) {
                return new PaymentCaptureResult(false, providerOrderId, amount, "VND", "FAILED",
                        root.path("return_message").asText("Payment failed"));
            } else if (returnCode == 4) {
                return new PaymentCaptureResult(false, providerOrderId, amount, "VND", "CANCELLED",
                        root.path("return_message").asText("Payment cancelled"));
            }
            return new PaymentCaptureResult(false, providerOrderId, amount, "VND", "PENDING",
                    "Payment pending on ZaloPay");
        } catch (Exception ex) {
            log.error("Failed to query ZaloPay order for appTransId={}", providerOrderId, ex);
            return new PaymentCaptureResult(false, null, null, "VND", "UNKNOWN", ex.getMessage());
        }
    }

    @Override
    public RefundResult requestRefund(String providerTransactionId, BigDecimal amount) {
        return new RefundResult(false, "MANUAL_REVIEW_REQUIRED", amount,
                "ZaloPay automated API refund requires full context/manual review in sandbox");
    }

    public String generateAppTransId() {
        return generateAppTransId(LocalDate.now(VIETNAM_ZONE));
    }

    public String generateAppTransId(LocalDate date) {
        if (paymentRepository != null) {
            Long seq = paymentRepository.getNextPayOSOrderCode();
            if (seq != null) {
                return date.format(DATE_FORMATTER) + "_" + seq;
            }
            throw new IllegalStateException(
                    "PostgreSQL sequence payos_order_code_seq returned null for ZaloPay app_trans_id");
        }
        long nextVal = transCounter.incrementAndGet();
        long suffix = 100000L + Math.floorMod(nextVal, 900000L);
        return date.format(DATE_FORMATTER) + "_" + String.format("%06d", suffix);
    }
}

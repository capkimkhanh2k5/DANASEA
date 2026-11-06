package com.danasea.backend.modules.order.infrastructure.adapters;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.danasea.backend.configs.properties.PayOSProperties;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentCancelResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.ports.RefundResult;
import com.danasea.backend.modules.order.infrastructure.adapters.payos.PayOSSignatureHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class PayOSPaymentAdapter implements PaymentGatewayPort {

    private final RestClient restClient;
    private final PayOSProperties payOSProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public PayOSPaymentAdapter(
            RestClient.Builder restClientBuilder,
            PayOSProperties payOSProperties,
            ObjectMapper objectMapper) {
        this.payOSProperties = payOSProperties;
        this.objectMapper = objectMapper;
        this.restClient = (restClientBuilder != null ? restClientBuilder : RestClient.builder())
                .baseUrl(payOSProperties.baseUrl())
                .build();
    }

    @Override
    public boolean supportsProvider(PaymentProvider provider) {
        return provider == PaymentProvider.PAYOS;
    }

    @Override
    public boolean canCancelServerSide(PaymentProvider provider) {
        return provider == PaymentProvider.PAYOS;
    }

    @Override
    public PaymentIntentResult createPaymentIntent(UUID paymentId, UUID orderId, BigDecimal amount, PaymentProvider provider) {
        return createPaymentIntent(paymentId, orderId, amount, provider, null, null);
    }

    @Override
    public PaymentIntentResult createPaymentIntent(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            PaymentProvider provider,
            Long specificOrderCode,
            OffsetDateTime expiresAt) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        long orderCode = specificOrderCode != null ? specificOrderCode : System.currentTimeMillis();
        long amountLong = amount.longValueExact();
        String description = "DANASEA";

        String returnUrl = PaymentUrlHelper.appendQueryParams(payOSProperties.returnUrl(),
                Map.of("orderId", orderId.toString(), "paymentId", paymentId.toString()));
        String cancelUrl = PaymentUrlHelper.appendQueryParams(payOSProperties.cancelUrl(),
                Map.of("orderId", orderId.toString(), "paymentId", paymentId.toString(), "status", "cancelled"));

        String signature = PayOSSignatureHelper.createRequestSignature(
                amountLong,
                cancelUrl,
                description,
                orderCode,
                returnUrl,
                payOSProperties.checksumKey()
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("orderCode", orderCode);
        requestBody.put("amount", amountLong);
        requestBody.put("description", description);
        requestBody.put("returnUrl", returnUrl);
        requestBody.put("cancelUrl", cancelUrl);
        requestBody.put("signature", signature);

        if (expiresAt != null) {
            requestBody.put("expiredAt", expiresAt.toEpochSecond());
        }

        try {
            String response = restClient.post()
                    .uri("/v2/payment-requests")
                    .header("x-client-id", payOSProperties.clientId())
                    .header("x-api-key", payOSProperties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String code = root.path("code").asText();
            if (!"00".equals(code)) {
                String desc = root.path("desc").asText("payOS creation failed");
                throw new PaymentGatewayException("payOS rejected payment request: " + desc);
            }

            JsonNode data = root.path("data");
            String checkoutUrl = data.path("checkoutUrl").asText();
            String qrCode = data.path("qrCode").asText(null);

            return new PaymentIntentResult(
                    paymentId,
                    orderId,
                    PaymentProvider.PAYOS,
                    amount,
                    checkoutUrl,
                    qrCode,
                    expiresAt,
                    String.valueOf(orderCode),
                    amount,
                    "VND",
                    null
            );
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to create payOS payment request for orderCode={}", orderCode, ex);
            throw new PaymentGatewayException("Failed to communicate with payOS.", ex);
        }
    }

    @Override
    public boolean verifyWebhookSignature(Map<String, String> rawParams, String signature) {
        Map<String, Object> dataMap = new HashMap<>(rawParams);
        return PayOSSignatureHelper.verifyWebhookSignature(dataMap, signature, payOSProperties.checksumKey());
    }

    public boolean verifyWebhookData(Map<String, Object> dataMap, String signature) {
        return PayOSSignatureHelper.verifyWebhookSignature(dataMap, signature, payOSProperties.checksumKey());
    }

    @Override
    public PaymentCancelResult cancelPayment(PaymentProvider provider, String providerOrderId, String reason) {
        if (provider != PaymentProvider.PAYOS || providerOrderId == null || providerOrderId.isBlank()) {
            return PaymentCancelResult.failed("Invalid provider or orderId", "INVALID_REQUEST");
        }

        try {
            Map<String, String> body = Map.of("cancellationReason", reason != null ? reason : "Order canceled or window expired");
            String response = restClient.post()
                    .uri("/v2/payment-requests/{orderCode}/cancel", providerOrderId)
                    .header("x-client-id", payOSProperties.clientId())
                    .header("x-api-key", payOSProperties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String code = root.path("code").asText();
            JsonNode data = root.path("data");
            if ("00".equals(code) && "CANCELLED".equalsIgnoreCase(data.path("status").asText())
                    && (providerOrderId.equals(data.path("orderCode").asText()) || providerOrderId.equals(data.path("id").asText()))
                    && data.hasNonNull("amountPaid") && new BigDecimal(data.path("amountPaid").asText()).signum() == 0) {
                return PaymentCancelResult.success("payOS link canceled successfully");
            }
            String desc = root.path("desc").asText("");
            if ("20".equals(code) || "118".equals(code) || desc.toLowerCase().contains("paid") || desc.toLowerCase().contains("already")) {
                return PaymentCancelResult.alreadyCaptured("payOS order was already captured");
            }
            return PaymentCancelResult.failed(root.path("desc").asText("Cancel failed"), code);
        } catch (RestClientResponseException ex) {
            log.warn("payOS cancel responded with error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return PaymentCancelResult.unknown("payOS cancel HTTP error: " + ex.getMessage(), String.valueOf(ex.getStatusCode().value()));
        } catch (Exception ex) {
            log.error("Exception calling payOS cancel for orderCode={}", providerOrderId, ex);
            return PaymentCancelResult.unknown("Network exception calling payOS cancel: " + ex.getMessage(), "NETWORK_ERROR");
        }
    }

    @Override
    public PaymentCaptureResult queryPayment(PaymentProvider provider, String providerOrderId, String transactionDate) {
        try {
            String response = restClient.get()
                    .uri("/v2/payment-requests/{orderCode}", providerOrderId)
                    .header("x-client-id", payOSProperties.clientId())
                    .header("x-api-key", payOSProperties.apiKey())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (!"00".equals(root.path("code").asText())
                    || (!providerOrderId.equals(data.path("orderCode").asText())
                    && !providerOrderId.equals(data.path("id").asText()))) {
                return new PaymentCaptureResult(false, null, null, "VND", "UNKNOWN", "Unverified payOS link response");
            }


            String status = data.path("status").asText();
            BigDecimal amount = data.hasNonNull("amountPaid") ? new BigDecimal(data.path("amountPaid").asText()) : null;
            if ("PAID".equalsIgnoreCase(status)) {
                JsonNode transactions = data.path("transactions");
                if (!transactions.isArray() || transactions.size() != 1) {
                    return new PaymentCaptureResult(false, null, amount, "VND", "UNKNOWN",
                            "A single payOS transfer is required for automatic reconciliation");
                }
                JsonNode transfer = transactions.get(0);
                return new PaymentCaptureResult(true, transfer.path("reference").asText(null), amount, "VND", "COMPLETED",
                        "Payment completed on payOS", parsePaymentTime(transfer.path("transactionDateTime").asText()));
            } else if ("CANCELLED".equalsIgnoreCase(status) && amount != null && amount.signum() == 0) {
                return new PaymentCaptureResult(false, providerOrderId, amount, "VND", "CANCELLED", "Payment cancelled on payOS");
            }
            return new PaymentCaptureResult(false, providerOrderId, amount, "VND", "PENDING", "Payment pending on payOS");
        } catch (Exception ex) {
            log.error("Failed to query payOS payment for orderCode={}", providerOrderId, ex);
            return new PaymentCaptureResult(false, null, null, "VND", "UNKNOWN", ex.getMessage());
        }
    }

    public static OffsetDateTime parsePaymentTime(String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    @Override
    public RefundResult requestRefund(String providerTransactionId, BigDecimal amount) {
        return new RefundResult(false, "MANUAL_REVIEW_REQUIRED", null, "payOS does not support automated API refund");
    }
}

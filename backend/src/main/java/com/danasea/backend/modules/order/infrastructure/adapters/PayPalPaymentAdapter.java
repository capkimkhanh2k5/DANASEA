package com.danasea.backend.modules.order.infrastructure.adapters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import com.danasea.backend.modules.order.application.services.ExchangeRateService;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.danasea.backend.configs.properties.PayPalProperties;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundRequest;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.ports.RefundResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Adapter triển khai cổng thanh toán quốc tế PayPal REST API v2.
 * Hỗ trợ OAuth2 Bearer Token, Order Capture URL, Webhook verification và Refund.
 */
@Component
@Primary
@Slf4j
public class PayPalPaymentAdapter implements PaymentGatewayPort {

    private static final BigDecimal MIN_USD_AMOUNT = new BigDecimal("1.00");

    private final RestClient restClient;
    private final PayPalProperties payPalProperties;
    private final ObjectMapper objectMapper;
    private final VNPayPaymentAdapter vnPayPaymentAdapter;
    private final ExchangeRateService exchangeRateService;

    @Autowired
    public PayPalPaymentAdapter(
            RestClient.Builder restClientBuilder,
            PayPalProperties payPalProperties,
            ObjectMapper objectMapper,
            VNPayPaymentAdapter vnPayPaymentAdapter,
            ExchangeRateService exchangeRateService) {
        this.payPalProperties = payPalProperties;
        this.objectMapper = objectMapper;
        this.vnPayPaymentAdapter = vnPayPaymentAdapter;
        this.exchangeRateService = exchangeRateService;
        this.restClient = restClientBuilder
                .baseUrl(payPalProperties.baseUrl())
                .build();
    }

    public PayPalPaymentAdapter(
            RestClient.Builder restClientBuilder,
            PayPalProperties payPalProperties,
            ObjectMapper objectMapper,
            VNPayPaymentAdapter vnPayPaymentAdapter) {
        this(restClientBuilder, payPalProperties, objectMapper, vnPayPaymentAdapter, new ExchangeRateService() {
            @Override
            public ExchangeRateSnapshot getCurrentUsdToVndRate() {
                return new ExchangeRateSnapshot(new BigDecimal("25900"), LocalDate.now(), "FALLBACK");
            }
            @Override
            public ExchangeRateSnapshot syncRateFromFrankfurter() {
                return getCurrentUsdToVndRate();
            }
            @Override
            public void initializeRateOnStartup() {}
        });
    }

    @Override
    public boolean supportsProvider(PaymentProvider provider) {
        return provider == PaymentProvider.PAYPAL;
    }

    /**
     * Lấy OAuth2 Access Token từ PayPal Client Credentials.
     */
    public String fetchAccessToken() {
        try {
            String credentials = payPalProperties.clientId() + ":" + payPalProperties.clientSecret();
            String encodedAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            String responseBody = restClient.post()
                    .uri("/v1/oauth2/token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("access_token")) {
                return root.get("access_token").asText();
            }
        } catch (Exception ex) {
            throw new PaymentGatewayException("PayPal authentication failed.", ex);
        }
        throw new PaymentGatewayException("PayPal did not return an access token.");
    }

    @Override
    public PaymentIntentResult createPaymentIntent(UUID paymentId, UUID orderId, BigDecimal amount, PaymentProvider provider) {
        if (provider == PaymentProvider.VNPAY) {
            return vnPayPaymentAdapter.createPaymentIntent(paymentId, orderId, amount, provider);
        }

        if (provider != PaymentProvider.PAYPAL) {
            throw new InvalidOrderStateException("The selected payment provider is not supported.");
        }
        UUID effectivePaymentId = paymentId != null ? paymentId : UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(15);

        ExchangeRateService.ExchangeRateSnapshot rateSnapshot = exchangeRateService.getCurrentUsdToVndRate();
        BigDecimal exchangeRate = rateSnapshot.rate();
        LocalDate rateDate = rateSnapshot.effectiveDate();
        String rateSource = rateSnapshot.source();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderStateException("The order amount must be positive.");
        }

        BigDecimal usdAmount = amount.divide(exchangeRate, 2, RoundingMode.HALF_UP);
        if (usdAmount.compareTo(MIN_USD_AMOUNT) < 0) {
            throw new InvalidOrderStateException(
                    "The order amount is below the minimum threshold ($1.00 USD) supported by PayPal.");
        }

        String approveUrl = null;
        String payPalOrderId = null;

        try {
            String token = fetchAccessToken();
            if (token != null && !token.isBlank()) {
                Map<String, String> returnParams = Map.of("orderId", orderId.toString(), "paymentId", effectivePaymentId.toString());
                Map<String, Object> orderPayload = Map.of(
                        "intent", "CAPTURE",
                        "purchase_units", List.of(
                                Map.of(
                                        "reference_id", effectivePaymentId.toString(),
                                        "custom_id", effectivePaymentId.toString(),
                                        "description", "Danasea Master Order " + orderId,
                                        "amount", Map.of(
                                                "currency_code", "USD",
                                                "value", usdAmount.toPlainString()
                                        )
                                )
                        ),
                        "application_context", Map.of(
                                "brand_name", "DANASea Experience",
                                "user_action", "PAY_NOW",
                                "return_url", PaymentUrlHelper.appendQueryParams(payPalProperties.returnUrl(), returnParams),
                                "cancel_url", PaymentUrlHelper.appendQueryParams(payPalProperties.cancelUrl(), returnParams)
                        )
                );

                String responseBody = restClient.post()
                        .uri("/v2/checkout/orders")
                        .header("PayPal-Request-Id", effectivePaymentId.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(orderPayload)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("id")) {
                    payPalOrderId = root.path("id").asText(null);
                }
                if (root.has("links") && root.get("links").isArray()) {
                    for (JsonNode link : root.get("links")) {
                        if ("approve".equalsIgnoreCase(link.path("rel").asText())) {
                            approveUrl = link.path("href").asText();
                            break;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new PaymentGatewayException("PayPal order creation failed.", ex);
        }

        if (approveUrl == null || approveUrl.isBlank() || payPalOrderId == null || payPalOrderId.isBlank()) {
            throw new PaymentGatewayException("PayPal did not return an approval URL.");
        }

        // qrCodeUrl trả về chính approveUrl để Client-side (Frontend) tự render mã QR an toàn
        String qrCodeUrl = approveUrl;

        return new PaymentIntentResult(
                effectivePaymentId,
                orderId,
                PaymentProvider.PAYPAL,
                amount,
                approveUrl,
                qrCodeUrl,
                expiresAt,
                payPalOrderId,
                usdAmount,
                "USD",
                null,
                exchangeRate,
                rateDate,
                rateSource
        );
    }

    @Override
    public boolean verifyWebhookSignature(Map<String, String> rawParams, String signature) {
        if (signature == null || signature.isBlank() || rawParams == null || rawParams.isEmpty()) {
            return false;
        }

        // 1. Kiểm tra nếu là Webhook từ VNPay
        if (rawParams.keySet().stream().anyMatch(k -> k.startsWith("vnp_"))) {
            return vnPayPaymentAdapter.verifyWebhookSignature(rawParams, signature);
        }

        // 2. Kiểm tra nếu có headers truyền tin của PayPal Webhook
        String transmissionId = rawParams.getOrDefault("paypal-transmission-id", rawParams.get("transmission_id"));
        String transmissionTime = rawParams.getOrDefault("paypal-transmission-time", rawParams.get("transmission_time"));
        String certUrl = rawParams.getOrDefault("paypal-cert-url", rawParams.get("cert_url"));
        String authAlgo = rawParams.getOrDefault("paypal-auth-algo", rawParams.get("auth_algo"));
        String webhookId = payPalProperties.webhookId();

        if (transmissionId != null && transmissionTime != null && certUrl != null && authAlgo != null
                && webhookId != null && !webhookId.isBlank() && rawParams.containsKey("rawPayload")) {
            try {
                String token = fetchAccessToken();
                if (token != null && !token.isBlank()) {
                    Map<String, Object> verifyPayload = Map.of(
                            "auth_algo", authAlgo,
                            "cert_url", certUrl,
                            "transmission_id", transmissionId,
                            "transmission_sig", signature,
                            "transmission_time", transmissionTime,
                            "webhook_id", webhookId,
                            "webhook_event", objectMapper.readValue(rawParams.get("rawPayload"), new TypeReference<Map<String, Object>>() { })
                    );

                    String response = restClient.post()
                            .uri("/v1/notifications/verify-webhook-signature")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(verifyPayload)
                            .retrieve()
                            .body(String.class);

                    JsonNode root = objectMapper.readTree(response);
                    return "SUCCESS".equalsIgnoreCase(root.path("verification_status").asText());
                }
            } catch (Exception e) {
                log.warn("PayPal live webhook verification failed: {}", e.getMessage());
            }
        }

        return false;
    }

    @Override
    public PaymentCaptureResult captureOrder(PaymentProvider provider, String providerOrderId, String requestId) {
        requirePayPal(provider);
        if (requestId == null || requestId.isBlank()) {
            throw new PaymentGatewayException("A persisted capture request ID is required.");
        }
        try {
            String body = restClient.post().uri("/v2/checkout/orders/{id}/capture", providerOrderId)
                    .header("PayPal-Request-Id", requestId)
                    .header("Prefer", "return=representation")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fetchAccessToken())
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of()).retrieve().body(String.class);
            return captureResult(objectMapper.readTree(body), providerOrderId);
        } catch (Exception ex) {
            if (isTimeoutException(ex)) {
                throw new PaymentGatewayException("GATEWAY_TIMEOUT_AWAITING_VERIFICATION", ex);
            }
            if (ex instanceof RestClientResponseException response
                    && response.getStatusCode().value() == 422
                    && response.getResponseBodyAsString().contains("ORDER_ALREADY_CAPTURED")) {
                return queryCapture(provider, providerOrderId);
            }
            throw new PaymentGatewayException("PayPal capture failed.", ex);
        }
    }

    @Override
    public PaymentCaptureResult queryPayment(PaymentProvider provider, String providerOrderId, String transactionDate) {
        if (provider == PaymentProvider.VNPAY) {
            return vnPayPaymentAdapter.queryPayment(provider, providerOrderId, transactionDate);
        }
        return queryCapture(provider, providerOrderId);
    }

    @Override
    public PaymentCaptureResult queryCapture(PaymentProvider provider, String providerOrderId) {
        requirePayPal(provider);
        try {
            String body = restClient.get().uri("/v2/checkout/orders/{id}", providerOrderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fetchAccessToken())
                    .retrieve().body(String.class);
            return captureResult(objectMapper.readTree(body), providerOrderId);
        } catch (Exception ex) {
            throw new PaymentGatewayException("Capture reconciliation is unavailable.", ex);
        }
    }

    private PaymentCaptureResult captureResult(JsonNode root, String providerOrderId) {
        if (!providerOrderId.equals(root.path("id").asText())
                || (root.path("purchase_units").isArray() && root.path("purchase_units").size() != 1)) {
            throw new PaymentGatewayException("PayPal order identity does not match the persisted intent.");
        }
        JsonNode captures = root.path("purchase_units").path(0).path("payments").path("captures");
        if (!captures.isArray() || captures.size() != 1) {
            return new PaymentCaptureResult(false, null, null, null, root.path("status").asText("UNKNOWN"),
                    "No single capture is available for verification.");
        }
        JsonNode capture = captures.get(0);
        String id = capture.path("id").asText(null);
        String status = capture.path("status").asText("UNKNOWN");
        BigDecimal amount = capture.path("amount").hasNonNull("value")
                ? new BigDecimal(capture.path("amount").path("value").asText()) : null;
        return new PaymentCaptureResult("COMPLETED".equals(status) && id != null && !id.isBlank(), id,
                amount, capture.path("amount").path("currency_code").asText(null), status,
                "PayPal capture status: " + status, parsePaymentTime(capture.path("create_time").asText()));
    }

    public static OffsetDateTime parsePaymentTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    @Override
    public RefundResult requestRefund(GatewayRefundRequest request) {
        if (request.provider() == PaymentProvider.VNPAY) {
            return vnPayPaymentAdapter.requestRefund(request);
        }
        requirePayPal(request.provider());
        if (request.transactionId() == null || request.transactionId().isBlank()
                || request.requestId() == null || request.requestId().isBlank()
                || request.amount() == null || request.amount().signum() <= 0 || request.currency() == null) {
            throw new PaymentGatewayException("Persisted refund identity, capture ID and money are required.");
        }
        try {
            String body = restClient.post().uri("/v2/payments/captures/{id}/refund", request.transactionId())
                    .header("PayPal-Request-Id", request.requestId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fetchAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", Map.of("value", request.amount().toPlainString(), "currency_code", request.currency()),
                            "invoice_id", request.requestId(), "note_to_payer", "Refund for Danasea Order"))
                    .retrieve().body(String.class);
            return refundResult(objectMapper.readTree(body), request);
        } catch (Exception ex) {
            throw new PaymentGatewayException(isTimeoutException(ex)
                    ? "GATEWAY_TIMEOUT_AWAITING_VERIFICATION" : "PayPal refund response could not be verified.", ex);
        }
    }

    @Override
    public RefundResult queryRefund(GatewayRefundRequest request, String providerRefundId) {
        if (request.provider() == PaymentProvider.VNPAY) {
            return vnPayPaymentAdapter.queryRefund(request, providerRefundId);
        }
        requirePayPal(request.provider());
        if (providerRefundId == null || providerRefundId.isBlank()) {
            return new RefundResult(false, null, null, "REFUND_ID_UNAVAILABLE", GatewayRefundStatus.UNKNOWN, null);
        }
        try {
            String body = restClient.get().uri("/v2/payments/refunds/{id}", providerRefundId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fetchAccessToken()).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!providerRefundId.equals(root.path("id").asText())) {
                throw new PaymentGatewayException("The provider refund ID does not match the requested refund.");
            }
            return refundResult(root, request);
        } catch (Exception ex) {
            throw new PaymentGatewayException("Refund reconciliation is unavailable.", ex);
        }
    }

    private RefundResult refundResult(JsonNode root, GatewayRefundRequest request) {
        String id = root.path("id").asText(null);
        if (id == null || id.isBlank() || !root.path("amount").hasNonNull("value")) {
            throw new PaymentGatewayException("PayPal returned incomplete refund data.");
        }
        BigDecimal amount = new BigDecimal(root.path("amount").path("value").asText());
        String currency = root.path("amount").path("currency_code").asText();
        if (amount.compareTo(request.amount()) != 0 || !request.currency().equals(currency)) {
            throw new PaymentGatewayException("PayPal refund money does not match the persisted request.");
        }
        if (root.hasNonNull("invoice_id") && !request.requestId().equals(root.path("invoice_id").asText())) {
            throw new PaymentGatewayException("PayPal refund operation does not match the persisted request.");
        }
        for (JsonNode link : root.path("links")) {
            if ("up".equals(link.path("rel").asText())
                    && !link.path("href").asText().endsWith("/captures/" + request.transactionId())) {
                throw new PaymentGatewayException("PayPal refund belongs to another capture.");
            }
        }
        String status = root.path("status").asText("UNKNOWN");
        GatewayRefundStatus state = switch (status) {
            case "COMPLETED" -> GatewayRefundStatus.COMPLETED;
            case "PENDING" -> GatewayRefundStatus.PENDING;
            case "FAILED", "CANCELLED" -> GatewayRefundStatus.FAILED;
            default -> GatewayRefundStatus.UNKNOWN;
        };
        return new RefundResult(state == GatewayRefundStatus.COMPLETED, id, amount, "PayPal refund status: " + status, state, currency);
    }

    @Override
    public RefundResult requestRefund(PaymentProvider provider, String transactionId, BigDecimal amount, String requestId) {
        throw new PaymentGatewayException("Persisted original gateway money and operation context are required.");
    }

    @Override
    public RefundResult requestRefund(PaymentProvider provider, String transactionId, BigDecimal amount) {
        throw new PaymentGatewayException("A persisted refund request ID and original gateway context are required.");
    }

    @Override
    public RefundResult requestRefund(String transactionId, BigDecimal amount) {
        throw new PaymentGatewayException("The original gateway context is required for refund.");
    }

    @Override
    public RefundResult queryRefund(PaymentProvider provider, String transactionId, String refundId) {
        if (provider == PaymentProvider.VNPAY) {
            return vnPayPaymentAdapter.queryRefund(provider, transactionId, refundId);
        }
        throw new PaymentGatewayException("Persisted refund money is required for reconciliation.");
    }

    private void requirePayPal(PaymentProvider provider) {
        if (provider != PaymentProvider.PAYPAL) {
            throw new PaymentGatewayException("The selected provider does not support this PayPal operation.");
        }
    }

    private boolean isTimeoutException(Throwable ex) {
        if (ex == null) return false;
        if (ex instanceof ResourceAccessException) return true;
        if (ex instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status >= 500 || status == 408) return true;
        }
        return isTimeoutException(ex.getCause());
    }
}

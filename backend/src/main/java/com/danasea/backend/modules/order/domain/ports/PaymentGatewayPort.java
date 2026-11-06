package com.danasea.backend.modules.order.domain.ports;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.models.PaymentProvider;

/**
 * Domain Port giao tiếp với các cổng thanh toán bên ngoài (PayPal, VNPay, MoMo, payOS, ZaloPay).
 * Hỗ trợ tạo Payment Intent, xác thực webhook signature, truy vấn, hủy link và hoàn tiền.
 */
public interface PaymentGatewayPort {

    PaymentIntentResult createPaymentIntent(UUID paymentId, UUID orderId, BigDecimal amount, PaymentProvider provider);

    default PaymentIntentResult createPaymentIntent(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            PaymentProvider provider,
            Long specificOrderCode,
            java.time.OffsetDateTime expiresAt) {
        return createPaymentIntent(paymentId, orderId, amount, provider);
    }

    default PaymentIntentResult createPaymentIntent(UUID orderId, BigDecimal amount, PaymentProvider provider) {
        return createPaymentIntent(UUID.randomUUID(), orderId, amount, provider);
    }

    boolean verifyWebhookSignature(Map<String, String> rawParams, String signature);

    RefundResult requestRefund(String providerTransactionId, BigDecimal amount);

    default RefundResult requestRefund(PaymentProvider provider, String providerTransactionId, BigDecimal amount) {
        return requestRefund(providerTransactionId, amount);
    }

    default RefundResult requestRefund(PaymentProvider provider, String providerTransactionId, BigDecimal amount, String idempotencyKey) {
        return requestRefund(provider, providerTransactionId, amount);
    }

    default PaymentCaptureResult captureOrder(PaymentProvider provider, String providerOrderId, String idempotencyKey) {
        throw new UnsupportedOperationException("Capture is not supported for provider: " + provider);
    }

    default PaymentCaptureResult queryCapture(PaymentProvider provider, String providerOrderId) {
        throw new UnsupportedOperationException("Capture lookup is not supported for provider: " + provider);
    }

    default PaymentCaptureResult queryPayment(PaymentProvider provider, String providerOrderId, String transactionDate) {
        return queryCapture(provider, providerOrderId);
    }

    default RefundResult requestRefund(GatewayRefundRequest request) {
        throw new UnsupportedOperationException("Refund context is not supported for provider: " + request.provider());
    }

    default RefundResult queryRefund(GatewayRefundRequest request, String providerRefundId) {
        throw new UnsupportedOperationException("Refund lookup is not supported for provider: " + request.provider());
    }

    default RefundResult queryRefund(PaymentProvider provider, String providerTransactionId, String providerRefundId) {
        throw new UnsupportedOperationException("Query refund is not supported for provider: " + provider);
    }

    default PaymentCancelResult cancelPayment(PaymentProvider provider, String providerOrderId, String reason) {
        return PaymentCancelResult.failed("Cancellation not supported for provider: " + provider, "UNSUPPORTED");
    }

    default boolean canCancelServerSide(PaymentProvider provider) {
        return false;
    }

    default boolean supportsProvider(PaymentProvider provider) {
        return false;
    }
}

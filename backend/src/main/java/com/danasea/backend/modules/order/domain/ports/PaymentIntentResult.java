package com.danasea.backend.modules.order.domain.ports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.models.PaymentProvider;

public record PaymentIntentResult(
        UUID paymentId,
        UUID orderId,
        PaymentProvider provider,
        BigDecimal amount,
        String paymentUrl,
        String qrCodeUrl,
        OffsetDateTime expiresAt,
        String providerOrderId,
        BigDecimal providerAmount,
        String providerCurrency,
        String providerTransactionDate,
        BigDecimal exchangeRate,
        LocalDate exchangeRateDate,
        String exchangeRateSource
) {
    public PaymentIntentResult(
            UUID paymentId,
            UUID orderId,
            PaymentProvider provider,
            BigDecimal amount,
            String paymentUrl,
            String qrCodeUrl,
            OffsetDateTime expiresAt) {
        this(paymentId, orderId, provider, amount, paymentUrl, qrCodeUrl, expiresAt, null, null, null, null, null, null, null);
    }

    public PaymentIntentResult(
            UUID paymentId,
            UUID orderId,
            PaymentProvider provider,
            BigDecimal amount,
            String paymentUrl,
            String qrCodeUrl,
            OffsetDateTime expiresAt,
            String providerOrderId) {
        this(paymentId, orderId, provider, amount, paymentUrl, qrCodeUrl, expiresAt, providerOrderId, null, null, null, null, null, null);
    }

    public PaymentIntentResult(
            UUID paymentId,
            UUID orderId,
            PaymentProvider provider,
            BigDecimal amount,
            String paymentUrl,
            String qrCodeUrl,
            OffsetDateTime expiresAt,
            String providerOrderId,
            BigDecimal providerAmount,
            String providerCurrency,
            String providerTransactionDate) {
        this(paymentId, orderId, provider, amount, paymentUrl, qrCodeUrl, expiresAt, providerOrderId, providerAmount,
                providerCurrency, providerTransactionDate, null, null, null);
    }
}

package com.danasea.backend.modules.order.domain.models;

import java.math.BigDecimal;
import java.util.UUID;

import com.danasea.backend.shared.core.domain.models.BaseDomainModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class Payment extends BaseDomainModel {
    private UUID masterOrderId;
    private PaymentProvider provider;
    private String providerTransactionId;
    private String idempotencyKey;
    private String webhookEventId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String rawWebhookPayload;
    private String paymentUrl;
    private String qrCodeUrl;
    private OffsetDateTime expiresAt;
    private BigDecimal exchangeRate;
    private LocalDate exchangeRateDate;
    private String exchangeRateSource;
}

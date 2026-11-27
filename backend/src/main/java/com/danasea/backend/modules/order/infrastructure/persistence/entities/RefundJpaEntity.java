package com.danasea.backend.modules.order.infrastructure.persistence.entities;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "refunds")
public class RefundJpaEntity extends BaseJpaEntity {

    private UUID paymentId;
    @Column(precision = 20, scale = 2)
    private BigDecimal providerAmount;
    @Column(length = 3)
    private String providerCurrency;
    @Column(length = 100)
    private String gatewayRequestId;
    private OffsetDateTime gatewayRequestedAt;
    private OffsetDateTime nextAttemptAt;
    @Column(nullable = false)
    private Integer verificationAttempts = 0;

    private UUID subOrderId;

    private BigDecimal amount;

    private BigDecimal refundPercentage;

    @Enumerated(EnumType.STRING)
    private RefundReason reason;

    @Enumerated(EnumType.STRING)
    private RefundStatus status;

    private OffsetDateTime processedAt;

    private UUID requestedBy;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "provider_refund_id", length = 150)
    private String providerRefundId;

    @Enumerated(EnumType.STRING)
    private PaymentProvider provider;

    @Column(name = "webhook_event_id", length = 150)
    private String webhookEventId;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

}

package com.danasea.backend.modules.order.infrastructure.persistence.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.models.PaymentAttemptState;
import com.danasea.backend.modules.order.domain.models.PaymentCancelStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "payments")
public class PaymentJpaEntity extends BaseJpaEntity {

    @Column(name = "exchange_rate", precision = 15, scale = 6)
    private BigDecimal exchangeRate;
    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;
    @Column(name = "exchange_rate_source", length = 50)
    private String exchangeRateSource;

    @Column(precision = 20, scale = 2)
    private BigDecimal providerAmount;
    @Column(length = 3)
    private String providerCurrency;
    @Column(length = 14)
    private String providerTransactionDate;
    @Column(length = 100)
    private String captureRequestId;
    private OffsetDateTime captureRequestedAt;
    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false)
    private Integer reconciliationAttempts = 0;
    private OffsetDateTime reconciliationNextAttemptAt;
    private OffsetDateTime lastReconciledAt;

    private UUID masterOrderId;

    @Enumerated(EnumType.STRING)
    private PaymentProvider provider;

    private String providerTransactionId;

    @Column(name = "provider_order_id", length = 100)
    private String providerOrderId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "webhook_event_id", length = 150)
    private String webhookEventId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private OffsetDateTime paidAt;

    public void setStatus(PaymentStatus status) {
        if (status == PaymentStatus.SUCCESS && paidAt == null) {
            paidAt = OffsetDateTime.now();
        }
        this.status = status;
    }

    @Column(columnDefinition = "TEXT")
    private String rawWebhookPayload;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @Column(name = "qr_code_url", columnDefinition = "TEXT")
    private String qrCodeUrl;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    // --- 5+5 Multi-Attempt Tracking Fields ---
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_state", nullable = false, length = 30)
    private PaymentAttemptState attemptState = PaymentAttemptState.PENDING;

    @Column(name = "danasea_deadline_at")
    private OffsetDateTime danaseaDeadlineAt;

    @Column(name = "provider_expires_at")
    private OffsetDateTime providerExpiresAt;

    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_status", nullable = false, length = 30)
    private PaymentCancelStatus cancelStatus = PaymentCancelStatus.NONE;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Column(name = "is_canonical", nullable = false)
    private Boolean isCanonical = false;

    @Column(name = "payos_order_code", unique = true)
    private Long payosOrderCode;

    @PrePersist
    @PreUpdate
    public void syncAttemptStateWithStatus() {
        if (this.attemptState == null || this.attemptState == PaymentAttemptState.PENDING) {
            if (this.status == PaymentStatus.FAILED) {
                this.attemptState = PaymentAttemptState.FAILED;
            } else if (this.status == PaymentStatus.SUCCESS) {
                this.attemptState = PaymentAttemptState.COMPLETED;
            }
        }
    }
}

package com.danasea.backend.modules.order.presentation.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;

public record RefundDetailResponse(
        UUID id,
        UUID subOrderId,
        BigDecimal amount,
        BigDecimal percentage,
        RefundReason reason,
        RefundStatus status,
        PaymentProvider provider,
        String providerRefundId,
        String providerTransactionId,
        Integer retryCount,
        String lastError,
        OffsetDateTime processedAt,
        OffsetDateTime createdAt,
        Integer verificationAttempts,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime gatewayRequestedAt,
        UUID paymentId,
        BigDecimal providerAmount,
        String providerCurrency
) {
    public RefundDetailResponse(
            UUID id,
            UUID subOrderId,
            BigDecimal amount,
            BigDecimal percentage,
            RefundReason reason,
            RefundStatus status,
            PaymentProvider provider,
            String providerRefundId,
            String providerTransactionId,
            Integer retryCount,
            String lastError,
            OffsetDateTime processedAt,
            OffsetDateTime createdAt) {
        this(id, subOrderId, amount, percentage, reason, status, provider, providerRefundId, providerTransactionId,
                retryCount, lastError, processedAt, createdAt, null, null, null, null, null, null);
    }

    public static RefundDetailResponse fromEntity(RefundJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RefundDetailResponse(
                entity.getId(),
                entity.getSubOrderId(),
                entity.getAmount(),
                entity.getRefundPercentage(),
                entity.getReason(),
                entity.getStatus(),
                entity.getProvider(),
                entity.getProviderRefundId(),
                entity.getProviderTransactionId(),
                entity.getRetryCount(),
                entity.getLastError(),
                entity.getProcessedAt(),
                entity.getCreatedAt(),
                entity.getVerificationAttempts(),
                entity.getNextAttemptAt(),
                entity.getGatewayRequestedAt(),
                entity.getPaymentId(),
                entity.getProviderAmount(),
                entity.getProviderCurrency()
        );
    }
}

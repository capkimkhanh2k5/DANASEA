package com.danasea.backend.modules.order.presentation.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.models.PaymentAttemptState;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;

/**
 * Phản hồi chi tiết cho Payment Intent trong cửa sổ 5+5.
 * Trả về thông tin link/QR, attempt hiện tại, và thời hạn thanh toán không bị reset.
 */
public record PaymentWindowResponse(
        UUID paymentId,
        UUID orderId,
        PaymentProvider provider,
        BigDecimal amount,
        PaymentStatus status,
        PaymentAttemptState attemptState,
        Integer attemptNumber,
        String paymentUrl,
        String qrCodeUrl,
        OffsetDateTime danaseaDeadlineAt,
        Long remainingSeconds,
        OffsetDateTime providerExpiresAt
) {}

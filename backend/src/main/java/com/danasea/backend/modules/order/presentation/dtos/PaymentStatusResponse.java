package com.danasea.backend.modules.order.presentation.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentAttemptState;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;

/**
 * Phản hồi trạng thái thanh toán và đồng hồ đếm ngược của đơn hàng.
 */
public record PaymentStatusResponse(
        UUID orderId,
        MasterOrderStatus orderStatus,
        PaymentOrderStatus paymentStatus,
        OffsetDateTime danaseaDeadlineAt,
        Long remainingSeconds,
        boolean isExpired,
        UUID activePaymentId,
        PaymentProvider activeProvider,
        PaymentAttemptState activeAttemptState,
        Integer activeAttemptNumber,
        String paymentUrl,
        String qrCodeUrl
) {}

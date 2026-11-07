package com.danasea.backend.modules.order.presentation.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;

public record OrderResponse(
        UUID id,
        UUID bookingId,
        UUID customerId,
        MasterOrderStatus status,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        List<SubOrderResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime paymentDeadline
) {
}

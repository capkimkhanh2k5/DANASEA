package com.danasea.backend.modules.order.application.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.shared.util.DisplayCodeFormatter;

public record MasterOrderDetailResult(
        UUID id,
        UUID bookingId,
        UUID customerId,
        MasterOrderStatus status,
        PaymentOrderStatus paymentStatus,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        UUID discountCodeId,
        OffsetDateTime paymentDeadline,
        String idempotencyKey,
        OffsetDateTime createdAt,
        List<SubOrderDetailResult> subOrders,
        String orderCode
) {
    public MasterOrderDetailResult(
            UUID id,
            UUID bookingId,
            UUID customerId,
            MasterOrderStatus status,
            PaymentOrderStatus paymentStatus,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            UUID discountCodeId,
            OffsetDateTime paymentDeadline,
            String idempotencyKey,
            OffsetDateTime createdAt,
            List<SubOrderDetailResult> subOrders
    ) {
        this(id, bookingId, customerId, status, paymentStatus, totalAmount, discountAmount,
                discountCodeId, paymentDeadline, idempotencyKey, createdAt, subOrders,
                DisplayCodeFormatter.format(id, "ORD"));
    }

    public static MasterOrderDetailResult fromDomain(MasterOrder order) {
        if (order == null) {
            return null;
        }
        List<SubOrderDetailResult> subOrderResults = order.getSubOrders() != null
                ? order.getSubOrders().stream().map(SubOrderDetailResult::fromDomain).toList()
                : Collections.emptyList();
        String effectiveOrderCode = order.getOrderCode() != null && !order.getOrderCode().isBlank()
                ? order.getOrderCode()
                : DisplayCodeFormatter.format(order.getId(), "ORD");

        return new MasterOrderDetailResult(
                order.getId(),
                order.getBookingId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getDiscountCodeId(),
                order.getPaymentDeadline(),
                order.getIdempotencyKey(),
                order.getCreatedAt(),
                subOrderResults,
                effectiveOrderCode
        );
    }
}

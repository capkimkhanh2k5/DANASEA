package com.danasea.backend.modules.report.application.ports;

import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Projection record chứa thông tin SubOrder phục vụ thống kê báo cáo. */
public record SubOrderRecord(
        UUID id,
        UUID masterOrderId,
        UUID vendorId,
        UUID serviceId,
        UUID slotId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotalAmount,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal vendorPayoutAmount,
        SubOrderStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        RefundReason cancellationReason,
        BigDecimal finalAmount,
        BigDecimal platformDiscountAmount,
        BigDecimal commissionBasisAmount) {
    public SubOrderRecord(UUID id,UUID masterOrderId,UUID vendorId,UUID serviceId,UUID slotId,Integer quantity,
            BigDecimal unitPrice,BigDecimal subtotalAmount,BigDecimal commissionRate,BigDecimal commissionAmount,
            BigDecimal vendorPayoutAmount,SubOrderStatus status,OffsetDateTime createdAt,OffsetDateTime updatedAt,
            RefundReason cancellationReason) {
        this(id,masterOrderId,vendorId,serviceId,slotId,quantity,unitPrice,subtotalAmount,commissionRate,commissionAmount,
                vendorPayoutAmount,status,createdAt,updatedAt,cancellationReason,null,null,null);
    }
    public SubOrderRecord(
            UUID id,
            UUID masterOrderId,
            UUID vendorId,
            UUID serviceId,
            UUID slotId,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotalAmount,
            BigDecimal commissionRate,
            BigDecimal commissionAmount,
            BigDecimal vendorPayoutAmount,
            SubOrderStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this(
                id,
                masterOrderId,
                vendorId,
                serviceId,
                slotId,
                quantity,
                unitPrice,
                subtotalAmount,
                commissionRate,
                commissionAmount,
                vendorPayoutAmount,
                status,
                createdAt,
                updatedAt,
                null);
    }
}

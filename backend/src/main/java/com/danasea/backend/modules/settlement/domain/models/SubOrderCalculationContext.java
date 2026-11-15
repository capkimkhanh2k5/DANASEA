package com.danasea.backend.modules.settlement.domain.models;

import java.math.BigDecimal;
import java.util.UUID;

import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubOrderCalculationContext {
    private UUID subOrderId;
    private SubOrderStatus status;
    private BigDecimal subtotalAmount;
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;
    private BigDecimal refundPercentage;
    private boolean hasActiveDispute;
    private DisputeStatus disputeStatus;
    private boolean disputeWindowOpen;
    private boolean hasPendingRefund;
    private boolean hasActiveDisruption;
    private BigDecimal commissionRate;

    private BigDecimal vendorDiscountAmount;
    private BigDecimal platformDiscountAmount;
    private BigDecimal commissionBasisAmount;
    private BigDecimal finalAmount;

    public BigDecimal getEffectiveCommissionBasis() {
        if (commissionBasisAmount != null) {
            return commissionBasisAmount;
        }
        if (subtotalAmount != null) {
            BigDecimal vd = vendorDiscountAmount != null ? vendorDiscountAmount : BigDecimal.ZERO;
            return subtotalAmount.subtract(vd).max(BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }
    public BigDecimal getEffectiveFinalAmount() {
        if (finalAmount != null) {
            return finalAmount;
        }
        BigDecimal subtotal = subtotalAmount != null ? subtotalAmount : BigDecimal.ZERO;
        BigDecimal vendor = vendorDiscountAmount != null ? vendorDiscountAmount : BigDecimal.ZERO;
        BigDecimal platform = platformDiscountAmount != null ? platformDiscountAmount : BigDecimal.ZERO;
        return subtotal.subtract(vendor).subtract(platform).max(BigDecimal.ZERO);
    }

}

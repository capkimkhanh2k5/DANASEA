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

    /**
     * Vendor net revenue before commission deduction.
     * - Regular orders (K1): commissionBasis == subtotal - vendorDiscount -> identical to basis.
     * - Stacked Flash Sale + Vendor Voucher orders (K2): commissionBasis == subtotal while vendor still absorbs vendorDiscount,
     *   so vendor net revenue before commission is (subtotal - vendorDiscount).
     */
    public BigDecimal getEffectiveVendorNetRevenue() {
        BigDecimal basis = getEffectiveCommissionBasis();
        if (subtotalAmount == null) {
            return basis;
        }
        BigDecimal vd = vendorDiscountAmount != null ? vendorDiscountAmount : BigDecimal.ZERO;
        BigDecimal netAfterVendorDiscount = subtotalAmount.subtract(vd).max(BigDecimal.ZERO);
        return basis.min(netAfterVendorDiscount);
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

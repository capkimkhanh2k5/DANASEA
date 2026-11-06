package com.danasea.backend.modules.settlement.domain.models;

import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyFinalizedException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {
    private UUID id;
    private UUID vendorId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal totalGrossRevenue;
    private BigDecimal totalCommission;
    private BigDecimal totalNetPayout;
    @Builder.Default
    private BigDecimal vendorFaultCompensationDeductedAmount = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal safetyRebateRate = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal safetyRebateAmount = BigDecimal.ZERO;
    @Builder.Default
    private Boolean safetyRebateQualified = false;
    private String safetyRebateNote;
    private SettlementStatus status;
    private OffsetDateTime generatedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    
    @Builder.Default
    private List<SettlementLineItem> lineItems = new ArrayList<>();

    public boolean isImmutable() {
        return status != null && status.isImmutable();
    }

    public boolean canBeRegenerated() {
        return status == null || status.canRegenerate();
    }

    public void finalizeSettlement() {
        if (this.status != null && this.status.isFinalized()) {
            throw new SettlementAlreadyFinalizedException(
                    "Settlement is already finalized or paid for vendor " + vendorId + " and period " + periodStart + " to " + periodEnd);
        }
        this.status = SettlementStatus.FINALIZED;
    }

    public void markAsPaid() {
        if (this.status != SettlementStatus.FINALIZED) {
            throw new IllegalStateException("Only FINALIZED settlements can be marked as PAID. Current status: " + this.status);
        }
        this.status = SettlementStatus.PAID;
    }

    public void addLineItem(SettlementLineItem item) {
        if (this.lineItems == null) {
            this.lineItems = new ArrayList<>();
        }
        if (this.id != null) {
            item.setSettlementId(this.id);
        }
        this.lineItems.add(item);
    }

    public void calculateTotals() {
        BigDecimal sumGross = BigDecimal.ZERO;
        BigDecimal sumCommission = BigDecimal.ZERO;
        BigDecimal sumPayout = BigDecimal.ZERO;

        if (lineItems != null) {
            for (SettlementLineItem item : lineItems) {
                if (!item.isExcluded()) {
                    sumGross = sumGross.add(item.getGrossAmount() != null ? item.getGrossAmount() : BigDecimal.ZERO);
                    sumCommission = sumCommission.add(item.getCommissionAmount() != null ? item.getCommissionAmount() : BigDecimal.ZERO);
                    sumPayout = sumPayout.add(item.getNetAmount() != null ? item.getNetAmount() : BigDecimal.ZERO);
                }
            }
        }

        this.totalGrossRevenue = sumGross;
        this.totalCommission = sumCommission;
        this.totalNetPayout = sumPayout;
    }

    // Compatibility aliases with DB columns / legacy code
    public BigDecimal getGrossAmount() {
        return totalGrossRevenue;
    }

    public void setGrossAmount(BigDecimal val) {
        this.totalGrossRevenue = val;
    }

    public BigDecimal getCommissionAmount() {
        return totalCommission;
    }

    public void setCommissionAmount(BigDecimal val) {
        this.totalCommission = val;
    }

    public BigDecimal getNetPayableAmount() {
        return totalNetPayout;
    }

    public void setNetPayableAmount(BigDecimal val) {
        this.totalNetPayout = val;
    }
}

package com.danasea.backend.modules.order.domain.models;

import com.danasea.backend.shared.core.domain.models.BaseDomainModel;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class DiscountCode extends BaseDomainModel {
    private String code;
    private DiscountScope scope;
    private DiscountSponsorType sponsorType = DiscountSponsorType.PLATFORM;
    private UUID vendorId;
    private UUID serviceId;
    private UUID recipientCustomerId;
    private UUID replacementSubOrderId;
    private UUID compensationDisruptionId;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount = BigDecimal.ZERO;
    private BigDecimal maxDiscountAmount;
    private Integer maxUses;
    private Integer usedCount = 0;
    private Integer maxUsesPerUser;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private Boolean isActive = true;

    public boolean isCurrentlyActive(OffsetDateTime now) {
        if (Boolean.FALSE.equals(isActive)) {
            return false;
        }
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        if (validTo != null && now.isAfter(validTo)) {
            return false;
        }
        return true;
    }

    public boolean hasQuotaRemaining() {
        if (maxUses == null) {
            return true;
        }
        return usedCount == null || usedCount < maxUses;
    }

    public boolean canUserRedeem(long userUsageCount) {
        if (maxUsesPerUser == null) {
            return true;
        }
        return userUsageCount < maxUsesPerUser;
    }

    public boolean meetsMinimumAmount(BigDecimal amount) {
        if (minOrderAmount == null || minOrderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        return amount != null && amount.compareTo(minOrderAmount) >= 0;
    }

    /**
     * Hybrid stacking policy:
     * - Vendor-funded vouchers (scope=VENDOR, sponsorType=VENDOR) can stack on top of Platform Flash Sales.
     * - Vendor-fault compensation vouchers (VC-..., recovered from at-fault vendor settlement) can also stack.
     * - Platform promotional vouchers (e.g., DANASEA10) and platform replacement vouchers remain blocked
     *   to prevent double platform subsidy on a single Flash Sale item.
     */
    public boolean isStackableWithFlashSale() {
        if (scope == DiscountScope.VENDOR && sponsorType == DiscountSponsorType.VENDOR) {
            return true;
        }
        return compensationDisruptionId != null;
    }
}

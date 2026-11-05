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
}

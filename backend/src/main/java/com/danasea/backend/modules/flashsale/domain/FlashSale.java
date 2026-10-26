package com.danasea.backend.modules.flashsale.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FlashSale(UUID id, UUID serviceId, UUID optionId, String status,
        BigDecimal unitSubsidy, Instant startsAt, Instant endsAt, int quota, BigDecimal budget,
        BigDecimal originalUnitPrice, UUID policyId, UUID createdBy) {
    public boolean activeAt(Instant now) {
        return "ENABLED".equals(status) && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
    public FlashSale enabled(BigDecimal originalPrice, UUID policy) {
        if(!"DRAFT".equals(status)) throw new FlashSaleException("FLASH_SALE_TERMS_IMMUTABLE");
        if(originalPrice==null || originalPrice.compareTo(unitSubsidy)<=0 || policy==null)
            throw new FlashSaleException("INVALID_FLASH_SALE_PRICE");
        return new FlashSale(id,serviceId,optionId,"ENABLED",unitSubsidy,startsAt,endsAt,quota,budget,originalPrice,policy,createdBy);
    }
    public FlashSale cancelled() {
        if("CANCELLED".equals(status)) return this;
        if(!"DRAFT".equals(status) && !"ENABLED".equals(status)) throw new FlashSaleException("FLASH_SALE_TERMS_IMMUTABLE");
        return new FlashSale(id,serviceId,optionId,"CANCELLED",unitSubsidy,startsAt,endsAt,quota,budget,originalUnitPrice,policyId,createdBy);
    }
}

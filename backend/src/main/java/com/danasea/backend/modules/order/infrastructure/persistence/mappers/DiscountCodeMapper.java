package com.danasea.backend.modules.order.infrastructure.persistence.mappers;

import com.danasea.backend.modules.order.domain.models.DiscountCode;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;

import org.springframework.stereotype.Component;

@Component
public class DiscountCodeMapper {

    public DiscountCode toDomain(DiscountCodeJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        DiscountCode domain = new DiscountCode();
        domain.setId(entity.getId());
        domain.setCode(entity.getCode());
        domain.setScope(entity.getScope());
        domain.setSponsorType(entity.getSponsorType());
        domain.setVendorId(entity.getVendorId());
        domain.setServiceId(entity.getServiceId());
        domain.setRecipientCustomerId(entity.getRecipientCustomerId());
        domain.setReplacementSubOrderId(entity.getReplacementSubOrderId());
        domain.setCompensationDisruptionId(entity.getCompensationDisruptionId());
        domain.setDiscountType(entity.getDiscountType());
        domain.setDiscountValue(entity.getDiscountValue());
        domain.setMinOrderAmount(entity.getMinOrderAmount());
        domain.setMaxDiscountAmount(entity.getMaxDiscountAmount());
        domain.setMaxUses(entity.getMaxUses());
        domain.setUsedCount(entity.getUsedCount());
        domain.setMaxUsesPerUser(entity.getMaxUsesPerUser());
        domain.setValidFrom(entity.getValidFrom());
        domain.setValidTo(entity.getValidTo());
        domain.setIsActive(entity.getIsActive());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        return domain;
    }

    public DiscountCodeJpaEntity toEntity(DiscountCode domain) {
        if (domain == null) {
            return null;
        }
        DiscountCodeJpaEntity entity = new DiscountCodeJpaEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setCode(domain.getCode());
        entity.setScope(domain.getScope());
        entity.setSponsorType(domain.getSponsorType());
        entity.setVendorId(domain.getVendorId());
        entity.setServiceId(domain.getServiceId());
        entity.setRecipientCustomerId(domain.getRecipientCustomerId());
        entity.setReplacementSubOrderId(domain.getReplacementSubOrderId());
        entity.setCompensationDisruptionId(domain.getCompensationDisruptionId());
        entity.setDiscountType(domain.getDiscountType());
        entity.setDiscountValue(domain.getDiscountValue());
        entity.setMinOrderAmount(domain.getMinOrderAmount());
        entity.setMaxDiscountAmount(domain.getMaxDiscountAmount());
        entity.setMaxUses(domain.getMaxUses());
        entity.setUsedCount(domain.getUsedCount() != null ? domain.getUsedCount() : 0);
        entity.setMaxUsesPerUser(domain.getMaxUsesPerUser());
        entity.setValidFrom(domain.getValidFrom());
        entity.setValidTo(domain.getValidTo());
        entity.setIsActive(domain.getIsActive());
        if (domain.getCreatedAt() != null) {
            entity.setCreatedAt(domain.getCreatedAt());
        }
        if (domain.getUpdatedAt() != null) {
            entity.setUpdatedAt(domain.getUpdatedAt());
        }
        return entity;
    }
}

package com.danasea.backend.modules.vendor.infrastructure.mappers;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;

@Component
public class VendorMapper {

    public Vendor toDomain(VendorJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Vendor domain = new Vendor();
        domain.setId(entity.getId());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        domain.setUserId(entity.getUserId());
        domain.setBusinessName(entity.getBusinessName());
        domain.setTaxCode(entity.getTaxCode());
        domain.setAddress(entity.getAddress());
        domain.setBankAccountNumber(entity.getBankAccountNumber());
        domain.setBankName(entity.getBankName());
        domain.setBankAccountHolder(entity.getBankAccountHolder());
        domain.setVerificationStatus(entity.getVerificationStatus());
        domain.setVerifiedBy(entity.getVerifiedBy());
        domain.setVerifiedAt(entity.getVerifiedAt());
        domain.setRatingAvg(entity.getRatingAvg());
        domain.setRatingCount(entity.getRatingCount());
        domain.setBadgeTier(entity.getBadgeTier());
        domain.setRejectionReason(entity.getRejectionReason());
        return domain;
    }

    public VendorJpaEntity toEntity(Vendor domain) {
        if (domain == null) {
            return null;
        }
        VendorJpaEntity entity = new VendorJpaEntity();
        entity.setId(domain.getId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setUserId(domain.getUserId());
        entity.setBusinessName(domain.getBusinessName());
        entity.setTaxCode(domain.getTaxCode());
        entity.setAddress(domain.getAddress());
        entity.setBankAccountNumber(domain.getBankAccountNumber());
        entity.setBankName(domain.getBankName());
        entity.setBankAccountHolder(domain.getBankAccountHolder());
        entity.setVerificationStatus(domain.getVerificationStatus());
        entity.setVerifiedBy(domain.getVerifiedBy());
        entity.setVerifiedAt(domain.getVerifiedAt());
        entity.setRatingAvg(domain.getRatingAvg());
        entity.setRatingCount(domain.getRatingCount());
        entity.setBadgeTier(domain.getBadgeTier());
        entity.setRejectionReason(domain.getRejectionReason());
        return entity;
    }

    public List<Vendor> toDomainList(List<VendorJpaEntity> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(this::toDomain)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<VendorJpaEntity> toEntityList(List<Vendor> domains) {
        if (domains == null) {
            return Collections.emptyList();
        }
        return domains.stream()
                .map(this::toEntity)
                .filter(Objects::nonNull)
                .toList();
    }
}

package com.danasea.backend.modules.partner.infrastructure.mappers;

import com.danasea.backend.modules.partner.domain.models.PartnerApplication;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import org.springframework.stereotype.Component;

@Component
public class PartnerApplicationMapper {

    public PartnerApplication toDomain(VendorRoleApplicationJpaEntity entity) {
        if (entity == null) return null;
        return PartnerApplication.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .businessName(entity.getBusinessName())
                .taxCode(entity.getTaxCode())
                .address(entity.getAddress())
                .bankAccountNumber(entity.getBankAccountNumber())
                .bankName(entity.getBankName())
                .bankAccountHolder(entity.getBankAccountHolder())
                .status(entity.getStatus())
                .rejectionReason(entity.getRejectionReason())
                .reviewedBy(entity.getReviewedBy())
                .reviewedAt(entity.getReviewedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public VendorRoleApplicationJpaEntity toEntity(PartnerApplication domain) {
        if (domain == null) return null;
        VendorRoleApplicationJpaEntity entity = new VendorRoleApplicationJpaEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setBusinessName(domain.getBusinessName());
        entity.setTaxCode(domain.getTaxCode());
        entity.setAddress(domain.getAddress());
        entity.setBankAccountNumber(domain.getBankAccountNumber());
        entity.setBankName(domain.getBankName());
        entity.setBankAccountHolder(domain.getBankAccountHolder());
        entity.setStatus(domain.getStatus());
        entity.setRejectionReason(domain.getRejectionReason());
        entity.setReviewedBy(domain.getReviewedBy());
        entity.setReviewedAt(domain.getReviewedAt());
        return entity;
    }

    public PartnerApplicationResponse toResponse(PartnerApplication domain) {
        if (domain == null) return null;
        return new PartnerApplicationResponse(
                domain.getId(),
                domain.getUserId(),
                domain.getBusinessName(),
                domain.getTaxCode(),
                domain.getAddress(),
                domain.getBankAccountNumber(),
                domain.getBankName(),
                domain.getBankAccountHolder(),
                domain.getStatus(),
                domain.getRejectionReason(),
                domain.getReviewedBy(),
                domain.getReviewedAt(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}

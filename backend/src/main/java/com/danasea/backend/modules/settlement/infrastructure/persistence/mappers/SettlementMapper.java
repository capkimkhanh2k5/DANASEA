package com.danasea.backend.modules.settlement.infrastructure.persistence.mappers;

import com.danasea.backend.modules.settlement.application.dtos.SettlementDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.SettlementLineItemResponse;
import com.danasea.backend.modules.settlement.application.dtos.SettlementResponse;
import com.danasea.backend.modules.settlement.domain.models.Settlement;
import com.danasea.backend.modules.settlement.domain.models.SettlementLineItem;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementLineItemJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class SettlementMapper {

    public Settlement toDomain(SettlementJpaEntity entity, List<SettlementLineItemJpaEntity> lineItemEntities) {
        if (entity == null) return null;

        List<SettlementLineItem> lineItems = lineItemEntities != null
                ? lineItemEntities.stream().map(this::toLineItemDomain).toList()
                : Collections.emptyList();

        return Settlement.builder()
                .id(entity.getId())
                .vendorId(entity.getVendorId())
                .periodStart(entity.getPeriodStart())
                .periodEnd(entity.getPeriodEnd())
                .totalGrossRevenue(entity.getGrossAmount())
                .totalCommission(entity.getCommissionAmount())
                .totalNetPayout(entity.getNetPayableAmount())
                .vendorFaultCompensationDeductedAmount(entity.getVendorFaultCompensationDeductedAmount())
                .safetyRebateRate(entity.getSafetyRebateRate())
                .safetyRebateAmount(entity.getSafetyRebateAmount())
                .safetyRebateQualified(entity.getSafetyRebateQualified())
                .safetyRebateNote(entity.getSafetyRebateNote())
                .status(entity.getStatus())
                .generatedAt(entity.getGeneratedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .lineItems(lineItems)
                .build();
    }

    public SettlementLineItem toLineItemDomain(SettlementLineItemJpaEntity entity) {
        if (entity == null) return null;
        return SettlementLineItem.builder()
                .id(entity.getId())
                .settlementId(entity.getSettlementId())
                .subOrderId(entity.getSubOrderId())
                .grossAmount(entity.getGrossAmount())
                .refundAmount(entity.getRefundAmount())
                .commissionRate(entity.getCommissionRate())
                .commissionAmount(entity.getCommissionAmount())
                .netAmount(entity.getNetAmount())
                .excludedReason(entity.getExcludedReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public SettlementJpaEntity toEntity(Settlement domain) {
        if (domain == null) return null;
        SettlementJpaEntity entity = SettlementJpaEntity.builder()
                .vendorId(domain.getVendorId())
                .periodStart(domain.getPeriodStart())
                .periodEnd(domain.getPeriodEnd())
                .grossAmount(domain.getTotalGrossRevenue())
                .commissionAmount(domain.getTotalCommission())
                .netPayableAmount(domain.getTotalNetPayout())
                .vendorFaultCompensationDeductedAmount(domain.getVendorFaultCompensationDeductedAmount())
                .safetyRebateRate(domain.getSafetyRebateRate())
                .safetyRebateAmount(domain.getSafetyRebateAmount())
                .safetyRebateQualified(domain.getSafetyRebateQualified())
                .safetyRebateNote(domain.getSafetyRebateNote())
                .status(domain.getStatus())
                .generatedAt(domain.getGeneratedAt())
                .build();
        entity.setId(domain.getId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    public SettlementLineItemJpaEntity toLineItemEntity(SettlementLineItem domain) {
        if (domain == null) return null;
        SettlementLineItemJpaEntity entity = SettlementLineItemJpaEntity.builder()
                .settlementId(domain.getSettlementId())
                .subOrderId(domain.getSubOrderId())
                .grossAmount(domain.getGrossAmount())
                .refundAmount(domain.getRefundAmount())
                .commissionRate(domain.getCommissionRate())
                .commissionAmount(domain.getCommissionAmount())
                .netAmount(domain.getNetAmount())
                .excludedReason(domain.getExcludedReason())
                .build();
        entity.setId(domain.getId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    public SettlementResponse toResponse(SettlementJpaEntity entity) {
        if (entity == null) return null;
        return SettlementResponse.builder()
                .id(entity.getId())
                .vendorId(entity.getVendorId())
                .periodStart(entity.getPeriodStart())
                .periodEnd(entity.getPeriodEnd())
                .grossAmount(entity.getGrossAmount())
                .commissionAmount(entity.getCommissionAmount())
                .netPayableAmount(entity.getNetPayableAmount())
                .vendorFaultCompensationDeductedAmount(entity.getVendorFaultCompensationDeductedAmount())
                .safetyRebateRate(entity.getSafetyRebateRate())
                .safetyRebateAmount(entity.getSafetyRebateAmount())
                .safetyRebateQualified(entity.getSafetyRebateQualified())
                .safetyRebateNote(entity.getSafetyRebateNote())
                .status(entity.getStatus())
                .generatedAt(entity.getGeneratedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public SettlementResponse toResponse(Settlement domain) {
        if (domain == null) return null;
        return SettlementResponse.builder()
                .id(domain.getId())
                .vendorId(domain.getVendorId())
                .periodStart(domain.getPeriodStart())
                .periodEnd(domain.getPeriodEnd())
                .grossAmount(domain.getTotalGrossRevenue())
                .commissionAmount(domain.getTotalCommission())
                .netPayableAmount(domain.getTotalNetPayout())
                .vendorFaultCompensationDeductedAmount(domain.getVendorFaultCompensationDeductedAmount())
                .safetyRebateRate(domain.getSafetyRebateRate())
                .safetyRebateAmount(domain.getSafetyRebateAmount())
                .safetyRebateQualified(domain.getSafetyRebateQualified())
                .safetyRebateNote(domain.getSafetyRebateNote())
                .status(domain.getStatus())
                .generatedAt(domain.getGeneratedAt())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public SettlementLineItemResponse toLineItemResponse(SettlementLineItemJpaEntity entity) {
        if (entity == null) return null;
        return SettlementLineItemResponse.builder()
                .id(entity.getId())
                .subOrderId(entity.getSubOrderId())
                .grossAmount(entity.getGrossAmount())
                .refundAmount(entity.getRefundAmount())
                .commissionRate(entity.getCommissionRate())
                .commissionAmount(entity.getCommissionAmount())
                .netAmount(entity.getNetAmount())
                .excludedReason(entity.getExcludedReason())
                .isExcluded(entity.getExcludedReason() != null)
                .build();
    }

    public SettlementLineItemResponse toLineItemResponse(SettlementLineItem domain) {
        if (domain == null) return null;
        return SettlementLineItemResponse.builder()
                .id(domain.getId())
                .subOrderId(domain.getSubOrderId())
                .grossAmount(domain.getGrossAmount())
                .refundAmount(domain.getRefundAmount())
                .commissionRate(domain.getCommissionRate())
                .commissionAmount(domain.getCommissionAmount())
                .netAmount(domain.getNetAmount())
                .excludedReason(domain.getExcludedReason())
                .isExcluded(domain.isExcluded())
                .build();
    }

    public SettlementDetailResponse toDetailResponse(SettlementJpaEntity entity, List<SettlementLineItemJpaEntity> lineItems) {
        if (entity == null) return null;
        List<SettlementLineItemResponse> itemResponses = lineItems != null
                ? lineItems.stream().map(this::toLineItemResponse).toList()
                : Collections.emptyList();

        return SettlementDetailResponse.builder()
                .id(entity.getId())
                .vendorId(entity.getVendorId())
                .periodStart(entity.getPeriodStart())
                .periodEnd(entity.getPeriodEnd())
                .grossAmount(entity.getGrossAmount())
                .commissionAmount(entity.getCommissionAmount())
                .netPayableAmount(entity.getNetPayableAmount())
                .vendorFaultCompensationDeductedAmount(entity.getVendorFaultCompensationDeductedAmount())
                .safetyRebateRate(entity.getSafetyRebateRate())
                .safetyRebateAmount(entity.getSafetyRebateAmount())
                .safetyRebateQualified(entity.getSafetyRebateQualified())
                .safetyRebateNote(entity.getSafetyRebateNote())
                .status(entity.getStatus())
                .generatedAt(entity.getGeneratedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .lineItems(itemResponses)
                .build();
    }
}

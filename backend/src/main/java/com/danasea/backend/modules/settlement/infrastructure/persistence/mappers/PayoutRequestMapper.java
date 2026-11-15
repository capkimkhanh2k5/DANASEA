package com.danasea.backend.modules.settlement.infrastructure.persistence.mappers;

import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import org.springframework.stereotype.Component;

@Component
public class PayoutRequestMapper {

    public PayoutRequestResponse toResponse(PayoutRequestJpaEntity entity) {
        if (entity == null) return null;
        return PayoutRequestResponse.builder()
                .id(entity.getId())
                .vendorId(entity.getVendorId())
                .settlementId(entity.getSettlementId())
                .amount(entity.getAmount())
                .status(entity.getStatus())
                .processedBy(entity.getProcessedBy())
                .processedAt(entity.getProcessedAt())
                .rejectionReason(entity.getRejectionReason())
                .bankReference(entity.getBankReference())
                .adminNote(entity.getAdminNote())
                .note(entity.getVendorNote())
                .vendorNote(entity.getVendorNote())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public PayoutRequestDetailResponse toDetailResponse(
            PayoutRequestJpaEntity entity,
            Vendor vendor,
            SettlementJpaEntity settlement
    ) {
        if (entity == null) return null;

        String vendorName = vendor != null ? vendor.getBusinessName() : null;
        String bankName = vendor != null ? vendor.getBankName() : null;
        String bankAccountNumber = vendor != null ? vendor.getBankAccountNumber() : null;
        String bankAccountHolder = vendor != null ? vendor.getBankAccountHolder() : null;

        return PayoutRequestDetailResponse.builder()
                .id(entity.getId())
                .vendorId(entity.getVendorId())
                .vendorName(vendorName)
                .bankName(bankName)
                .bankAccountNumber(bankAccountNumber)
                .bankAccountHolder(bankAccountHolder)
                .settlementId(entity.getSettlementId())
                .periodStart(settlement != null ? settlement.getPeriodStart() : null)
                .periodEnd(settlement != null ? settlement.getPeriodEnd() : null)
                .settlementNetPayableAmount(settlement != null ? settlement.getNetPayableAmount() : null)
                .settlementStatus(settlement != null ? settlement.getStatus() : null)
                .amount(entity.getAmount())
                .status(entity.getStatus())
                .processedBy(entity.getProcessedBy())
                .processedAt(entity.getProcessedAt())
                .rejectionReason(entity.getRejectionReason())
                .bankReference(entity.getBankReference())
                .adminNote(entity.getAdminNote())
                .note(entity.getVendorNote())
                .vendorNote(entity.getVendorNote())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

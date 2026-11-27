package com.danasea.backend.modules.order.infrastructure.persistence.mappers;

import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.shared.util.DisplayCodeFormatter;

import org.springframework.stereotype.Component;

@Component
public class SubOrderMapper {

    public SubOrder toDomain(SubOrderJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        SubOrder domain = new SubOrder();
        domain.setId(entity.getId());
        domain.setBookingItemId(entity.getBookingItemId());
        domain.setMasterOrderId(entity.getMasterOrderId());
        domain.setSubOrderCode(entity.getSubOrderCode() != null && !entity.getSubOrderCode().isBlank()
                ? entity.getSubOrderCode()
                : DisplayCodeFormatter.format(entity.getId(), "SUB"));
        domain.setVendorId(entity.getVendorId());
        domain.setServiceId(entity.getServiceId());
        domain.setSlotId(entity.getSlotId());
        domain.setQuantity(entity.getQuantity());
        domain.setUnitPrice(entity.getUnitPrice());
        domain.setSubtotalAmount(entity.getSubtotalAmount());
        domain.setDiscountAmount(entity.getDiscountAmount());
        domain.setVendorDiscountAmount(entity.getVendorDiscountAmount());
        domain.setPlatformDiscountAmount(entity.getPlatformDiscountAmount());
        domain.setCommissionBasisAmount(entity.getCommissionBasisAmount());
        domain.setFinalAmount(entity.getFinalAmount());
        domain.setCommissionRate(entity.getCommissionRate());
        domain.setCommissionAmount(entity.getCommissionAmount());
        domain.setVendorPayoutAmount(entity.getVendorPayoutAmount());
        domain.setStatus(entity.getStatus());
        domain.setCancellationReason(entity.getCancellationReason());
        domain.setCancellationPolicyVersion(entity.getCancellationPolicyVersion());
        domain.setWaiverRequired(entity.getWaiverRequired() != null ? entity.getWaiverRequired() : Boolean.FALSE);
        domain.setWaiverVersion(entity.getWaiverVersion() != null ? entity.getWaiverVersion() : 1);
        domain.setWaiverContent(entity.getWaiverContent());
        domain.setWaiverContentEn(entity.getWaiverContentEn());
        domain.setWaiverAccepted(entity.getWaiverAccepted() != null ? entity.getWaiverAccepted() : Boolean.FALSE);
        domain.setWaiverAcceptedAt(entity.getWaiverAcceptedAt());
        domain.setWaiverAcceptedBy(entity.getWaiverAcceptedBy());
        domain.setWaiverAcceptedLanguage(entity.getWaiverAcceptedLanguage());
        domain.setWaiverAcceptedContent(entity.getWaiverAcceptedContent());
        domain.setQrSecret(entity.getQrSecret());
        domain.setCheckedInAt(entity.getCheckedInAt());
        domain.setVendorNotifiedAt(entity.getVendorNotifiedAt());
        domain.setRescheduleVersion(entity.getRescheduleVersion());
        domain.setRescheduledAt(entity.getRescheduledAt());
        domain.setOriginalSlotId(entity.getOriginalSlotId());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        return domain;
    }

    public SubOrderJpaEntity toEntity(SubOrder domain) {
        if (domain == null) {
            return null;
        }
        SubOrderJpaEntity entity = new SubOrderJpaEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setBookingItemId(domain.getBookingItemId());
        entity.setMasterOrderId(domain.getMasterOrderId());
        if (domain.getSubOrderCode() != null && !domain.getSubOrderCode().isBlank()) {
            entity.setSubOrderCode(domain.getSubOrderCode());
        } else if (domain.getId() != null) {
            entity.setSubOrderCode(DisplayCodeFormatter.format(domain.getId(), "SUB"));
        }
        entity.setVendorId(domain.getVendorId());
        entity.setServiceId(domain.getServiceId());
        entity.setSlotId(domain.getSlotId());
        entity.setQuantity(domain.getQuantity());
        entity.setUnitPrice(domain.getUnitPrice());
        entity.setSubtotalAmount(domain.getSubtotalAmount());
        entity.setDiscountAmount(domain.getDiscountAmount());
        entity.setVendorDiscountAmount(domain.getVendorDiscountAmount());
        entity.setPlatformDiscountAmount(domain.getPlatformDiscountAmount());
        entity.setCommissionBasisAmount(domain.getCommissionBasisAmount());
        entity.setFinalAmount(domain.getFinalAmount());
        entity.setCommissionRate(domain.getCommissionRate());
        entity.setCommissionAmount(domain.getCommissionAmount());
        entity.setVendorPayoutAmount(domain.getVendorPayoutAmount());
        entity.setStatus(domain.getStatus());
        entity.setCancellationReason(domain.getCancellationReason());
        entity.setCancellationPolicyVersion(domain.getCancellationPolicyVersion());
        entity.setWaiverRequired(domain.getWaiverRequired() != null ? domain.getWaiverRequired() : Boolean.FALSE);
        entity.setWaiverVersion(domain.getWaiverVersion() != null ? domain.getWaiverVersion() : 1);
        entity.setWaiverContent(domain.getWaiverContent());
        entity.setWaiverContentEn(domain.getWaiverContentEn());
        entity.setWaiverAccepted(domain.getWaiverAccepted() != null ? domain.getWaiverAccepted() : Boolean.FALSE);
        entity.setWaiverAcceptedAt(domain.getWaiverAcceptedAt());
        entity.setWaiverAcceptedBy(domain.getWaiverAcceptedBy());
        entity.setWaiverAcceptedLanguage(domain.getWaiverAcceptedLanguage());
        entity.setWaiverAcceptedContent(domain.getWaiverAcceptedContent());
        entity.setQrSecret(domain.getQrSecret());
        entity.setCheckedInAt(domain.getCheckedInAt());
        entity.setVendorNotifiedAt(domain.getVendorNotifiedAt());
        entity.setRescheduleVersion(domain.getRescheduleVersion() == null ? 0L : domain.getRescheduleVersion());
        entity.setRescheduledAt(domain.getRescheduledAt());
        entity.setOriginalSlotId(domain.getOriginalSlotId());
        if (domain.getCreatedAt() != null) {
            entity.setCreatedAt(domain.getCreatedAt());
        }
        if (domain.getUpdatedAt() != null) {
            entity.setUpdatedAt(domain.getUpdatedAt());
        }
        return entity;
    }
}

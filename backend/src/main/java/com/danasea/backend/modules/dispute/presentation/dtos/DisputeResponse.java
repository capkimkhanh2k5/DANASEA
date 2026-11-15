package com.danasea.backend.modules.dispute.presentation.dtos;

import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DisputeResponse(
        UUID id,
        UUID orderId,
        UUID subOrderId,
        UUID customerId,
        DisputeReason reason,
        String description,
        List<String> evidenceUrls,
        DisputeStatus status,
        BigDecimal refundPercentage,
        String adminNote,
        UUID resolvedBy,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String vendorResponse,
        List<String> vendorEvidenceUrls,
        OffsetDateTime vendorRespondedAt,
        String resolvedByName
) {
    public DisputeResponse(
            UUID id,
            UUID orderId,
            UUID subOrderId,
            UUID customerId,
            DisputeReason reason,
            String description,
            List<String> evidenceUrls,
            DisputeStatus status,
            BigDecimal refundPercentage,
            String adminNote,
            UUID resolvedBy,
            OffsetDateTime resolvedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String vendorResponse,
            List<String> vendorEvidenceUrls,
            OffsetDateTime vendorRespondedAt
    ) {
        this(id, orderId, subOrderId, customerId, reason, description, evidenceUrls, status, refundPercentage, adminNote, resolvedBy, resolvedAt, createdAt, updatedAt, vendorResponse, vendorEvidenceUrls, vendorRespondedAt, null);
    }

    public DisputeResponse(
            UUID id,
            UUID orderId,
            UUID subOrderId,
            UUID customerId,
            DisputeReason reason,
            String description,
            List<String> evidenceUrls,
            DisputeStatus status,
            BigDecimal refundPercentage,
            String adminNote,
            UUID resolvedBy,
            OffsetDateTime resolvedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(id, orderId, subOrderId, customerId, reason, description, evidenceUrls, status, refundPercentage, adminNote, resolvedBy, resolvedAt, createdAt, updatedAt, null, null, null);
    }

    public DisputeResponse(
            UUID id,
            UUID subOrderId,
            UUID masterOrderId,
            UUID raisedBy,
            DisputeReason reason,
            String description,
            List<String> evidenceUrls,
            DisputeStatus status,
            String adminNote,
            UUID resolvedBy,
            OffsetDateTime resolvedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(id, masterOrderId, subOrderId, raisedBy, reason, description, evidenceUrls, status, null, adminNote, resolvedBy, resolvedAt, createdAt, updatedAt, null, null, null);
    }

    public static DisputeResponse fromEntity(DisputeJpaEntity entity) {
        return fromEntity(entity, null);
    }

    public static DisputeResponse fromEntity(DisputeJpaEntity entity, String resolvedByName) {
        if (entity == null) {
            return null;
        }
        return new DisputeResponse(
                entity.getId(),
                entity.getOrderId(),
                entity.getSubOrderId(),
                entity.getCustomerId(),
                entity.getReason(),
                entity.getDescription(),
                entity.getEvidenceUrls(),
                entity.getStatus(),
                entity.getRefundPercentage(),
                entity.getAdminNote(),
                entity.getResolvedBy(),
                entity.getResolvedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVendorResponse(),
                entity.getVendorEvidenceUrls(),
                entity.getVendorRespondedAt(),
                resolvedByName
        );
    }

    public UUID masterOrderId() {
        return orderId;
    }

    public UUID raisedBy() {
        return customerId;
    }

    public String resolutionNote() {
        return adminNote;
    }
}

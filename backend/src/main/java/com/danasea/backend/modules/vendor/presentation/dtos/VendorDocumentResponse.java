package com.danasea.backend.modules.vendor.presentation.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.modules.vendor.domain.models.DocStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;

public record VendorDocumentResponse(
        UUID id,
        UUID vendorId,
        DocType docType,
        String fileUrl,
        DocStatus status,
        UUID reviewedBy,
        OffsetDateTime reviewedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static VendorDocumentResponse fromDomain(VendorDocument doc) {
        return fromDomain(doc, doc != null ? doc.getFileUrl() : null);
    }

    public static VendorDocumentResponse fromDomain(VendorDocument doc, String deliveryUrl) {
        if (doc == null) {
            return null;
        }
        return new VendorDocumentResponse(
                doc.getId(),
                doc.getVendorId(),
                doc.getDocType(),
                deliveryUrl != null ? deliveryUrl : doc.getFileUrl(),
                doc.getStatus(),
                doc.getReviewedBy(),
                doc.getReviewedAt(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public DocType getDocType() {
        return docType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public DocStatus getStatus() {
        return status;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

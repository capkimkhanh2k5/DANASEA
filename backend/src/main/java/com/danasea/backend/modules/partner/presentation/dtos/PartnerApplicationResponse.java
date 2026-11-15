package com.danasea.backend.modules.partner.presentation.dtos;

import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PartnerApplicationResponse(
        UUID id,
        UUID userId,
        String businessName,
        String taxCode,
        String address,
        String bankAccountNumber,
        String bankName,
        String bankAccountHolder,
        PartnerApplicationStatus status,
        String rejectionReason,
        UUID reviewedBy,
        OffsetDateTime reviewedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getBusinessName() { return businessName; }
    public String getTaxCode() { return taxCode; }
    public String getAddress() { return address; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public String getBankName() { return bankName; }
    public String getBankAccountHolder() { return bankAccountHolder; }
    public PartnerApplicationStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getReviewedBy() { return reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

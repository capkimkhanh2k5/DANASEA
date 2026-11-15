package com.danasea.backend.modules.partner.domain.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerApplication {
    private UUID id;
    private UUID userId;
    private String businessName;
    private String taxCode;
    private String address;
    private String bankAccountNumber;
    private String bankName;
    private String bankAccountHolder;
    private PartnerApplicationStatus status;
    private String rejectionReason;
    private UUID reviewedBy;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void approve(UUID adminId) {
        if (this.status != PartnerApplicationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING applications can be approved");
        }
        this.status = PartnerApplicationStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = OffsetDateTime.now();
        this.rejectionReason = null;
    }

    public void reject(UUID adminId, String reason) {
        if (this.status != PartnerApplicationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING applications can be rejected");
        }
        this.status = PartnerApplicationStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = OffsetDateTime.now();
        this.rejectionReason = reason;
    }

    public void resubmit(String businessName, String taxCode, String address,
                          String bankAccountNumber, String bankName, String bankAccountHolder) {
        if (this.status != PartnerApplicationStatus.REJECTED) {
            throw new IllegalStateException("Only REJECTED applications can be resubmitted");
        }
        this.businessName = businessName;
        this.taxCode = taxCode;
        this.address = address;
        this.bankAccountNumber = bankAccountNumber;
        this.bankName = bankName;
        this.bankAccountHolder = bankAccountHolder;
        this.status = PartnerApplicationStatus.PENDING;
        this.rejectionReason = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
    }
}

package com.danasea.backend.modules.vendor.presentation.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;

public record VendorProfileResponse(
        UUID id,
        UUID userId,
        String businessName,
        String taxCode,
        String address,
        String bankAccountNumber,
        String bankName,
        String bankAccountHolder,
        VerificationStatus verificationStatus,
        BadgeTier badgeTier,
        BigDecimal ratingAvg,
        Integer ratingCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String rejectionReason
) {
    public VendorProfileResponse(
            UUID id,
            UUID userId,
            String businessName,
            String taxCode,
            String address,
            String bankAccountNumber,
            String bankName,
            String bankAccountHolder,
            VerificationStatus verificationStatus,
            BadgeTier badgeTier,
            BigDecimal ratingAvg,
            Integer ratingCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(id, userId, businessName, taxCode, address, bankAccountNumber, bankName,
                bankAccountHolder, verificationStatus, badgeTier, ratingAvg, ratingCount, createdAt, updatedAt, null);
    }

    public static VendorProfileResponse fromDomain(Vendor vendor) {
        if (vendor == null) {
            return null;
        }
        return new VendorProfileResponse(
                vendor.getId(),
                vendor.getUserId(),
                vendor.getBusinessName(),
                vendor.getTaxCode(),
                vendor.getAddress(),
                vendor.getBankAccountNumber(),
                vendor.getBankName(),
                vendor.getBankAccountHolder(),
                vendor.getVerificationStatus(),
                vendor.getBadgeTier(),
                vendor.getRatingAvg(),
                vendor.getRatingCount(),
                vendor.getCreatedAt(),
                vendor.getUpdatedAt(),
                vendor.getRejectionReason()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public String getAddress() {
        return address;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBankAccountHolder() {
        return bankAccountHolder;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public BadgeTier getBadgeTier() {
        return badgeTier;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public Integer getRatingCount() {
        return ratingCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}

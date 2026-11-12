package com.danasea.backend.modules.admin.application.usecases;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RejectVendorUseCase {

    private final VendorInternalApi vendorInternalApi;
    private final AuditLogInternalApi auditLogInternalApi;

    @Transactional
    public AdminVendorResponse execute(UUID vendorId, UUID adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason cannot be blank");
        }
        String cleanReason = reason.trim();

        Vendor vendor = vendorInternalApi.findById(vendorId)
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found with id: " + vendorId));

        if (vendor.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new IllegalArgumentException("Vendor is already rejected");
        }

        if (vendor.getVerificationStatus() == VerificationStatus.APPROVED) {
            throw new IllegalArgumentException("Cannot reject an already approved vendor");
        }

        vendor.setVerificationStatus(VerificationStatus.REJECTED);
        vendor.setVerifiedBy(adminId);
        vendor.setVerifiedAt(OffsetDateTime.now());
        vendor.setRejectionReason(cleanReason);

        Vendor savedVendor = vendorInternalApi.saveVendor(vendor);

        // Log audit
        auditLogInternalApi.recordAuditLog(
                adminId,
                "VENDOR_REJECTION",
                "VENDOR",
                vendorId,
                "Vendor rejected by admin. Reason: " + cleanReason
        );

        return mapToResponse(savedVendor);
    }

    private AdminVendorResponse mapToResponse(Vendor vendor) {
        return AdminVendorResponse.builder()
                .id(vendor.getId())
                .userId(vendor.getUserId())
                .businessName(vendor.getBusinessName())
                .taxCode(vendor.getTaxCode())
                .address(vendor.getAddress())
                .bankAccountNumber(vendor.getBankAccountNumber())
                .bankName(vendor.getBankName())
                .bankAccountHolder(vendor.getBankAccountHolder())
                .verificationStatus(vendor.getVerificationStatus())
                .verifiedBy(vendor.getVerifiedBy())
                .verifiedAt(vendor.getVerifiedAt())
                .ratingAvg(vendor.getRatingAvg())
                .ratingCount(vendor.getRatingCount())
                .badgeTier(vendor.getBadgeTier())
                .createdAt(vendor.getCreatedAt())
                .updatedAt(vendor.getUpdatedAt())
                .rejectionReason(vendor.getRejectionReason())
                .build();
    }
}

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
import com.danasea.backend.modules.vendor.domain.models.DocType;
import com.danasea.backend.modules.admin.domain.exceptions.VendorDocumentsIncompleteException;
import java.util.EnumSet;
import java.util.Set;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApproveVendorUseCase {

    private final VendorInternalApi vendorInternalApi;
    private final AuditLogInternalApi auditLogInternalApi;

    @Transactional
    public AdminVendorResponse execute(UUID vendorId, UUID adminId) {
        Vendor vendor = vendorInternalApi.findById(vendorId)
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found with id: " + vendorId));

        if (vendor.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new IllegalArgumentException("Vendor is not in PENDING status");
        }

        Set<DocType> requiredDocuments = EnumSet.allOf(DocType.class);
        Set<DocType> approvedDocuments = vendorInternalApi.getApprovedDocumentTypes(vendorId);
        if (!approvedDocuments.containsAll(requiredDocuments)) {
            requiredDocuments.removeAll(approvedDocuments);
            throw new VendorDocumentsIncompleteException(
                    "Vendor approval requires approved documents: " + requiredDocuments);
        }

        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor.setVerifiedBy(adminId);
        vendor.setVerifiedAt(OffsetDateTime.now());

        Vendor savedVendor = vendorInternalApi.saveVendor(vendor);

        // Log audit
        auditLogInternalApi.recordAuditLog(
                adminId,
                "VENDOR_APPROVAL",
                "VENDOR",
                vendorId,
                "Vendor approved by admin"
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
                .build();
    }
}

package com.danasea.backend.modules.admin.presentation.dtos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.presentation.dtos.VendorDocumentResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminVendorResponse {
    private UUID id;
    private UUID userId;
    private String businessName;
    private String taxCode;
    private String address;
    private String bankAccountNumber;
    private String bankName;
    private String bankAccountHolder;
    private VerificationStatus verificationStatus;
    private UUID verifiedBy;
    private OffsetDateTime verifiedAt;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private BadgeTier badgeTier;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<VendorDocumentResponse> documents;
    private String rejectionReason;
}

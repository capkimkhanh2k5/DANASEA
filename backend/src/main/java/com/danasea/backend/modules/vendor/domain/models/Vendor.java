package com.danasea.backend.modules.vendor.domain.models;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.domain.models.BaseDomainModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Vendor extends BaseDomainModel {
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
    private String rejectionReason;
}

package com.danasea.backend.modules.vendor.infrastructure.persistence.entities;

import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "vendors")
public class VendorJpaEntity extends BaseJpaEntity {

    private UUID userId;

    private String businessName;

    private String taxCode;

    private String address;

    private String bankAccountNumber;

    private String bankName;

    private String bankAccountHolder;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    private UUID verifiedBy;

    private OffsetDateTime verifiedAt;

    private BigDecimal ratingAvg;

    private Integer ratingCount;

    @Enumerated(EnumType.STRING)
    private BadgeTier badgeTier;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

}

package com.danasea.backend.modules.partner.infrastructure.persistence.entities;

import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vendor_role_applications")
@Getter
@Setter
public class VendorRoleApplicationJpaEntity extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "tax_code", nullable = false)
    private String taxCode;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "bank_account_number", nullable = false)
    private String bankAccountNumber;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "bank_account_holder", nullable = false)
    private String bankAccountHolder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PartnerApplicationStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
}

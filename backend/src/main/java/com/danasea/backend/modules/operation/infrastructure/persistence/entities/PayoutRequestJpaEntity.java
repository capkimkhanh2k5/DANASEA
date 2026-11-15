package com.danasea.backend.modules.operation.infrastructure.persistence.entities;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payout_requests")
public class PayoutRequestJpaEntity extends BaseJpaEntity {

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;

    @Column(name = "amount", nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayoutRequestStatus status;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "bank_reference", length = 100)
    private String bankReference;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "vendor_note", length = 1000)
    private String vendorNote;
}

package com.danasea.backend.modules.settlement.infrastructure.persistence.entities;

import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementJpaEntity extends BaseJpaEntity {

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "gross_amount", precision = 38, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "commission_amount", precision = 38, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "net_payable_amount", precision = 38, scale = 2)
    private BigDecimal netPayableAmount;

    @Builder.Default
    @Column(name = "vendor_fault_compensation_deducted_amount", nullable = false, precision = 38, scale = 2)
    private BigDecimal vendorFaultCompensationDeductedAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "safety_rebate_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal safetyRebateRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "safety_rebate_amount", nullable = false, precision = 38, scale = 2)
    private BigDecimal safetyRebateAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "safety_rebate_qualified", nullable = false)
    private Boolean safetyRebateQualified = false;

    @Column(name = "safety_rebate_note")
    private String safetyRebateNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SettlementStatus status;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;
}

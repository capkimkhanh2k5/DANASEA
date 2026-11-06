package com.danasea.backend.modules.systemconfig.infrastructure.persistence.entities;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "commission_rules")
public class CommissionRuleJpaEntity extends BaseJpaEntity {

    @Column(name = "rule_code", nullable = false, unique = true)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "target_type", nullable = false)
    private String targetType; // GLOBAL, CATEGORY, VENDOR, ONBOARDING

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "effective_from")
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Column(name = "description")
    private String description;

    @Column(name = "updated_by")
    private UUID updatedBy;
}

package com.danasea.backend.modules.ai.infrastructure.persistence.entities;

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
@Table(name = "ai_dispute_summaries")
public class AiDisputeSummaryJpaEntity extends BaseJpaEntity {

    @Column(nullable = false, unique = true)
    private UUID disputeId;

    @Column(nullable = false)
    private UUID subOrderId;

    @Column(nullable = false)
    private UUID requestedByAdminId;

    @Column(length = 50)
    private String recommendedResolution;

    private Integer recommendedRefundPercent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    @Column(nullable = false, length = 50)
    private String analysisMethod = "HYBRID_DUAL_BRAIN";
}

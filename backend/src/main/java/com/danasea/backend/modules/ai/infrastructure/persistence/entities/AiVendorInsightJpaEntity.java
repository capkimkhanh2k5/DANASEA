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
@Table(name = "ai_vendor_insights")
public class AiVendorInsightJpaEntity extends BaseJpaEntity {

    @Column(nullable = false)
    private UUID vendorId;

    private UUID serviceId;

    @Column(nullable = false, length = 50)
    private String insightType; // REVIEW_COACHING, SMART_REPLY, LISTING_OPTIMIZER, SLOT_ADVISOR

    @Column(length = 128)
    private String sourceFingerprint;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, length = 50)
    private String analysisMethod = "HYBRID_DUAL_BRAIN";
}

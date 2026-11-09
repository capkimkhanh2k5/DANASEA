package com.danasea.backend.modules.ai.infrastructure.persistence.entities;

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
@Table(name = "ai_marine_briefings")
public class AiMarineBriefingJpaEntity extends BaseJpaEntity {

    @Column(nullable = false)
    private UUID masterOrderId;

    @Column(nullable = false)
    private UUID subOrderId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID slotId;

    @Column(nullable = false, length = 50)
    private String weatherStatus = "UNKNOWN";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String briefingJson;

    private OffsetDateTime notifiedAt;
}

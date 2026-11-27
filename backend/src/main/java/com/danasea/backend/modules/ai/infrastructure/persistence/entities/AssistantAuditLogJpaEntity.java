package com.danasea.backend.modules.ai.infrastructure.persistence.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "assistant_audit_logs")
public class AssistantAuditLogJpaEntity extends BaseJpaEntity {

    @Column(nullable = false)
    private UUID conversationId;

    @Column(nullable = true)
    private UUID userId;

    @Column(nullable = false)
    private String toolName;
    
    @Column
    private String keyMasked;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String requestPayload;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String responsePayload;

    @Column(nullable = false)
    private OffsetDateTime executedAt;

}

package com.danasea.backend.modules.ai.infrastructure.persistence.entities;

import com.danasea.backend.modules.ai.domain.models.AiMessageRole;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "ai_messages")
public class AiMessageJpaEntity extends BaseJpaEntity {

    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    private AiMessageRole role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String toolCalls;

    @Column(columnDefinition = "TEXT")
    private String responsePayload;

}

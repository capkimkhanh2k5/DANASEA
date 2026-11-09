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
@Table(name = "ai_copilot_messages")
public class AiCopilotMessageJpaEntity extends BaseJpaEntity {

    @Column(nullable = false)
    private UUID conversationId;

    @Column(nullable = false, length = 50)
    private String role; // USER, ASSISTANT, TOOL, SYSTEM

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String artifactsJson;

    @Column(length = 500)
    private String toolsUsed;

    @Column(nullable = false)
    private boolean verified = false;
}

package com.danasea.backend.modules.ai.infrastructure.persistence.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "ai_conversations")
public class AiConversationJpaEntity extends BaseJpaEntity {

    private UUID userId;

    private OffsetDateTime startedAt;

    private OffsetDateTime endedAt;

    @Column(nullable = false, length = 2)
    private String locale = "vi";

    @Column(length = 200)
    private String title;

    @Column(nullable = false)
    private boolean pinned = false;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(length = 40)
    private String contextType;

    private UUID contextId;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    private OffsetDateTime deletedAt;

    @Column(columnDefinition = "TEXT")
    private String structuredContext;
    private UUID processingToken;
    private OffsetDateTime processingUntil;

}

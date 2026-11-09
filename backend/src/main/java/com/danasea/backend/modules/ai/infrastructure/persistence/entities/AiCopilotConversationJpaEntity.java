package com.danasea.backend.modules.ai.infrastructure.persistence.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "ai_copilot_conversations")
public class AiCopilotConversationJpaEntity extends BaseJpaEntity {

    @Column(nullable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 20)
    private String actorRole; // VENDOR or ADMIN

    private UUID vendorId;

    @Column(nullable = false, length = 10)
    private String language = "vi";

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

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}

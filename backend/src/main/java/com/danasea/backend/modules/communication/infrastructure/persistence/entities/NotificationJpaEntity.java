package com.danasea.backend.modules.communication.infrastructure.persistence.entities;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.NotificationStatus;
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
@Table(name = "notifications")
public class NotificationJpaEntity extends BaseJpaEntity {

    @Column(length = 160, unique = true)
    private String idempotencyKey;

    private UUID userId;

    private String type;

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 2)
    private String locale = "vi";

    private String relatedEntityType;

    private UUID relatedEntityId;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    private OffsetDateTime sentAt;

    @JsonProperty("isRead")
    @Column(nullable = false)
    private boolean isRead = false;

    private OffsetDateTime readAt;

    @JsonProperty("isRead")
    public boolean isRead() {
        return isRead;
    }

    public boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(boolean isRead) {
        this.isRead = isRead;
    }
}

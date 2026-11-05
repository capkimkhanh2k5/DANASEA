package com.danasea.backend.modules.communication.presentation.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.NotificationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private UUID userId;
    private String type;
    private NotificationChannel channel;
    private String title;
    private String body;
    private String relatedEntityType;
    private UUID relatedEntityId;
    private NotificationStatus status;
    private OffsetDateTime sentAt;
    private OffsetDateTime createdAt;
    @JsonProperty("isRead")
    private Boolean isRead;
    private OffsetDateTime readAt;

    @JsonProperty("isRead")
    public Boolean getIsRead() {
        return isRead;
    }

    public boolean isRead() {
        return Boolean.TRUE.equals(isRead);
    }
}

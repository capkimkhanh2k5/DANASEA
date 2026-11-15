package com.danasea.backend.modules.admin.presentation.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class AuditLogResponse {
    private UUID id;
    private UUID actorUserId;
    private String actorName;
    private String actorEmail;
    private String action;
    private String entityType;
    private UUID entityId;
    private String metadata;
    private OffsetDateTime timestamp;
}

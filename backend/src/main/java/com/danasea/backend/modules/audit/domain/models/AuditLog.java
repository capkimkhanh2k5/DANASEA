package com.danasea.backend.modules.audit.domain.models;

import java.util.UUID;


import com.danasea.backend.shared.core.domain.models.BaseDomainModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLog extends BaseDomainModel {
    private UUID actorUserId;
    private String action;
    private String entityType;
    private UUID entityId;
    private String metadata;
}

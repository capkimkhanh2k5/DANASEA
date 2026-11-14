package com.danasea.backend.modules.admin.application.usecases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.admin.presentation.dtos.AuditLogResponse;
import com.danasea.backend.modules.audit.domain.models.AuditLog;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetAuditLogsUseCase {

    private final AuditLogInternalApi auditLogInternalApi;

    public Page<AuditLogResponse> execute(Pageable pageable) {
        return execute(null, null, null, pageable);
    }

    public Page<AuditLogResponse> execute(String action, String entityType, String search, Pageable pageable) {
        Page<AuditLog> auditLogs = auditLogInternalApi.getAuditLogs(action, entityType, search, pageable);
        return auditLogs.map(this::mapToResponse);
    }

    private AuditLogResponse mapToResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .actorUserId(log.getActorUserId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .metadata(log.getMetadata())
                .timestamp(log.getCreatedAt())
                .build();
    }
}

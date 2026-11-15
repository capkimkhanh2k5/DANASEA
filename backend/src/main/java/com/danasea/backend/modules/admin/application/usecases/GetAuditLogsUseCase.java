package com.danasea.backend.modules.admin.application.usecases;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.presentation.dtos.AuditLogResponse;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.audit.domain.models.AuditLog;

@Service
public class GetAuditLogsUseCase {

    private final AuditLogInternalApi auditLogInternalApi;
    private final AccountInternalApi accountInternalApi;

    public GetAuditLogsUseCase(AuditLogInternalApi auditLogInternalApi,
                             @Autowired(required = false) AccountInternalApi accountInternalApi) {
        this.auditLogInternalApi = auditLogInternalApi;
        this.accountInternalApi = accountInternalApi;
    }

    public GetAuditLogsUseCase(AuditLogInternalApi auditLogInternalApi) {
        this(auditLogInternalApi, null);
    }

    public Page<AuditLogResponse> execute(Pageable pageable) {
        return execute(null, null, null, pageable);
    }

    public Page<AuditLogResponse> execute(String action, String entityType, String search, Pageable pageable) {
        Page<AuditLog> auditLogs = auditLogInternalApi.getAuditLogs(action, entityType, search, pageable);
        
        Map<UUID, User> usersMap = Map.of();
        if (accountInternalApi != null) {
            Set<UUID> actorIds = auditLogs.getContent().stream()
                    .map(AuditLog::getActorUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!actorIds.isEmpty()) {
                usersMap = accountInternalApi.findUsersByIds(actorIds);
            }
        }
        final Map<UUID, User> finalUsersMap = usersMap;

        return auditLogs.map(log -> mapToResponse(log, finalUsersMap.get(log.getActorUserId())));
    }

    private AuditLogResponse mapToResponse(AuditLog log, User user) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .actorUserId(log.getActorUserId())
                .actorName(user != null ? user.getFullName() : null)
                .actorEmail(user != null ? user.getEmail() : null)
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .metadata(log.getMetadata())
                .timestamp(log.getCreatedAt())
                .build();
    }
}

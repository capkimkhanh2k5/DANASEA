package com.danasea.backend.modules.admin.application.usecases;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.domain.exceptions.AuditLogNotFoundException;
import com.danasea.backend.modules.admin.presentation.dtos.AuditLogResponse;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.audit.domain.models.AuditLog;

@Service
public class GetAuditLogDetailUseCase {

    private final AuditLogInternalApi auditLogInternalApi;
    private final AccountInternalApi accountInternalApi;

    public GetAuditLogDetailUseCase(AuditLogInternalApi auditLogInternalApi,
                                    @Autowired(required = false) AccountInternalApi accountInternalApi) {
        this.auditLogInternalApi = auditLogInternalApi;
        this.accountInternalApi = accountInternalApi;
    }

    public GetAuditLogDetailUseCase(AuditLogInternalApi auditLogInternalApi) {
        this(auditLogInternalApi, null);
    }

    public AuditLogResponse execute(UUID id) {
        AuditLog auditLog = auditLogInternalApi.getAuditLogById(id)
                .orElseThrow(() -> new AuditLogNotFoundException("Audit log not found with id: " + id));

        User user = null;
        if (auditLog.getActorUserId() != null && accountInternalApi != null) {
            user = accountInternalApi.findUserById(auditLog.getActorUserId()).orElse(null);
        }

        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .actorUserId(auditLog.getActorUserId())
                .actorName(user != null ? user.getFullName() : null)
                .actorEmail(user != null ? user.getEmail() : null)
                .action(auditLog.getAction())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .metadata(auditLog.getMetadata())
                .timestamp(auditLog.getCreatedAt())
                .build();
    }
}

package com.danasea.backend.modules.audit.application.services;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.audit.application.ports.AuditLogPort;
import com.danasea.backend.modules.audit.domain.models.AuditLog;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditLogInternalService implements AuditLogInternalApi {

    private final AuditLogPort auditLogPort;

    @Async
    @Override
    public void recordAuditLog(UUID actorUserId, String action, String entityType, UUID entityId, String metadata) {
        recordTransactionalAuditLog(actorUserId, action, entityType, entityId, metadata);
    }

    @Transactional
    @Override
    public void recordTransactionalAuditLog(UUID actorUserId, String action, String entityType, UUID entityId, String metadata) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUserId(actorUserId);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setMetadata(metadata);

        auditLogPort.saveAuditLog(auditLog);
    }

    @Override
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogPort.getAuditLogs(pageable);
    }

    @Override
    public Page<AuditLog> getAuditLogs(String action, String entityType, String search, Pageable pageable) {
        return auditLogPort.getAuditLogs(action, entityType, search, pageable);
    }

    @Override
    public Optional<AuditLog> getAuditLogById(UUID id) {
        return auditLogPort.getAuditLogById(id);
    }
}

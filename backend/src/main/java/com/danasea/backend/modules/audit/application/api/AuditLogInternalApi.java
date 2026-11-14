package com.danasea.backend.modules.audit.application.api;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.danasea.backend.modules.audit.domain.models.AuditLog;

public interface AuditLogInternalApi {
    void recordAuditLog(UUID actorUserId, String action, String entityType, UUID entityId, String metadata);

    default void recordTransactionalAuditLog(UUID actorUserId, String action, String entityType, UUID entityId, String metadata) {
        recordAuditLog(actorUserId, action, entityType, entityId, metadata);
    }

    Page<AuditLog> getAuditLogs(Pageable pageable);

    default Page<AuditLog> getAuditLogs(String action, String entityType, String search, Pageable pageable) {
        return getAuditLogs(pageable);
    }

    Optional<AuditLog> getAuditLogById(UUID id);
}

package com.danasea.backend.modules.audit.application.ports;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.danasea.backend.modules.audit.domain.models.AuditLog;

public interface AuditLogPort {
    void saveAuditLog(AuditLog log);
    Page<AuditLog> getAuditLogs(Pageable pageable);
    Page<AuditLog> getAuditLogs(String action, String entityType, String search, Pageable pageable);
    Optional<AuditLog> getAuditLogById(UUID id);
}

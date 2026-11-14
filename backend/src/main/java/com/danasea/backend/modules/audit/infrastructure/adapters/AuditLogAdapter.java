package com.danasea.backend.modules.audit.infrastructure.adapters;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import com.danasea.backend.modules.audit.application.ports.AuditLogPort;
import com.danasea.backend.modules.audit.domain.models.AuditLog;
import com.danasea.backend.modules.audit.infrastructure.mappers.AuditLogMapper;
import com.danasea.backend.modules.audit.infrastructure.persistence.repositories.JpaAuditLogRepository;
import com.danasea.backend.modules.audit.infrastructure.persistence.entities.AuditLogJpaEntity;

@Component
@RequiredArgsConstructor
public class AuditLogAdapter implements AuditLogPort {
    private final JpaAuditLogRepository jpaAuditLogRepository;
    private final AuditLogMapper auditLogMapper;

    @Override
    public void saveAuditLog(AuditLog log) {
        AuditLogJpaEntity entity = auditLogMapper.toEntity(log);
        jpaAuditLogRepository.save(entity);
    }

    @Override
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return getAuditLogs(null, null, null, pageable);
    }

    @Override
    public Page<AuditLog> getAuditLogs(String action, String entityType, String search, Pageable pageable) {
        String cleanAction = normalize(action);
        String cleanEntityType = normalize(entityType);
        String pattern = toContainsPattern(search);

        if (cleanAction == null && cleanEntityType == null && pattern == null) {
            return jpaAuditLogRepository.findAll(pageable)
                    .map(auditLogMapper::toDomain);
        }

        return jpaAuditLogRepository.findByFilters(cleanAction, cleanEntityType, pattern, pageable)
                .map(auditLogMapper::toDomain);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static String toContainsPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String clean = search.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + clean + "%";
    }

    @Override
    public Optional<AuditLog> getAuditLogById(UUID id) {
        return jpaAuditLogRepository.findById(id)
                .map(auditLogMapper::toDomain);
    }
}

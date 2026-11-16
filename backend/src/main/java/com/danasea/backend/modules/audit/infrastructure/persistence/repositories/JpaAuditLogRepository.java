package com.danasea.backend.modules.audit.infrastructure.persistence.repositories;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.danasea.backend.modules.audit.infrastructure.persistence.entities.AuditLogJpaEntity;

@Repository
public interface JpaAuditLogRepository extends JpaRepository<AuditLogJpaEntity, UUID> {

    @Query("""
            SELECT a FROM AuditLogJpaEntity a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:pattern IS NULL
                OR LOWER(a.action) LIKE :pattern ESCAPE '!'
                OR LOWER(a.entityType) LIKE :pattern ESCAPE '!'
                OR LOWER(CAST(a.id AS string)) LIKE :pattern ESCAPE '!'
                OR LOWER(CAST(a.entityId AS string)) LIKE :pattern ESCAPE '!'
                OR LOWER(CAST(a.actorUserId AS string)) LIKE :pattern ESCAPE '!')
            """)
    Page<AuditLogJpaEntity> findByFilters(
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("pattern") String pattern,
            Pageable pageable);
}


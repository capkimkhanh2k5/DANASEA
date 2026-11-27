package com.danasea.backend.modules.audit.infrastructure.persistence.entities;

import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "audit_logs")
public class AuditLogJpaEntity extends BaseJpaEntity {

    private UUID actorUserId;

    private String action;

    private String entityType;

    private UUID entityId;

    @Column(columnDefinition = "TEXT")
    private String metadata;

}

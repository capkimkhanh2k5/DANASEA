package com.danasea.backend.modules.service.infrastructure.persistence.entities;

import com.danasea.backend.modules.service.domain.models.DocStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "service_safety_documents")
public class ServiceSafetyDocumentJpaEntity extends BaseJpaEntity {

    private UUID serviceId;

    private String fileUrl;

    @Enumerated(EnumType.STRING)
    private DocStatus status;

    private UUID reviewedBy;

    private OffsetDateTime reviewedAt;

    private String rejectionReason;

}

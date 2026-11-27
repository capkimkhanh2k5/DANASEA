package com.danasea.backend.modules.vendor.infrastructure.persistence.entities;

import com.danasea.backend.modules.vendor.domain.models.DocStatus;
import com.danasea.backend.modules.vendor.domain.models.DocType;
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
@Table(name = "vendor_documents")
public class VendorDocumentJpaEntity extends BaseJpaEntity {

    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    private DocType docType;

    private String fileUrl;

    @Enumerated(EnumType.STRING)
    private DocStatus status;

    private UUID reviewedBy;

    private OffsetDateTime reviewedAt;

}

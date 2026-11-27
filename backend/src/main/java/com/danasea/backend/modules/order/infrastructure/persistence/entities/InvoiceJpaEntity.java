package com.danasea.backend.modules.order.infrastructure.persistence.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "invoices")
public class InvoiceJpaEntity extends BaseJpaEntity {

    private UUID masterOrderId;

    private String invoiceNumber;

    private String pdfUrl;

    private OffsetDateTime issuedAt;

}

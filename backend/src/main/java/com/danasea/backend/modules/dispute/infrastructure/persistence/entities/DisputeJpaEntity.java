package com.danasea.backend.modules.dispute.infrastructure.persistence.entities;

import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.converters.StringListJsonConverter;
import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "DisputeEntity")
@Table(name = "disputes")
@Access(AccessType.FIELD)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeJpaEntity extends BaseJpaEntity {

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "sub_order_id", nullable = false)
    private UUID subOrderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 100, nullable = false)
    private DisputeReason reason;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "evidence_urls", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> evidenceUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private DisputeStatus status;

    @Column(name = "refund_percentage", precision = 5, scale = 2)
    private BigDecimal refundPercentage;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "vendor_response", columnDefinition = "TEXT")
    private String vendorResponse;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "vendor_evidence_urls", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> vendorEvidenceUrls = new ArrayList<>();

    @Column(name = "vendor_responded_at")
    private OffsetDateTime vendorRespondedAt;

    // Compatibility aliases
    @Transient
    public UUID getRaisedBy() {
        return customerId;
    }

    public void setRaisedBy(UUID raisedBy) {
        this.customerId = raisedBy;
    }

    @Transient
    public UUID getMasterOrderId() {
        return orderId;
    }

    public void setMasterOrderId(UUID masterOrderId) {
        this.orderId = masterOrderId;
    }

    @Transient
    public String getResolutionNote() {
        return adminNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.adminNote = resolutionNote;
    }
}


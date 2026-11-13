package com.danasea.backend.modules.service.infrastructure.persistence.entities;

import com.danasea.backend.modules.service.domain.models.OperationType;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "services")
public class ServiceJpaEntity extends BaseJpaEntity {

    private UUID vendorId;

    private UUID categoryId;

    private String name;

    private String nameEn;

    private String slug;

    private String description;

    private String descriptionEn;

    private BigDecimal price;

    private Integer durationMinutes;

    private Integer capacityPerSlot;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private OperationType operationType = OperationType.FIXED_DEPARTURE;

    private String locationName;

    private String address;

    @Column(precision = 46, scale = 10)
    private BigDecimal latitude;

    @Column(precision = 46, scale = 10)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    private ServiceStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(columnDefinition = "TEXT")
    private String waiverContent;

    @Column(columnDefinition = "TEXT")
    private String waiverContentEn;

    @Column(name = "waiver_required")
    private Boolean waiverRequired = false;

    @Column(name = "waiver_version")
    private Integer waiverVersion = 1;

    private Boolean weatherSensitive;

    private BigDecimal minWindKmh;

    private BigDecimal maxWaveM;

    private BigDecimal avgRating;

    private Integer ratingCount;

    private Integer viewCount;

}

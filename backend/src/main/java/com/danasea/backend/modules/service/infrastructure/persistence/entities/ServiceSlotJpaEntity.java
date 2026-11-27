package com.danasea.backend.modules.service.infrastructure.persistence.entities;

import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "service_slots")
public class ServiceSlotJpaEntity extends BaseJpaEntity {

    private UUID serviceId;

    @Column(name = "option_id")
    private UUID optionId;

    private LocalDate date;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer capacity;

    private Integer bookedCount;

    @Enumerated(EnumType.STRING)
    private SlotStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_type")
    private InventoryType inventoryType = InventoryType.PERSON_LIMIT;
}

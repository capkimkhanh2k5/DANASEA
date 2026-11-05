package com.danasea.backend.modules.service.infrastructure.persistence.entities;

import java.util.UUID;

import com.danasea.backend.shared.core.infrastructure.persistence.entities.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "service_slot_units",
    uniqueConstraints = @UniqueConstraint(name = "uq_slot_unit", columnNames = {"slot_id", "unit_number"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceSlotUnitJpaEntity extends BaseJpaEntity {

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(name = "unit_number", nullable = false)
    private Integer unitNumber;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "booked_count", nullable = false)
    private Integer bookedCount;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "equipment_blocked", nullable = false)
    private boolean equipmentBlocked;
}

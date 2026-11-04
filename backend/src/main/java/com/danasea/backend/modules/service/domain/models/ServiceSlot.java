package com.danasea.backend.modules.service.domain.models;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.shared.core.domain.models.BaseDomainModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ServiceSlot extends BaseDomainModel {
    private UUID serviceId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer capacity;
    private Integer bookedCount;
    private SlotStatus status;
    private InventoryType inventoryType;
    @Builder.Default
    private List<ServiceSlotUnit> units = new ArrayList<>();

    public boolean isOpen() {
        return SlotStatus.OPEN.equals(this.status);
    }

    public int getAvailableCapacity() {
        if (inventoryType == InventoryType.SHARED_CAPACITY_UNITS && units != null && !units.isEmpty()) {
            return units.stream().mapToInt(ServiceSlotUnit::getAvailableCapacity).sum();
        }
        int cap = capacity != null ? capacity : 0;
        int booked = bookedCount != null ? bookedCount : 0;
        return Math.max(0, cap - booked);
    }
}

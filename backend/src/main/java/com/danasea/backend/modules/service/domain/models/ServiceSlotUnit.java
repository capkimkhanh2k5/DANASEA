package com.danasea.backend.modules.service.domain.models;

import java.util.UUID;

import com.danasea.backend.shared.core.domain.models.BaseDomainModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ServiceSlotUnit extends BaseDomainModel {
    private UUID slotId;
    private Integer unitNumber;
    private Integer capacity;
    private Integer bookedCount;
    private UUID equipmentId;
    private boolean equipmentBlocked;

    public int getAvailableCapacity() {
        if (equipmentBlocked) return 0;
        int cap = capacity != null ? capacity : 0;
        int booked = bookedCount != null ? bookedCount : 0;
        return Math.max(0, cap - booked);
    }
}

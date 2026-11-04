package com.danasea.backend.modules.service.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class SlotAvailabilityEquipmentTest {
    @Test
    void blocksBothPrivateRentalAndSharedSeatsWithoutLosingBookedCounts() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        var holds = (HashOperations<String, Object, Object>) mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(holds);
        when(holds.entries(anyString())).thenReturn(Map.of());
        var calculator = new SlotAvailabilityCalculator(mock(ServiceSlotRepositoryPort.class), redis);

        var slot = ServiceSlot.builder()
                .id(UUID.randomUUID())
                .inventoryType(InventoryType.SHARED_CAPACITY_UNITS)
                .units(List.of(
                        ServiceSlotUnit.builder().unitNumber(1).capacity(10).bookedCount(0).equipmentBlocked(true).build(),
                        ServiceSlotUnit.builder().unitNumber(2).capacity(8).bookedCount(3).build(),
                        ServiceSlotUnit.builder().unitNumber(3).capacity(6).bookedCount(0).build()))
                .build();

        assertThat(calculator.evaluate(slot, ServiceOption.builder().optionType(OptionType.SHARED).build()).available())
                .isEqualTo(11);
        assertThat(calculator.evaluate(slot, ServiceOption.builder().optionType(OptionType.PRIVATE)
                .maxPaxPerPackage(6).build()).available()).isEqualTo(1);
    }
}

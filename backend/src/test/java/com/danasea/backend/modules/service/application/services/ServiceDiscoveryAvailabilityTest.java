package com.danasea.backend.modules.service.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.PricingUnit;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;

class ServiceDiscoveryAvailabilityTest {

    private ServiceOptionRepositoryPort optionPort;
    private ServiceSlotRepositoryPort slotPort;
    private ServiceDiscoveryAvailability discovery;

    private UUID serviceId;
    private UUID sharedOptId;
    private UUID privateOptId;
    private ServiceOption sharedOption;
    private ServiceOption privateOption;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        optionPort = mock(ServiceOptionRepositoryPort.class);
        slotPort = mock(ServiceSlotRepositoryPort.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(anyString())).thenReturn(Map.of());

        SlotAvailabilityCalculator calculator = new SlotAvailabilityCalculator(slotPort, redis);
        discovery = new ServiceDiscoveryAvailability(optionPort, slotPort, calculator);

        serviceId = UUID.randomUUID();
        sharedOptId = UUID.randomUUID();
        privateOptId = UUID.randomUUID();
        sharedOption = ServiceOption.builder()
                .id(sharedOptId)
                .serviceId(serviceId)
                .optionType(OptionType.SHARED)
                .pricingUnit(PricingUnit.PER_PERSON)
                .price(new BigDecimal("500000"))
                .status(OptionStatus.ACTIVE)
                .build();
        privateOption = ServiceOption.builder()
                .id(privateOptId)
                .serviceId(serviceId)
                .optionType(OptionType.PRIVATE)
                .pricingUnit(PricingUnit.PER_PACKAGE)
                .maxPaxPerPackage(4)
                .price(new BigDecimal("1800000"))
                .status(OptionStatus.ACTIVE)
                .build();
        when(optionPort.findByServiceIdAndStatus(serviceId, OptionStatus.ACTIVE))
                .thenReturn(List.of(sharedOption, privateOption));
    }

    @Test
    @DisplayName("V61 bound slot emits structured options only for allowed optionIds")
    void describeRespectsV61OptionIdsBinding() {
        ServiceSlot sharedOnlySlot = ServiceSlot.builder()
                .id(UUID.randomUUID())
                .serviceId(serviceId)
                .optionIds(List.of(sharedOptId))
                .date(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .capacity(10)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .inventoryType(InventoryType.PERSON_LIMIT)
                .build();
        when(slotPort.findByServiceId(serviceId)).thenReturn(List.of(sharedOnlySlot));

        var responses = discovery.describe(Service.builder().id(serviceId).build());
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).optionId()).isEqualTo(sharedOptId);

        assertThat(discovery.matches(serviceId, SearchServicesCriteria.builder().guests(4).build())).isTrue();
    }

    @Test
    @DisplayName("Legacy unbound PERSON_LIMIT slot supports only SHARED options, not PRIVATE")
    void legacyUnboundPersonLimitSlotSkipsPrivateOption() {
        ServiceSlot legacySlot = ServiceSlot.builder()
                .id(UUID.randomUUID())
                .serviceId(serviceId)
                .date(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .capacity(8)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .inventoryType(InventoryType.PERSON_LIMIT)
                .build();
        when(slotPort.findByServiceId(serviceId)).thenReturn(List.of(legacySlot));

        var responses = discovery.describe(Service.builder().id(serviceId).build());
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).optionId()).isEqualTo(sharedOptId);
    }

    @Test
    @DisplayName("Legacy unbound SHARED_CAPACITY_UNITS slot supports both SHARED and PRIVATE within capacity")
    void legacyUnboundUnitsSlotSupportsCompatibleOptions() {
        ServiceSlot legacyUnitSlot = ServiceSlot.builder()
                .id(UUID.randomUUID())
                .serviceId(serviceId)
                .date(LocalDate.now().plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(16, 0))
                .capacity(6)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .inventoryType(InventoryType.SHARED_CAPACITY_UNITS)
                .units(List.of(ServiceSlotUnit.builder().unitNumber(1).capacity(6).bookedCount(0).build()))
                .build();
        when(slotPort.findByServiceId(serviceId)).thenReturn(List.of(legacyUnitSlot));

        var responses = discovery.describe(Service.builder().id(serviceId).build());
        assertThat(responses).extracting(r -> r.optionId()).containsExactlyInAnyOrder(sharedOptId, privateOptId);
    }
}

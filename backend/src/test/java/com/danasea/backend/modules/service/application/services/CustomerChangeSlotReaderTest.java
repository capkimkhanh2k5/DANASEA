package com.danasea.backend.modules.service.application.services;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.service.application.api.CustomerChangeSlotReadApi.SlotTarget;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerChangeSlotReaderTest {

    @Mock
    private ServiceSlotRepositoryPort slots;
    @Mock
    private ServiceRepositoryPort services;
    @InjectMocks
    private CustomerChangeSlotReader reader;

    @ParameterizedTest
    @EnumSource(SlotStatus.class)
    void mapsRequestedSlotExactlyAndRetainsStatusForChangeRequestValidation(SlotStatus status) {
        UUID slotId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 12);
        LocalTime start = LocalTime.of(9, 30);
        ServiceSlot slot = new ServiceSlot();
        slot.setId(slotId);
        slot.setServiceId(serviceId);
        slot.setDate(date);
        slot.setStartTime(start);
        slot.setStatus(status);
        Service service = Service.builder().status(ServiceStatus.PUBLISHED).build();
        when(slots.findById(slotId)).thenReturn(Optional.of(slot));
        when(services.findPublishedById(serviceId)).thenReturn(Optional.of(service));

        SlotTarget result = reader.read(slotId);

        assertThat(result).isEqualTo(new SlotTarget(slotId, serviceId, date, start, status.name()));
        verify(slots).findById(slotId);
        verify(services).findPublishedById(serviceId);
        verifyNoMoreInteractions(slots, services);
    }

    @Test
    void rejectsMissingSlotBeforeLookingUpAnyService() {
        UUID slotId = UUID.randomUUID();
        when(slots.findById(slotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.read(slotId))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Requested slot not found");

        verify(slots).findById(slotId);
        verifyNoMoreInteractions(slots);
        verifyNoInteractions(services);
    }

    @Test
    void refusesToExposeSlotTargetWhenItsServiceIsMissingOrUnpublished() {
        UUID slotId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ServiceSlot slot = new ServiceSlot();
        slot.setId(slotId);
        slot.setServiceId(serviceId);
        when(slots.findById(slotId)).thenReturn(Optional.of(slot));
        when(services.findPublishedById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.read(slotId))
                .isInstanceOf(ServiceNotFoundException.class).hasMessage("Requested service is not published");

        verify(slots).findById(slotId);
        verify(services).findPublishedById(serviceId);
        verifyNoMoreInteractions(slots, services);
    }
}

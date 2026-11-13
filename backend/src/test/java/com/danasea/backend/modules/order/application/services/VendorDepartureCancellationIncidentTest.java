package com.danasea.backend.modules.order.application.services;

import com.danasea.backend.modules.booking.application.usecases.CancelBookingHoldUseCase;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.shared.presentation.ApiConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VendorDepartureCancellationIncidentTest — SC14 Vendor Incident Cancellation for Checked-in Orders")
class VendorDepartureCancellationIncidentTest {

    @Mock
    private VendorInternalApi vendors;
    @Mock
    private JpaServiceRepository services;
    @Mock
    private JpaServiceSlotRepository slots;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private DisruptionDecisionService decisions;
    @Mock
    private CancelBookingHoldUseCase cancelHold;

    private VendorDepartureCancellation cancellationService;

    private final UUID actor = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID slotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cancellationService = new VendorDepartureCancellation(vendors, services, slots, jdbc, decisions, cancelHold);

        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(actor);
        when(vendors.findByUserId(actor)).thenReturn(Optional.of(vendor));

        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setVendorId(vendorId);
        when(services.findById(serviceId)).thenReturn(Optional.of(service));
    }

    private ServiceSlotJpaEntity buildSlot(SlotStatus status) {
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(slotId);
        slot.setServiceId(serviceId);
        slot.setDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(11, 30));
        slot.setStatus(status);
        return slot;
    }

    @Test
    @DisplayName("1. Regular cancel blocked when passenger is already checked in")
    void testRegularCancel_BlockedWhenPassengerCheckedIn() {
        ServiceSlotJpaEntity slot = buildSlot(SlotStatus.OPEN);
        when(slots.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        // Simulate query: a passenger has checked in
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(slotId)))
                .thenReturn(java.util.List.of());
        when(jdbc.queryForObject(contains("checked_in_at IS NOT NULL"), eq(Boolean.class), eq(slotId)))
                .thenReturn(true);

        assertThatThrownBy(() -> cancellationService.cancel(actor, serviceId, slotId, "Rough sea"))
                .isInstanceOf(ApiConflictException.class)
                .hasMessageContaining("A passenger has checked in. Escalate to operations.");

        verify(slots, never()).save(any());
    }

    @Test
    @DisplayName("2. Incident cancel (SC14) allowed even when passenger is already checked in")
    void testCancelWithIncident_AllowedWhenPassengerCheckedIn() {
        ServiceSlotJpaEntity slot = buildSlot(SlotStatus.OPEN);
        when(slots.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(slotId)))
                .thenReturn(java.util.List.of());

        var result = cancellationService.cancelWithIncident(actor, serviceId, slotId,
                "Boat engine failed after boarding", "TECHNICAL_FAILURE");

        assertThat(result).isNotNull();
        assertThat(result.slotId()).isEqualTo(slotId);
        assertThat(result.reason()).isEqualTo("Boat engine failed after boarding");

        // Verify slot status set to CANCELLED
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.CANCELLED);
        verify(slots).save(slot);

        // Verify record inserted with is_incident = TRUE
        verify(jdbc).update(
                contains("INSERT INTO vendor_departure_cancellations"),
                eq(slotId), eq(vendorId), eq(actor), eq("Boat engine failed after boarding"),
                any(), eq(true), eq("TECHNICAL_FAILURE"));
    }
}

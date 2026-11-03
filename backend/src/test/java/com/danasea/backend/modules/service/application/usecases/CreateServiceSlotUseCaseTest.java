package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.presentation.dtos.CreateServiceSlotRequest;
import com.danasea.backend.modules.service.presentation.dtos.CreateSlotUnitRequest;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateServiceSlotUseCaseTest {
    @Mock private ServiceRepositoryPort services;
    @Mock private ServiceSlotRepositoryPort slots;
    @Mock private VendorPort vendors;
    @Captor private ArgumentCaptor<List<ServiceSlotUnit>> unitsCaptor;
    private CreateServiceSlotUseCase useCase;
    private UUID userId;
    private UUID vendorId;
    private UUID serviceId;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        useCase = new CreateServiceSlotUseCase(services, slots, vendors);
        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        tomorrow = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(1);
    }

    @Test
    void personLimitCreatesOpenUnbookedSlotAndDoesNotPersistUnits() {
        authorize();
        when(slots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = request(InventoryType.PERSON_LIMIT, 12, null);

        var response = useCase.execute(userId, serviceId, request);

        var captured = ArgumentCaptor.forClass(ServiceSlot.class);
        verify(slots).save(captured.capture());
        ServiceSlot saved = captured.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getServiceId()).isEqualTo(serviceId);
        assertThat(saved.getDate()).isEqualTo(tomorrow);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(saved.getInventoryType()).isEqualTo(InventoryType.PERSON_LIMIT);
        assertThat(saved.getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(saved.getCapacity()).isEqualTo(12);
        assertThat(saved.getBookedCount()).isZero();
        assertThat(saved.getUnits()).isEmpty();
        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.serviceId()).isEqualTo(serviceId);
        assertThat(response.date()).isEqualTo(request.date());
        assertThat(response.startTime()).isEqualTo(request.startTime());
        assertThat(response.endTime()).isEqualTo(request.endTime());
        assertThat(response.inventoryType()).isEqualTo(InventoryType.PERSON_LIMIT);
        assertThat(response.status()).isEqualTo(SlotStatus.OPEN);
        assertThat(response.capacity()).isEqualTo(12);
        assertThat(response.bookedCount()).isZero();
        assertThat(response.availableCapacity()).isEqualTo(12);
        assertThat(response.units()).isEmpty();
        verify(slots, never()).saveUnits(any(), anyList());
        verify(services).findByIdForUpdate(serviceId);
    }

    @Test
    void sharedUnitsDeriveTotalCapacityAndKeepEverySavedUnitBoundToTheGeneratedSlot() {
        authorize();
        when(slots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = request(InventoryType.SHARED_CAPACITY_UNITS, 999,
                List.of(new CreateSlotUnitRequest(7, 3), new CreateSlotUnitRequest(2, 5)));

        var response = useCase.execute(userId, serviceId, request);

        var slotCaptor = ArgumentCaptor.forClass(ServiceSlot.class);
        verify(slots).save(slotCaptor.capture());
        verify(slots).saveUnits(eq(response.id()), unitsCaptor.capture());
        ServiceSlot saved = slotCaptor.getValue();
        List<ServiceSlotUnit> savedUnits = unitsCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(response.id());
        assertThat(saved.getServiceId()).isEqualTo(serviceId);
        assertThat(saved.getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(saved.getInventoryType()).isEqualTo(InventoryType.SHARED_CAPACITY_UNITS);
        assertThat(saved.getCapacity()).isEqualTo(8);
        assertThat(saved.getBookedCount()).isZero();
        assertThat(saved.getUnits()).containsExactlyElementsOf(savedUnits);
        assertThat(savedUnits).hasSize(2);
        assertThat(savedUnits).extracting(ServiceSlotUnit::getId).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(savedUnits).extracting(ServiceSlotUnit::getUnitNumber).containsExactly(7, 2);
        assertThat(savedUnits).extracting(ServiceSlotUnit::getCapacity).containsExactly(3, 5);
        for (int index = 0; index < savedUnits.size(); index++) {
            ServiceSlotUnit unit = savedUnits.get(index);
            var row = response.units().get(index);
            assertThat(unit.getSlotId()).isEqualTo(saved.getId());
            assertThat(unit.getBookedCount()).isZero();
            assertThat(row.id()).isEqualTo(unit.getId());
            assertThat(row.unitNumber()).isEqualTo(unit.getUnitNumber());
            assertThat(row.capacity()).isEqualTo(unit.getCapacity());
            assertThat(row.bookedCount()).isZero();
            assertThat(row.availableCapacity()).isEqualTo(unit.getCapacity());
        }
        assertThat(response.capacity()).isEqualTo(8);
        assertThat(response.availableCapacity()).isEqualTo(8);
        assertThat(response.bookedCount()).isZero();
        assertThat(response.inventoryType()).isEqualTo(InventoryType.SHARED_CAPACITY_UNITS);
        assertThat(response.status()).isEqualTo(SlotStatus.OPEN);
    }

    @Test
    void maximumRepresentableUnitTotalRemainsPositiveWithoutOverflow() {
        authorize();
        when(slots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = useCase.execute(userId, serviceId, request(InventoryType.SHARED_CAPACITY_UNITS, null,
                List.of(new CreateSlotUnitRequest(1, Integer.MAX_VALUE - 1), new CreateSlotUnitRequest(2, 1))));
        assertThat(response.capacity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(response.availableCapacity()).isEqualTo(Integer.MAX_VALUE);
        verify(slots).saveUnits(eq(response.id()), anyList());
    }

    @Test
    void nullRequestIsRejectedBeforeAnyRepositoryAccess() {
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Request cannot be null");
        verifyNoInteractions(services, slots, vendors);
    }

    @Test
    void missingVendorCannotCreateAnySlot() {
        when(vendors.findByUserId(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.PERSON_LIMIT, 1, null)))
                .isInstanceOf(VendorNotApprovedException.class);
        verifyNoInteractions(services, slots);
    }

    @Test
    void missingServiceIsRejectedBeforeCheckingDepartures() {
        vendor();
        when(services.findByIdForUpdate(serviceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.PERSON_LIMIT, 1, null)))
                .isInstanceOf(ServiceNotFoundException.class);
        verifyNoInteractions(slots);
    }

    @Test
    void anotherVendorCannotCreateSlotsEvenWithAValidFutureRequest() {
        vendor();
        when(services.findByIdForUpdate(serviceId))
                .thenReturn(Optional.of(Service.builder().id(serviceId).vendorId(UUID.randomUUID()).build()));
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.PERSON_LIMIT, 1, null)))
                .isInstanceOf(UnauthorizedServiceAccessException.class);
        verifyNoInteractions(slots);
    }

    @Test
    void pastDepartureUsesVietnamBusinessTimeAndDoesNotWrite() {
        authorize();
        LocalDateTime past = LocalDateTime.now(Booking.VIETNAM_ZONE).minusMinutes(1);
        var request = new CreateServiceSlotRequest(past.toLocalDate(), past.toLocalTime(),
                past.toLocalTime().plusMinutes(30), InventoryType.PERSON_LIMIT, 2, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot create a slot in the past");
        verifyNoInteractions(slots);
    }

    @ParameterizedTest
    @CsvSource({"09:00,09:00", "10:00,09:00"})
    void endTimeMustBeAfterStartTime(String start, String end) {
        authorize();
        var request = new CreateServiceSlotRequest(tomorrow, LocalTime.parse(start), LocalTime.parse(end),
                InventoryType.PERSON_LIMIT, 2, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Start time must be before end time");
        verifyNoInteractions(slots);
    }

    @Test
    void duplicateServiceDepartureIsRejectedBeforeSavingSlotOrUnits() {
        authorize();
        var request = request(InventoryType.PERSON_LIMIT, 2, null);
        when(slots.existsByServiceIdAndDateAndStartTime(serviceId, request.date(), request.startTime())).thenReturn(true);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("departure already exists");
        noWrites();
    }

    @ParameterizedTest
    @MethodSource("invalidPersonCapacities")
    void personCapacityMustBePresentAndPositive(Integer capacity) {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.PERSON_LIMIT, capacity, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Capacity must be greater than 0");
        noWrites();
    }

    private static Stream<Arguments> invalidPersonCapacities() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(0), Arguments.of(-1));
    }

    @ParameterizedTest
    @MethodSource("missingUnits")
    void sharedInventoryRequiresAtLeastOneUnit(List<CreateSlotUnitRequest> units) {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.SHARED_CAPACITY_UNITS, null, units)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires a list of units");
        noWrites();
    }

    private static Stream<Arguments> missingUnits() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(List.of()));
    }

    @ParameterizedTest
    @MethodSource("invalidUnits")
    void invalidUnitNumbersAndCapacitiesCannotReachPersistence(CreateSlotUnitRequest unit, String message) {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId,
                request(InventoryType.SHARED_CAPACITY_UNITS, null, List.of(unit))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
        noWrites();
    }

    private static Stream<Arguments> invalidUnits() {
        return Stream.of(
                Arguments.of(new CreateSlotUnitRequest(null, 1), "Unit number must be greater than 0"),
                Arguments.of(new CreateSlotUnitRequest(0, 1), "Unit number must be greater than 0"),
                Arguments.of(new CreateSlotUnitRequest(-1, 1), "Unit number must be greater than 0"),
                Arguments.of(new CreateSlotUnitRequest(1, null), "Each unit capacity must be greater than 0"),
                Arguments.of(new CreateSlotUnitRequest(1, 0), "Each unit capacity must be greater than 0"),
                Arguments.of(new CreateSlotUnitRequest(1, -1), "Each unit capacity must be greater than 0"));
    }

    @Test
    void duplicateUnitNumberCannotPersistEitherTheSlotOrItsPartialUnitList() {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.SHARED_CAPACITY_UNITS, null,
                List.of(new CreateSlotUnitRequest(1, 2), new CreateSlotUnitRequest(1, 3)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Duplicate unit number: 1");
        noWrites();
    }

    @Test
    void unitTotalOverflowFailsBeforeAnySlotOrUnitWrite() {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.SHARED_CAPACITY_UNITS, null,
                List.of(new CreateSlotUnitRequest(1, Integer.MAX_VALUE), new CreateSlotUnitRequest(2, 1)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Total unit capacity exceeds the supported integer capacity")
                .hasCauseInstanceOf(ArithmeticException.class);
        noWrites();
    }

    @Test
    void nullUnitIsRejectedAsInvalidInputBeforeAnyWrite() {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId,
                request(InventoryType.SHARED_CAPACITY_UNITS, null, java.util.Collections.singletonList(null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unit cannot be null");
        noWrites();
    }

    private CreateServiceSlotRequest request(InventoryType inventoryType, Integer capacity, List<CreateSlotUnitRequest> units) {
        return new CreateServiceSlotRequest(tomorrow, LocalTime.of(9, 0), LocalTime.of(10, 0), inventoryType, capacity, units);
    }

    private void vendor() {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        when(vendors.findByUserId(userId)).thenReturn(Optional.of(vendor));
    }

    private void authorize() {
        vendor();
        when(services.findByIdForUpdate(serviceId))
                .thenReturn(Optional.of(Service.builder().id(serviceId).vendorId(vendorId).build()));
    }

    private void noWrites() {
        verify(slots, never()).save(any());
        verify(slots, never()).saveUnits(any(), anyList());
    }
}

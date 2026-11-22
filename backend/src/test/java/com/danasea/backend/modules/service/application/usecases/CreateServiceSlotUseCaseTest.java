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
import com.danasea.backend.modules.service.domain.exceptions.InvalidServiceStateException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.adapters.OperatingScheduleStore;
import com.danasea.backend.modules.service.presentation.dtos.CreateServiceSlotRequest;
import com.danasea.backend.modules.service.presentation.dtos.CreateSlotUnitRequest;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.shared.i18n.LocalizedException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
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
import com.danasea.backend.modules.service.domain.exceptions.ServiceSlotCreationException;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotResponse;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateServiceSlotUseCaseTest {
    @Mock private ServiceRepositoryPort services;
    @Mock private ServiceSlotRepositoryPort slots;
    @Mock private VendorPort vendors;
    @Mock private OperatingScheduleStore scheduleStore;
    @Mock private ServiceOptionRepositoryPort optionsPort;
    @Captor private ArgumentCaptor<List<ServiceSlotUnit>> unitsCaptor;
    private CreateServiceSlotUseCase useCase;
    private UUID userId;
    private UUID vendorId;
    private UUID serviceId;
    private LocalDate tomorrow;
    private UUID defaultOptionId;

    @BeforeEach
    void setUp() {
        useCase = new CreateServiceSlotUseCase(services, slots, vendors, scheduleStore, optionsPort);
        userId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        tomorrow = LocalDate.now(Booking.VIETNAM_ZONE).plusDays(1);
        defaultOptionId = UUID.randomUUID();
        ServiceOption defaultOption = ServiceOption.builder()
                .id(defaultOptionId)
                .serviceId(serviceId)
                .name("Standard Option")
                .optionType(OptionType.SHARED)
                .status(OptionStatus.ACTIVE)
                .build();
        org.mockito.Mockito.lenient().when(optionsPort.findById(defaultOptionId)).thenReturn(Optional.of(defaultOption));
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
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.bad_request"));
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
        LocalDateTime past = LocalDate.now(Booking.VIETNAM_ZONE).minusDays(1).atTime(8, 0);
        var request = new CreateServiceSlotRequest(past.toLocalDate(), past.toLocalTime(),
                past.toLocalTime().plusMinutes(30), InventoryType.PERSON_LIMIT, 2, null, defaultOptionId);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_past"));
        verifyNoInteractions(slots);
    }

    @ParameterizedTest
    @CsvSource({
            ",                08:00, 10:00,              PERSON_LIMIT, error.slot_date_time_required",
            "2099-01-01,      ,      10:00,              PERSON_LIMIT, error.slot_date_time_required",
            "2099-01-01,      08:00, ,                   PERSON_LIMIT, error.slot_date_time_required",
            "2099-01-01,      08:00, 10:00,              ,             validation.service.slot.inventory.required",
            "2099-01-01,      08:00, 10:00:00.000000001, PERSON_LIMIT, error.slot_time_precision"
    })
    void invalidDepartureFieldsDoNotReachAvailabilityChecks(LocalDate date, LocalTime start, LocalTime end,
                                                             InventoryType inventoryType, String messageKey) {
        authorize();
        var request = new CreateServiceSlotRequest(date, start, end, inventoryType, 2, null);

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo(messageKey));

        verifyNoInteractions(slots, scheduleStore);
    }

    @ParameterizedTest
    @CsvSource({"09:00,09:00", "10:00,09:00"})
    void endTimeMustBeAfterStartTime(String start, String end) {
        authorize();
        var request = new CreateServiceSlotRequest(tomorrow, LocalTime.parse(start), LocalTime.parse(end),
                InventoryType.PERSON_LIMIT, 2, null);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_time_order"));
        verifyNoInteractions(slots);
    }

    @Test
    void duplicateServiceDepartureIsRejectedBeforeSavingSlotOrUnits() {
        authorize();
        var request = request(InventoryType.PERSON_LIMIT, 2, null);
        when(slots.existsByServiceIdAndDateAndStartTime(serviceId, request.date(), request.startTime())).thenReturn(true);
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_already_exists"));
        noWrites();
    }

    @ParameterizedTest
    @MethodSource("invalidPersonCapacities")
    void personCapacityMustBePresentAndPositive(Integer capacity) {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.PERSON_LIMIT, capacity, null)))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_capacity"));
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
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_units_required"));
        noWrites();
    }

    private static Stream<Arguments> missingUnits() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(List.of()));
    }

    @ParameterizedTest
    @MethodSource("invalidUnits")
    void invalidUnitNumbersAndCapacitiesCannotReachPersistence(CreateSlotUnitRequest unit, String messageKey) {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId,
                request(InventoryType.SHARED_CAPACITY_UNITS, null, List.of(unit))))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo(messageKey));
        noWrites();
    }

    private static Stream<Arguments> invalidUnits() {
        return Stream.of(
                Arguments.of(new CreateSlotUnitRequest(null, 1), "error.slot_unit_number"),
                Arguments.of(new CreateSlotUnitRequest(0, 1), "error.slot_unit_number"),
                Arguments.of(new CreateSlotUnitRequest(-1, 1), "error.slot_unit_number"),
                Arguments.of(new CreateSlotUnitRequest(1, null), "error.slot_unit_capacity"),
                Arguments.of(new CreateSlotUnitRequest(1, 0), "error.slot_unit_capacity"),
                Arguments.of(new CreateSlotUnitRequest(1, -1), "error.slot_unit_capacity"));
    }

    @Test
    void duplicateUnitNumberCannotPersistEitherTheSlotOrItsPartialUnitList() {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.SHARED_CAPACITY_UNITS, null,
                List.of(new CreateSlotUnitRequest(1, 2), new CreateSlotUnitRequest(1, 3)))))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_duplicate_unit"));
        noWrites();
    }

    @Test
    void unitTotalOverflowFailsBeforeAnySlotOrUnitWrite() {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.SHARED_CAPACITY_UNITS, null,
                List.of(new CreateSlotUnitRequest(1, Integer.MAX_VALUE), new CreateSlotUnitRequest(2, 1)))))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_unit_capacity_overflow"));
        noWrites();
    }

    @Test
    void nullUnitIsRejectedAsInvalidInputBeforeAnyWrite() {
        authorize();
        assertThatThrownBy(() -> useCase.execute(userId, serviceId,
                request(InventoryType.SHARED_CAPACITY_UNITS, null, Collections.singletonList(null))))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_unit_number"));
        noWrites();
    }

    private CreateServiceSlotRequest request(InventoryType inventoryType, Integer capacity, List<CreateSlotUnitRequest> units) {
        return new CreateServiceSlotRequest(tomorrow, LocalTime.of(9, 0), LocalTime.of(10, 0), inventoryType, capacity, units, defaultOptionId);
    }

    private void vendor() {
        Vendor vendor = new Vendor();
        vendor.setId(vendorId);
        when(vendors.findByUserId(userId)).thenReturn(Optional.of(vendor));
    }

    @ParameterizedTest
    @MethodSource("nonPublishedStatuses")
    void cannotCreateSlotForNonPublishedService(ServiceStatus status) {
        vendor();
        when(services.findByIdForUpdate(serviceId))
                .thenReturn(Optional.of(Service.builder().id(serviceId).vendorId(vendorId).status(status).build()));

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, request(InventoryType.PERSON_LIMIT, 10, null)))
                .isInstanceOf(InvalidServiceStateException.class)
                .hasMessageContaining("Service must be PUBLISHED");
        noWrites();
    }

    static Stream<Arguments> nonPublishedStatuses() {
        return Stream.of(
                Arguments.of(ServiceStatus.DRAFT),
                Arguments.of(ServiceStatus.PENDING_REVIEW),
                Arguments.of(ServiceStatus.REJECTED),
                Arguments.of(ServiceStatus.PAUSED)
        );
    }

    private void authorize() {
        vendor();
        when(services.findByIdForUpdate(serviceId))
                .thenReturn(Optional.of(Service.builder().id(serviceId).vendorId(vendorId).status(ServiceStatus.PUBLISHED).build()));
    }

    private void noWrites() {
        verify(slots, never()).save(any());
        verify(slots, never()).saveUnits(any(), anyList());
    }

    @Test
    void slotCreationWithOptionIdBindsOptionSuccessfully() {
        authorize();
        UUID optionId = UUID.randomUUID();
        ServiceOption option = ServiceOption.builder()
                .id(optionId)
                .serviceId(serviceId)
                .optionType(OptionType.SHARED)
                .status(OptionStatus.ACTIVE)
                .build();
        when(optionsPort.findById(optionId)).thenReturn(Optional.of(option));
        when(slots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateServiceSlotRequest req = new CreateServiceSlotRequest(
                tomorrow, LocalTime.of(8, 0), LocalTime.of(9, 0),
                InventoryType.PERSON_LIMIT, 10, null, optionId);

        ServiceSlotResponse res = useCase.execute(userId, serviceId, req);

        assertThat(res.optionId()).isEqualTo(optionId);
    }

    @Test
    void slotCreationWithPrivateOptionRequiresSharedCapacityUnits() {
        authorize();
        UUID optionId = UUID.randomUUID();
        ServiceOption privateOption = ServiceOption.builder()
                .id(optionId)
                .serviceId(serviceId)
                .optionType(OptionType.PRIVATE)
                .status(OptionStatus.ACTIVE)
                .build();
        when(optionsPort.findById(optionId)).thenReturn(Optional.of(privateOption));

        // When inventoryType is PERSON_LIMIT for PRIVATE option -> throw exception
        CreateServiceSlotRequest req = new CreateServiceSlotRequest(
                tomorrow, LocalTime.of(8, 0), LocalTime.of(9, 0),
                InventoryType.PERSON_LIMIT, 10, null, optionId);

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, req))
                .isInstanceOfSatisfying(com.danasea.backend.shared.i18n.LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_private_requires_shared_capacity_units"));
    }

    @Test
    void slotCreationWithPrivateOptionAndSharedCapacityUnitsSucceeds() {
        authorize();
        UUID optionId = UUID.randomUUID();
        ServiceOption privateOption = ServiceOption.builder()
                .id(optionId)
                .serviceId(serviceId)
                .optionType(OptionType.PRIVATE)
                .status(OptionStatus.ACTIVE)
                .build();
        when(optionsPort.findById(optionId)).thenReturn(Optional.of(privateOption));
        when(slots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateServiceSlotRequest req = new CreateServiceSlotRequest(
                tomorrow, LocalTime.of(8, 0), LocalTime.of(9, 0),
                InventoryType.SHARED_CAPACITY_UNITS, null,
                List.of(new CreateSlotUnitRequest(1, 4)), optionId);

        ServiceSlotResponse res = useCase.execute(userId, serviceId, req);

        assertThat(res.optionId()).isEqualTo(optionId);
        assertThat(res.inventoryType()).isEqualTo(InventoryType.SHARED_CAPACITY_UNITS);
    }

    @Test
    void slotCreationWithoutOptionIdFails() {
        authorize();
        CreateServiceSlotRequest req = new CreateServiceSlotRequest(
                tomorrow, LocalTime.of(8, 0), LocalTime.of(9, 0),
                InventoryType.PERSON_LIMIT, 10, null, null);

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, req))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_option_required"));
    }

    @Test
    void slotCreationWithInactiveOptionFails() {
        authorize();
        UUID inactiveOptId = UUID.randomUUID();
        when(optionsPort.findById(inactiveOptId)).thenReturn(Optional.of(ServiceOption.builder()
                .id(inactiveOptId)
                .serviceId(serviceId)
                .status(OptionStatus.INACTIVE)
                .build()));

        CreateServiceSlotRequest req = new CreateServiceSlotRequest(
                tomorrow, LocalTime.of(8, 0), LocalTime.of(9, 0),
                InventoryType.PERSON_LIMIT, 10, null, inactiveOptId);

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, req))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_option_inactive"));
    }

    @Test
    void slotCreationWithOptionBelongingToDifferentServiceFails() {
        authorize();
        UUID otherOptId = UUID.randomUUID();
        when(optionsPort.findById(otherOptId)).thenReturn(Optional.of(ServiceOption.builder()
                .id(otherOptId)
                .serviceId(UUID.randomUUID())
                .status(OptionStatus.ACTIVE)
                .build()));

        CreateServiceSlotRequest req = new CreateServiceSlotRequest(
                tomorrow, LocalTime.of(8, 0), LocalTime.of(9, 0),
                InventoryType.PERSON_LIMIT, 10, null, otherOptId);

        assertThatThrownBy(() -> useCase.execute(userId, serviceId, req))
                .isInstanceOfSatisfying(LocalizedException.class,
                        error -> assertThat(error.getMessageRef().key()).isEqualTo("error.slot_option_not_owned"));
    }

    @Test
    void slotCreationMapsOptionTypeAndOptionNameToResponse() {
        authorize();
        when(slots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateServiceSlotRequest req = request(InventoryType.PERSON_LIMIT, 10, null);
        ServiceSlotResponse res = useCase.execute(userId, serviceId, req);

        assertThat(res.optionId()).isEqualTo(defaultOptionId);
        assertThat(res.optionType()).isEqualTo(OptionType.SHARED);
        assertThat(res.optionName()).isEqualTo("Standard Option");
    }
}

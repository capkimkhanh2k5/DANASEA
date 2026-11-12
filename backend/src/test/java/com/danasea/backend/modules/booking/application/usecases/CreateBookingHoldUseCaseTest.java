package com.danasea.backend.modules.booking.application.usecases;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.booking.application.dtos.BookingHoldItemDto;
import com.danasea.backend.modules.booking.application.dtos.BookingHoldResult;
import com.danasea.backend.modules.booking.application.dtos.CreateBookingHoldCommand;
import com.danasea.backend.modules.booking.domain.exceptions.InsufficientInventoryException;
import com.danasea.backend.modules.booking.domain.exceptions.SlotNotAvailableException;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.models.SlotValidationDetails;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
import com.danasea.backend.modules.booking.domain.ports.CustomerAccountPort;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateBookingHoldUseCaseTest {

    @Mock
    private BookingRepositoryPort bookingRepository;

    @Mock
    private InventoryLockPort inventoryLockPort;

    @Mock
    private ServiceSlotPort serviceSlotPort;

    private CreateBookingHoldUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateBookingHoldUseCase(bookingRepository, inventoryLockPort, serviceSlotPort);
    }

    @Test
    @DisplayName("Should successfully acquire lock and persist booking hold for multiple slots")
    void testCreateBookingHoldSuccess() {
        UUID customerId = UUID.randomUUID();
        UUID slotId1 = UUID.randomUUID();
        UUID slotId2 = UUID.randomUUID();
        UUID serviceId1 = UUID.randomUUID();
        UUID serviceId2 = UUID.randomUUID();
        UUID vendorId1 = UUID.randomUUID();
        UUID vendorId2 = UUID.randomUUID();

        SlotValidationDetails details1 = SlotValidationDetails.builder()
                .slotId(slotId1)
                .serviceId(serviceId1)
                .vendorId(vendorId1)
                .serviceName("Snorkeling Tour")
                .bookingDate(LocalDate.now().plusDays(2))
                .bookingTime(LocalTime.of(9, 0))
                .capacity(10)
                .bookedCount(2)
                .status(SlotStatus.OPEN)
                .price(new BigDecimal("100.00"))
                .servicePublished(true)
                .build();

        SlotValidationDetails details2 = SlotValidationDetails.builder()
                .slotId(slotId2)
                .serviceId(serviceId2)
                .vendorId(vendorId2)
                .serviceName("Jet Ski Adventure")
                .bookingDate(LocalDate.now().plusDays(2))
                .bookingTime(LocalTime.of(14, 0))
                .capacity(5)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .price(new BigDecimal("200.00"))
                .servicePublished(true)
                .build();

        when(serviceSlotPort.findSlotDetailsBatch(any())).thenReturn(List.of(details1, details2));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                customerId,
                List.of(
                        new BookingHoldItemDto(slotId1, 2),
                        new BookingHoldItemDto(slotId2, 1)
                )
        );

        BookingHoldResult result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.bookingId()).isNotNull();
        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.status()).isEqualTo(BookingStatus.HOLD);
        assertThat(result.totalAmount()).isEqualByComparingTo(new BigDecimal("400.00")); // (2*100) + (1*200)
        assertThat(result.items()).hasSize(2);
        assertThat(result.holdExpiresAt()).isNotNull();

        verify(inventoryLockPort).acquireHolds(eq(result.bookingId()), any(), eq(Duration.ofMinutes(5)));
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("Should aggregate quantities when duplicate slotId is provided in request")
    void testCreateBookingHoldAggregatesDuplicateSlots() {
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        SlotValidationDetails details = SlotValidationDetails.builder()
                .slotId(slotId)
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .serviceName("Boat Tour")
                .bookingDate(LocalDate.now().plusDays(1))
                .bookingTime(LocalTime.of(8, 0))
                .capacity(10)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .price(new BigDecimal("50.00"))
                .servicePublished(true)
                .build();

        when(serviceSlotPort.findSlotDetailsBatch(any())).thenReturn(List.of(details));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                customerId,
                List.of(
                        new BookingHoldItemDto(slotId, 2),
                        new BookingHoldItemDto(slotId, 3)
                )
        );

        BookingHoldResult result = useCase.execute(command);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(5);
        assertThat(result.totalAmount()).isEqualByComparingTo(new BigDecimal("250.00"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryLockItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryLockPort).acquireHolds(any(), captor.capture(), any());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should reject a request whose total quantity exceeds the bounded limit")
    void testRejectsExcessiveTotalQuantity() {
        UUID slotId = UUID.randomUUID();
        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                UUID.randomUUID(),
                List.of(
                        new BookingHoldItemDto(slotId, 60),
                        new BookingHoldItemDto(slotId, 41)));

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 100");

        verify(inventoryLockPort, never()).acquireHolds(any(), any(), any());
        verify(serviceSlotPort, never()).findSlotDetailsBatch(any());
    }

    @Test
    @DisplayName("Should throw SlotNotAvailableException when slot does not exist")
    void testSlotNotFound() {
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        when(serviceSlotPort.findSlotDetailsBatch(any())).thenReturn(List.of());

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                customerId,
                List.of(new BookingHoldItemDto(slotId, 1))
        );

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(SlotNotAvailableException.class)
                .hasMessageContaining("Slot does not exist");

        verify(inventoryLockPort, never()).acquireHolds(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw SlotNotAvailableException when service is not published")
    void testServiceNotPublished() {
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        SlotValidationDetails details = SlotValidationDetails.builder()
                .slotId(slotId)
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .bookingDate(LocalDate.now().plusDays(1))
                .bookingTime(LocalTime.of(10, 0))
                .capacity(10)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .price(new BigDecimal("100.00"))
                .servicePublished(false)
                .build();

        when(serviceSlotPort.findSlotDetailsBatch(any())).thenReturn(List.of(details));

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                customerId,
                List.of(new BookingHoldItemDto(slotId, 1))
        );

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(SlotNotAvailableException.class)
                .hasMessageContaining("Associated service is not published");

        verify(inventoryLockPort, never()).acquireHolds(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw SlotNotAvailableException when slot is CLOSED")
    void testSlotClosed() {
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        SlotValidationDetails details = SlotValidationDetails.builder()
                .slotId(slotId)
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .bookingDate(LocalDate.now().plusDays(1))
                .bookingTime(LocalTime.of(10, 0))
                .capacity(10)
                .bookedCount(0)
                .status(SlotStatus.CLOSED)
                .price(new BigDecimal("100.00"))
                .servicePublished(true)
                .build();

        when(serviceSlotPort.findSlotDetailsBatch(any())).thenReturn(List.of(details));

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                customerId,
                List.of(new BookingHoldItemDto(slotId, 1))
        );

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(SlotNotAvailableException.class)
                .hasMessageContaining("CLOSED");

        verify(inventoryLockPort, never()).acquireHolds(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw SlotNotAvailableException when slot date is in the past")
    void testSlotDateInPast() {
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        SlotValidationDetails details = SlotValidationDetails.builder()
                .slotId(slotId)
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .bookingDate(LocalDate.now().minusDays(1))
                .bookingTime(LocalTime.of(10, 0))
                .capacity(10)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .price(new BigDecimal("100.00"))
                .servicePublished(true)
                .build();

        when(serviceSlotPort.findSlotDetailsBatch(any())).thenReturn(List.of(details));

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                customerId,
                List.of(new BookingHoldItemDto(slotId, 1))
        );

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(SlotNotAvailableException.class)
                .hasMessageContaining("past");

        verify(inventoryLockPort, never()).acquireHolds(any(), any(), any());
    }

    @Test
    @DisplayName("Should propagate InsufficientInventoryException and not save booking when inventory lock fails")
    void testInsufficientInventoryPropagated() {
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        SlotValidationDetails details = SlotValidationDetails.builder()
                .slotId(slotId)
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .bookingDate(LocalDate.now().plusDays(1))
                .bookingTime(LocalTime.of(10, 0))
                .capacity(5)
                .bookedCount(4)
                .status(SlotStatus.OPEN)
                .price(new BigDecimal("100.00"))
                .servicePublished(true)
                .build();

        when(serviceSlotPort.findSlotDetailsBatch(any())).thenReturn(List.of(details));
        doThrow(new InsufficientInventoryException(slotId, 2, 1))
                .when(inventoryLockPort).acquireHolds(any(), any(), any());

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                customerId,
                List.of(new BookingHoldItemDto(slotId, 2))
        );

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InsufficientInventoryException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should rollback inventory lock when database save fails")
    void testCompensatingRollbackOnDatabaseFailure() {
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        SlotValidationDetails details = SlotValidationDetails.builder()
                .slotId(slotId)
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .bookingDate(LocalDate.now().plusDays(1))
                .bookingTime(LocalTime.of(10, 0))
                .capacity(10)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .price(new BigDecimal("100.00"))
                .servicePublished(true)
                .build();

        when(serviceSlotPort.findSlotDetailsBatch(any())).thenReturn(List.of(details));
        when(bookingRepository.save(any())).thenThrow(new RuntimeException("Database connection timeout"));

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                customerId,
                List.of(new BookingHoldItemDto(slotId, 1))
        );

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection timeout");

        verify(inventoryLockPort).acquireHolds(any(), any(), any());
        verify(inventoryLockPort).releaseHolds(any(), any());
    }

    @Test
    @DisplayName("Should invoke customer account validation with lock when port is configured")
    void testCustomerAccountValidationWithLock_WhenPortPresent() {
        CustomerAccountPort mockPort = org.mockito.Mockito.mock(CustomerAccountPort.class);
        useCase.setCustomerAccountPort(mockPort);

        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        SlotValidationDetails details = SlotValidationDetails.builder()
                .slotId(slotId)
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .bookingDate(LocalDate.now().plusDays(1))
                .bookingTime(LocalTime.of(10, 0))
                .capacity(10)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .price(new BigDecimal("100.00"))
                .servicePublished(true)
                .build();

        when(serviceSlotPort.findSlotDetailsBatch(any())).thenReturn(List.of(details));
        when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                customerId,
                List.of(new BookingHoldItemDto(slotId, 1))
        );

        BookingHoldResult result = useCase.execute(command);
        assertThat(result).isNotNull();
        verify(mockPort).validateCustomerForBookingWithLock(customerId);
    }

    @Test
    @DisplayName("Should reject booking creation when customer account validation fails (e.g. non-customer role)")
    void testCustomerAccountValidationFailure_RejectsBooking() {
        CustomerAccountPort mockPort = org.mockito.Mockito.mock(CustomerAccountPort.class);
        useCase.setCustomerAccountPort(mockPort);

        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        doThrow(new AccessDeniedException("Only customers are permitted to create bookings"))
                .when(mockPort).validateCustomerForBookingWithLock(customerId);

        CreateBookingHoldCommand command = new CreateBookingHoldCommand(
                customerId,
                List.of(new BookingHoldItemDto(slotId, 1))
        );

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only customers are permitted to create bookings");

        verify(serviceSlotPort, never()).findSlotDetailsBatch(any());
        verify(inventoryLockPort, never()).acquireHolds(any(), any(), any());
        verify(bookingRepository, never()).save(any());
    }
}

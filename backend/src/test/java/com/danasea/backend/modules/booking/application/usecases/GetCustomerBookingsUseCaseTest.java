package com.danasea.backend.modules.booking.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.booking.application.dtos.BookingSummaryResult;
import com.danasea.backend.modules.booking.application.dtos.GetCustomerBookingsQuery;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.booking.domain.models.BookingItem;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.domain.models.PagedResult;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCustomerBookingsUseCaseTest {

    @Mock
    private BookingRepositoryPort bookingRepository;

    private GetCustomerBookingsUseCase useCase;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetCustomerBookingsUseCase(bookingRepository);
    }

    private Booking createBooking(UUID id, BookingStatus status, int itemCount) {
        List<BookingItem> items = List.of(
                BookingItem.builder().id(UUID.randomUUID()).bookingId(id).quantity(2).build()
        );

        return Booking.builder()
                .id(id)
                .customerId(customerId)
                .status(status)
                .totalAmount(BigDecimal.valueOf(200000))
                .holdExpiresAt(OffsetDateTime.now().plusMinutes(15))
                .items(itemCount > 0 ? items : List.of())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("execute successfully returns paged bookings filtered by status")
    void execute_WithStatusFilter_ShouldReturnPagedResults() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = createBooking(bookingId, BookingStatus.CONFIRMED, 1);
        PagedResult<Booking> repoResult = new PagedResult<>(List.of(booking), 0, 10, 1L, 1);

        when(bookingRepository.findCustomerBookings(customerId, BookingStatus.CONFIRMED, 0, 10, "createdAt", "desc"))
                .thenReturn(repoResult);

        GetCustomerBookingsQuery query = new GetCustomerBookingsQuery(
                customerId,
                BookingStatus.CONFIRMED,
                0,
                10,
                "createdAt",
                "desc"
        );

        PagedResult<BookingSummaryResult> result = useCase.execute(query);

        assertThat(result).isNotNull();
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);

        BookingSummaryResult summary = result.content().get(0);
        assertThat(summary.bookingId()).isEqualTo(bookingId);
        assertThat(summary.customerId()).isEqualTo(customerId);
        assertThat(summary.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(summary.totalItems()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(200000));

        verify(bookingRepository).findCustomerBookings(customerId, BookingStatus.CONFIRMED, 0, 10, "createdAt", "desc");
    }

    @Test
    @DisplayName("execute successfully returns all bookings when status filter is null")
    void execute_WithoutStatusFilter_ShouldQueryAllStatuses() {
        PagedResult<Booking> repoResult = new PagedResult<>(List.of(), 0, 10, 0L, 0);

        when(bookingRepository.findCustomerBookings(customerId, null, 0, 10, "createdAt", "desc"))
                .thenReturn(repoResult);

        GetCustomerBookingsQuery query = new GetCustomerBookingsQuery(
                customerId,
                null,
                0,
                10,
                "createdAt",
                "desc"
        );

        PagedResult<BookingSummaryResult> result = useCase.execute(query);

        assertThat(result).isNotNull();
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);

        verify(bookingRepository).findCustomerBookings(customerId, null, 0, 10, "createdAt", "desc");
    }

    @Test
    @DisplayName("execute rejects invalid pagination values")
    void execute_RejectsInvalidPagination() {
        GetCustomerBookingsQuery query = new GetCustomerBookingsQuery(
                customerId,
                null,
                -5,
                0,
                null,
                null
        );

        assertThatThrownBy(() -> useCase.execute(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("execute throws IllegalArgumentException when customerId is null")
    void execute_WhenCustomerIdIsNull_ShouldThrowIllegalArgumentException() {
        GetCustomerBookingsQuery query = new GetCustomerBookingsQuery(null, null, 0, 10, "createdAt", "desc");
        assertThatThrownBy(() -> useCase.execute(query))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

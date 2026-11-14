package com.danasea.backend.modules.booking.application.usecases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.booking.application.dtos.BookingCancelResult;
import com.danasea.backend.modules.booking.application.dtos.CancelBookingCommand;
import com.danasea.backend.modules.booking.domain.exceptions.BookingNotFoundException;
import com.danasea.backend.modules.booking.domain.exceptions.InvalidBookingStateException;
import com.danasea.backend.modules.booking.domain.exceptions.UnauthorizedBookingAccessException;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.booking.domain.models.BookingItem;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.domain.models.CancellationFinancialResult;
import com.danasea.backend.modules.booking.domain.ports.BookingCancellationFinancialPort;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class CancelBookingUseCaseTest {

    @Mock
    private BookingRepositoryPort bookingRepository;

    @Mock
    private ServiceSlotPort serviceSlotPort;

    @Mock
    private InventoryLockPort inventoryLockPort;

    @Mock
    private BookingCancellationFinancialPort financialPort;

    private CancelBookingUseCase useCase;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID slotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new CancelBookingUseCase(
                bookingRepository, serviceSlotPort, inventoryLockPort, financialPort);
    }

    private Booking createBooking(BookingStatus status, LocalDate itemDate, LocalTime itemTime, BigDecimal totalAmount) {
        BookingItem item = BookingItem.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId)
                .serviceId(UUID.randomUUID())
                .vendorId(UUID.randomUUID())
                .slotId(slotId)
                .quantity(2)
                .bookingDate(itemDate)
                .bookingTime(itemTime)
                .price(totalAmount.divide(BigDecimal.valueOf(2)))
                .build();

        return Booking.builder()
                .id(bookingId)
                .customerId(customerId)
                .status(status)
                .totalAmount(totalAmount)
                .holdExpiresAt(OffsetDateTime.now().plusMinutes(15))
                .items(List.of(item))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Hủy sớm (> 24h) booking CONFIRMED: được hoàn 100% tiền và khôi phục capacity PostgreSQL")
    void execute_WhenConfirmedBookingCancelledMoreThan24hBefore_ShouldGrant100PercentRefundAndReleaseCapacity() {
        // Dịch vụ diễn ra sau 3 ngày nữa (> 24h tính từ hiện tại)
        LocalDate serviceDate = LocalDate.now().plusDays(3);
        LocalTime serviceTime = LocalTime.of(9, 0);
        BigDecimal totalAmount = BigDecimal.valueOf(500000);

        Booking booking = createBooking(BookingStatus.CONFIRMED, serviceDate, serviceTime, totalAmount);
        when(bookingRepository.findByIdWithItemsForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialPort.requestRefund(eq(bookingId), eq(customerId), any(), any()))
                .thenReturn(new CancellationFinancialResult(true, 100, totalAmount));

        CancelBookingCommand command = new CancelBookingCommand(bookingId, customerId, false, "Kế hoạch thay đổi");
        BookingCancelResult result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.bookingId()).isEqualTo(bookingId);
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.refundEligible()).isTrue();
        assertThat(result.refundPercentage()).isEqualTo(100);
        assertThat(result.refundAmount()).isEqualByComparingTo(totalAmount);
        assertThat(result.cancellationReason()).isEqualTo("Kế hoạch thay đổi");
        assertThat(result.message()).contains("refund request is pending");

        // Xác nhận hoàn trả sức chứa slot trong PostgreSQL
        verify(serviceSlotPort).releaseCapacityBatch(booking.getItems());
        // Không gọi release Redis hold vì booking đã được CONFIRMED trước đó
        verify(inventoryLockPort, never()).releaseHolds(any(), any());
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("Hủy trễ (< 24h) booking CONFIRMED: không được hoàn tiền (0%) nhưng vẫn khôi phục capacity PostgreSQL")
    void execute_WhenConfirmedBookingCancelledLessThan24hBefore_ShouldDenyRefundAndReleaseCapacity() {
        OffsetDateTime cancellationTime = OffsetDateTime.parse("2026-11-14T23:50:00+07:00");
        OffsetDateTime departure = cancellationTime.plusHours(2);
        BigDecimal totalAmount = BigDecimal.valueOf(500000);

        Booking booking = createBooking(
                BookingStatus.CONFIRMED, departure.toLocalDate(), departure.toLocalTime(), totalAmount);
        when(bookingRepository.findByIdWithItemsForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialPort.requestRefund(eq(bookingId), eq(customerId), any(), any()))
                .thenReturn(CancellationFinancialResult.noRefund());

        CancelBookingCommand command = new CancelBookingCommand(bookingId, customerId, false, null);
        BookingCancelResult result;
        try (var time = mockStatic(OffsetDateTime.class,
                withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS))) {
            time.when(OffsetDateTime::now).thenReturn(cancellationTime);
            result = useCase.execute(command);
        }

        assertThat(result).isNotNull();
        assertThat(result.bookingId()).isEqualTo(bookingId);
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.earliestServiceTime()).isEqualTo(departure);
        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundPercentage()).isEqualTo(0);
        assertThat(result.refundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        // Fallback lý do mặc định
        assertThat(result.cancellationReason()).isEqualTo("Customer requested cancellation");
        assertThat(result.message()).contains("does not provide a refund");

        // Vẫn phải nhả slot cho người khác đặt
        verify(serviceSlotPort).releaseCapacityBatch(booking.getItems());
        verify(inventoryLockPort, never()).releaseHolds(any(), any());
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("Hủy booking ở trạng thái HOLD: giải phóng Redis lock và không hoàn tiền vì chưa thanh toán")
    void execute_WhenHoldBookingCancelled_ShouldReleaseRedisHolds() {
        LocalDate serviceDate = LocalDate.now().plusDays(2);
        LocalTime serviceTime = LocalTime.of(10, 0);
        BigDecimal totalAmount = BigDecimal.valueOf(300000);

        Booking booking = createBooking(BookingStatus.HOLD, serviceDate, serviceTime, totalAmount);
        when(bookingRepository.findByIdWithItemsForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CancelBookingCommand command = new CancelBookingCommand(bookingId, customerId, false, "Đổi ý không đặt nữa");
        BookingCancelResult result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.refundEligible()).isFalse();
        assertThat(result.refundAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // Nhả Redis hold
        verify(inventoryLockPort).releaseHolds(eq(bookingId), any());
        // Không cần nhả capacity Postgres vì chưa commit
        verify(serviceSlotPort, never()).releaseCapacityBatch(any());
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("Chống IDOR: Khách hàng khác cố hủy booking của người khác -> ném UnauthorizedBookingAccessException")
    void execute_WhenCallerNotOwnerAndNotAdmin_ShouldThrowUnauthorizedBookingAccessException() {
        Booking booking = createBooking(BookingStatus.CONFIRMED, LocalDate.now().plusDays(2), LocalTime.of(10, 0), BigDecimal.valueOf(300000));
        when(bookingRepository.findByIdWithItemsForUpdate(bookingId)).thenReturn(Optional.of(booking));

        CancelBookingCommand command = new CancelBookingCommand(bookingId, otherUserId, false, "Hủy trộm");

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(UnauthorizedBookingAccessException.class);

        verify(bookingRepository, never()).save(any());
        verify(serviceSlotPort, never()).releaseCapacityBatch(any());
    }

    @Test
    @DisplayName("Admin bypass IDOR: Admin có thể hủy booking của bất kỳ khách hàng nào")
    void execute_WhenCallerIsAdmin_ShouldBypassOwnershipCheckAndCancel() {
        Booking booking = createBooking(BookingStatus.CONFIRMED, LocalDate.now().plusDays(2), LocalTime.of(10, 0), BigDecimal.valueOf(300000));
        when(bookingRepository.findByIdWithItemsForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialPort.requestRefund(eq(bookingId), eq(otherUserId), any(), any()))
                .thenReturn(new CancellationFinancialResult(true, 100, BigDecimal.valueOf(300000)));

        // otherUserId nhưng isAdmin = true
        CancelBookingCommand command = new CancelBookingCommand(bookingId, otherUserId, true, "Admin can thiệp theo yêu cầu khách");
        BookingCancelResult result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("Hủy booking đã bị CANCELLED từ trước: ném InvalidBookingStateException")
    void execute_WhenBookingAlreadyCancelled_ShouldThrowInvalidBookingStateException() {
        Booking booking = createBooking(BookingStatus.CANCELLED, LocalDate.now().plusDays(2), LocalTime.of(10, 0), BigDecimal.valueOf(300000));
        when(bookingRepository.findByIdWithItemsForUpdate(bookingId)).thenReturn(Optional.of(booking));

        CancelBookingCommand command = new CancelBookingCommand(bookingId, customerId, false, "Hủy tiếp");

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("already cancelled");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Booking không tồn tại: ném BookingNotFoundException")
    void execute_WhenBookingNotFound_ShouldThrowBookingNotFoundException() {
        when(bookingRepository.findByIdWithItemsForUpdate(bookingId)).thenReturn(Optional.empty());

        CancelBookingCommand command = new CancelBookingCommand(bookingId, customerId, false, "Hủy");

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    @DisplayName("Validation đầu vào: bookingId null ném IllegalArgumentException")
    void execute_WhenBookingIdIsNull_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(IllegalArgumentException.class);

        CancelBookingCommand command = new CancelBookingCommand(null, customerId, false, null);
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

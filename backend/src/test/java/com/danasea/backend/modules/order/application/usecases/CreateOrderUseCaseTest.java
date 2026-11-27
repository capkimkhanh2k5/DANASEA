package com.danasea.backend.modules.order.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.danasea.backend.modules.order.application.dtos.CreateOrderCommand;
import com.danasea.backend.modules.order.application.dtos.MasterOrderDetailResult;
import com.danasea.backend.modules.order.domain.exceptions.BookingNotEligibleForOrderException;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.ports.BookingLookupPort;
import com.danasea.backend.modules.order.domain.ports.BookingOrderView;
import com.danasea.backend.modules.order.domain.ports.BookingStatusUpdatePort;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.OrderEventPublisherPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateOrderUseCase Unit Tests")
class CreateOrderUseCaseTest {

    @Mock
    private MasterOrderRepositoryPort masterOrderRepository;
    @Mock
    private SubOrderRepositoryPort subOrderRepository;
    @Mock
    private BookingLookupPort bookingLookupPort;
    @Mock
    private BookingStatusUpdatePort bookingStatusUpdatePort;
    @Mock
    private CommissionPolicyPort commissionPolicyPort;
    @Mock
    private OrderEventPublisherPort orderEventPublisherPort;

    private CreateOrderUseCase useCase;

    private final UUID customerId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final String idempotencyKey = "order-create-idem-key-12345";

    @BeforeEach
    void setUp() {
        useCase = new CreateOrderUseCase(
                masterOrderRepository,
                subOrderRepository,
                bookingLookupPort,
                bookingStatusUpdatePort,
                commissionPolicyPort,
                orderEventPublisherPort
        );
    }

    @Nested
    @DisplayName("Thành công")
    class SuccessCases {

        @Test
        @DisplayName("Tạo đơn hàng thành công từ Booking HOLD: Tách SubOrder 1:1, tính hoa hồng 10%, cập nhật Booking sang PENDING_PAYMENT")
        void shouldCreateOrderSuccessfully() {
            // Given
            UUID bookingItemId1 = UUID.randomUUID();
            UUID bookingItemId2 = UUID.randomUUID();
            UUID vendorId = UUID.randomUUID();

            BookingOrderView.BookingItemOrderView item1 = new BookingOrderView.BookingItemOrderView(
                    bookingItemId1, vendorId, UUID.randomUUID(), UUID.randomUUID(), 2, new BigDecimal("100.00"));
            BookingOrderView.BookingItemOrderView item2 = new BookingOrderView.BookingItemOrderView(
                    bookingItemId2, vendorId, UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("300.00"));

            BookingOrderView bookingView = new BookingOrderView(
                    bookingId,
                    customerId,
                    "HOLD",
                    new BigDecimal("500.00"),
                    OffsetDateTime.now().plusMinutes(15),
                    List.of(item1, item2)
            );

            when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(bookingLookupPort.findBookingForOrderForUpdate(bookingId)).thenReturn(Optional.of(bookingView));
            when(commissionPolicyPort.getCommissionRate(vendorId)).thenReturn(new BigDecimal("0.10"));

            when(masterOrderRepository.save(any(MasterOrder.class))).thenAnswer(invocation -> {
                MasterOrder arg = invocation.getArgument(0);
                arg.setId(UUID.randomUUID());
                return arg;
            });
            when(subOrderRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            CreateOrderCommand command = new CreateOrderCommand(customerId, bookingId, idempotencyKey);
            MasterOrderDetailResult result = useCase.execute(command);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.customerId()).isEqualTo(customerId);
            assertThat(result.bookingId()).isEqualTo(bookingId);
            assertThat(result.totalAmount()).isEqualByComparingTo("500.00");
            assertThat(result.status()).isEqualTo(MasterOrderStatus.PENDING_PAYMENT);
            assertThat(result.paymentStatus()).isEqualTo(PaymentOrderStatus.UNPAID);
            assertThat(result.subOrders()).hasSize(2);

            // Xác nhận tỷ lệ hoa hồng 10%
            assertThat(result.subOrders().get(0).commissionRate()).isEqualByComparingTo("0.10");
            assertThat(result.subOrders().get(0).commissionAmount()).isEqualByComparingTo("20.00"); // 200 * 0.10
            assertThat(result.subOrders().get(0).vendorPayoutAmount()).isEqualByComparingTo("180.00");

            verify(bookingStatusUpdatePort).updateStatusToPendingPayment(bookingId);
            verify(orderEventPublisherPort).publishOrderCreatedEvent(any(MasterOrder.class));
        }

        @Test
        @DisplayName("Idempotency theo bookingId: Trả về đơn hàng cũ mà không tạo mới")
        void shouldReturnExistingOrderWhenCalledWithSameBookingId() {
            // Given
            MasterOrder existing = MasterOrder.createFromBooking(
                    customerId, bookingId, new BigDecimal("500.00"), OffsetDateTime.now().plusMinutes(10), idempotencyKey);
            existing.setId(UUID.randomUUID());

            when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.of(existing));
            when(subOrderRepository.findByMasterOrderId(existing.getId())).thenReturn(List.of());

            // When
            CreateOrderCommand command = new CreateOrderCommand(customerId, bookingId, idempotencyKey);
            MasterOrderDetailResult result = useCase.execute(command);

            // Then
            assertThat(result.id()).isEqualTo(existing.getId());
            verify(bookingLookupPort, never()).findBookingForOrderForUpdate(any());
            verify(masterOrderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Lỗi xác thực & Vi phạm trạng thái")
    class FailureCases {

        @Test
        @DisplayName("Chặn IDOR (403): Khách hàng không sở hữu booking này")
        void shouldThrowUnauthorizedAccessWhenCustomerDoesNotOwnBooking() {
            UUID differentCustomer = UUID.randomUUID();
            BookingOrderView bookingView = new BookingOrderView(
                    bookingId,
                    differentCustomer,
                    "HOLD",
                    new BigDecimal("500.00"),
                    OffsetDateTime.now().plusMinutes(15),
                    List.of()
            );

            when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(bookingLookupPort.findBookingForOrderForUpdate(bookingId)).thenReturn(Optional.of(bookingView));

            CreateOrderCommand command = new CreateOrderCommand(customerId, bookingId, idempotencyKey);

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(UnauthorizedOrderAccessException.class);
        }

        @Test
        @DisplayName("Trạng thái booking không phải HOLD (409): Ném BookingNotEligibleForOrderException")
        void shouldThrowExceptionWhenBookingStatusIsNotHold() {
            BookingOrderView bookingView = new BookingOrderView(
                    bookingId,
                    customerId,
                    "CONFIRMED",
                    new BigDecimal("500.00"),
                    OffsetDateTime.now().plusMinutes(15),
                    List.of()
            );

            when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(bookingLookupPort.findBookingForOrderForUpdate(bookingId)).thenReturn(Optional.of(bookingView));

            CreateOrderCommand command = new CreateOrderCommand(customerId, bookingId, idempotencyKey);

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(BookingNotEligibleForOrderException.class)
                    .hasMessageContaining("Booking is not in HOLD status");
        }

        @Test
        @DisplayName("Booking hold đã quá hạn TTL (409): Ném BookingNotEligibleForOrderException")
        void shouldThrowExceptionWhenBookingHoldHasExpired() {
            BookingOrderView bookingView = new BookingOrderView(
                    bookingId,
                    customerId,
                    "HOLD",
                    new BigDecimal("500.00"),
                    OffsetDateTime.now().minusMinutes(2), // đã quá hạn 2 phút
                    List.of()
            );

            when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(bookingLookupPort.findBookingForOrderForUpdate(bookingId)).thenReturn(Optional.of(bookingView));

            CreateOrderCommand command = new CreateOrderCommand(customerId, bookingId, idempotencyKey);

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(BookingNotEligibleForOrderException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Trùng idempotencyKey nhưng khác bookingId (409): Ném InvalidOrderStateException")
        void shouldThrowExceptionWhenIdempotencyKeyReusedForDifferentBooking() {
            UUID otherBookingId = UUID.randomUUID();
            MasterOrder existing = MasterOrder.createFromBooking(
                    customerId, otherBookingId, new BigDecimal("500.00"), OffsetDateTime.now().plusMinutes(10), idempotencyKey);

            when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)).thenReturn(Optional.of(existing));

            CreateOrderCommand command = new CreateOrderCommand(customerId, bookingId, idempotencyKey);

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("idempotency key was already used for another booking");
        }

        @Test
        @DisplayName("Chặn IDOR trên nhánh idempotent bookingId (403): Order cũ thuộc về khách hàng khác")
        void shouldThrowUnauthorizedAccessWhenExistingOrderByBookingBelongsToAnotherCustomer() {
            UUID otherCustomer = UUID.randomUUID();
            MasterOrder existing = MasterOrder.createFromBooking(
                    otherCustomer, bookingId, new BigDecimal("500.00"), OffsetDateTime.now().plusMinutes(10), idempotencyKey);

            when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.of(existing));

            CreateOrderCommand command = new CreateOrderCommand(customerId, bookingId, idempotencyKey);

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(UnauthorizedOrderAccessException.class);
            verify(bookingLookupPort, never()).findBookingForOrderForUpdate(any());
        }

        @Test
        @DisplayName("Chặn IDOR trên nhánh idempotent key (403): Order cũ thuộc về khách hàng khác")
        void shouldThrowUnauthorizedAccessWhenExistingOrderByKeyBelongsToAnotherCustomer() {
            UUID otherCustomer = UUID.randomUUID();
            MasterOrder existing = MasterOrder.createFromBooking(
                    otherCustomer, bookingId, new BigDecimal("500.00"), OffsetDateTime.now().plusMinutes(10), idempotencyKey);

            when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)).thenReturn(Optional.of(existing));

            CreateOrderCommand command = new CreateOrderCommand(customerId, bookingId, idempotencyKey);

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(UnauthorizedOrderAccessException.class);
        }
    }
}

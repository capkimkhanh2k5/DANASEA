package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.dtos.CreateOrderCommand;
import com.danasea.backend.modules.order.application.dtos.MasterOrderDetailResult;
import com.danasea.backend.modules.order.application.usecases.CreateOrderUseCase;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.ports.BookingLookupPort;
import com.danasea.backend.modules.order.domain.ports.BookingOrderView;
import com.danasea.backend.modules.order.domain.ports.BookingStatusUpdatePort;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.OrderEventPublisherPort;
import com.danasea.backend.modules.order.domain.ports.ServiceWaiverLookupPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.dtos.OrderResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Waiver Snapshot Tests")
class OrderWaiverSnapshotTest {

    @Mock private MasterOrderRepositoryPort masterOrderRepository;
    @Mock private SubOrderRepositoryPort subOrderRepository;
    @Mock private BookingLookupPort bookingLookupPort;
    @Mock private BookingStatusUpdatePort bookingStatusUpdatePort;
    @Mock private CommissionPolicyPort commissionPolicyPort;
    @Mock private OrderEventPublisherPort orderEventPublisherPort;
    @Mock private ServiceWaiverLookupPort serviceWaiverLookupPort;

    @Mock private JpaBookingRepository jpaBookingRepository;
    @Mock private JpaMasterOrderRepository jpaMasterOrderRepository;
    @Mock private JpaSubOrderRepository jpaSubOrderRepository;

    private CreateOrderUseCase createOrderUseCase;
    private OrderPaymentService orderPaymentService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID bookingItemId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        createOrderUseCase =
                new CreateOrderUseCase(
                        masterOrderRepository,
                        subOrderRepository,
                        bookingLookupPort,
                        bookingStatusUpdatePort,
                        commissionPolicyPort,
                        orderEventPublisherPort,
                        null,
                        null,
                        null,
                        null,
                        serviceWaiverLookupPort);

        orderPaymentService =
                new OrderPaymentService(
                        jpaBookingRepository,
                        jpaMasterOrderRepository,
                        jpaSubOrderRepository,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        orderPaymentService.setServiceWaiverLookupPort(serviceWaiverLookupPort);
        orderPaymentService.setConfiguredCommission(commissionPolicyPort);
        when(commissionPolicyPort.getCommissionRate(any())).thenReturn(new BigDecimal("0.10"));
    }

    @Test
    @DisplayName("CreateOrderUseCase: Ghi nhận đúng snapshot cam kết vào SubOrder khi tạo đơn")
    void createOrderUseCase_shouldPersistWaiverSnapshot() {
        BookingOrderView.BookingItemOrderView itemView =
                new BookingOrderView.BookingItemOrderView(
                        bookingItemId,
                        vendorId,
                        serviceId,
                        UUID.randomUUID(),
                        2,
                        BigDecimal.valueOf(500000));
        BookingOrderView bookingView =
                new BookingOrderView(
                        bookingId,
                        customerId,
                        "HOLD",
                        BigDecimal.valueOf(1000000),
                        OffsetDateTime.now().plusMinutes(15),
                        List.of(itemView));

        when(bookingLookupPort.findBookingForOrderForUpdate(bookingId))
                .thenReturn(Optional.of(bookingView));
        when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
        when(masterOrderRepository.findByCustomerIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        when(masterOrderRepository.save(any(MasterOrder.class)))
                .thenAnswer(
                        inv -> {
                            MasterOrder o = inv.getArgument(0);
                            o.setId(UUID.randomUUID());
                            return o;
                        });

        when(serviceWaiverLookupPort.findWaiverSnapshot(serviceId))
                .thenReturn(
                        Optional.of(
                                new ServiceWaiverLookupPort.ServiceWaiverSnapshot(
                                        serviceId,
                                        "Tour Lặn Biển",
                                        true,
                                        1,
                                        "Cam kết snapshot VI lúc tạo đơn",
                                        "Snapshot EN at order creation")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubOrder>> subOrdersCaptor = ArgumentCaptor.forClass(List.class);
        when(subOrderRepository.saveAll(subOrdersCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        MasterOrderDetailResult result =
                createOrderUseCase.execute(
                        new CreateOrderCommand(customerId, bookingId, "idem-key-1", null));

        assertThat(result).isNotNull();
        List<SubOrder> savedSubOrders = subOrdersCaptor.getValue();
        assertThat(savedSubOrders).hasSize(1);
        SubOrder savedSubOrder = savedSubOrders.get(0);

        assertThat(savedSubOrder.getWaiverRequired()).isTrue();
        assertThat(savedSubOrder.getWaiverVersion()).isEqualTo(1);
        assertThat(savedSubOrder.getWaiverContent()).isEqualTo("Cam kết snapshot VI lúc tạo đơn");
        assertThat(savedSubOrder.getWaiverContentEn()).isEqualTo("Snapshot EN at order creation");
        assertThat(savedSubOrder.getWaiverAccepted()).isFalse();
    }

    @Test
    @DisplayName(
            "OrderPaymentService.createOrder: Ghi nhận đúng snapshot cam kết vào SubOrderJpaEntity"
                    + " khi tạo đơn")
    void orderPaymentService_shouldPersistWaiverSnapshot() {
        BookingItemJpaEntity itemEntity = new BookingItemJpaEntity();
        itemEntity.setId(bookingItemId);
        itemEntity.setServiceId(serviceId);
        itemEntity.setVendorId(vendorId);
        itemEntity.setQuantity(1);
        itemEntity.setPrice(BigDecimal.valueOf(500000));

        BookingJpaEntity bookingEntity = new BookingJpaEntity();
        bookingEntity.setId(bookingId);
        bookingEntity.setCustomerId(customerId);
        bookingEntity.setStatus(BookingStatus.HOLD);
        bookingEntity.setTotalAmount(BigDecimal.valueOf(500000));
        bookingEntity.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(15));
        bookingEntity.setItems(List.of(itemEntity));

        when(jpaBookingRepository.findByIdWithItemsForUpdate(bookingId))
                .thenReturn(Optional.of(bookingEntity));
        when(jpaMasterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
        when(jpaMasterOrderRepository.findByCustomerIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        when(jpaMasterOrderRepository.save(any(MasterOrderJpaEntity.class)))
                .thenAnswer(
                        inv -> {
                            MasterOrderJpaEntity m = inv.getArgument(0);
                            m.setId(UUID.randomUUID());
                            return m;
                        });

        when(serviceWaiverLookupPort.findWaiverSnapshot(serviceId))
                .thenReturn(
                        Optional.of(
                                new ServiceWaiverLookupPort.ServiceWaiverSnapshot(
                                        serviceId,
                                        "Tour Lặn Biển",
                                        true,
                                        2,
                                        "Cam kết snapshot V2",
                                        "Waiver snapshot V2")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubOrderJpaEntity>> subOrdersCaptor =
                ArgumentCaptor.forClass(List.class);
        when(jpaSubOrderRepository.saveAll(subOrdersCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response =
                orderPaymentService.createOrder(customerId, bookingId, "idem-key-ops-1");

        assertThat(response).isNotNull();
        List<SubOrderJpaEntity> savedSubOrders = subOrdersCaptor.getValue();
        assertThat(savedSubOrders).hasSize(1);
        SubOrderJpaEntity savedSubOrder = savedSubOrders.get(0);

        assertThat(savedSubOrder.getWaiverRequired()).isTrue();
        assertThat(savedSubOrder.getWaiverVersion()).isEqualTo(2);
        assertThat(savedSubOrder.getWaiverContent()).isEqualTo("Cam kết snapshot V2");
        assertThat(savedSubOrder.getWaiverContentEn()).isEqualTo("Waiver snapshot V2");
        assertThat(savedSubOrder.getWaiverAccepted()).isFalse();
    }
}

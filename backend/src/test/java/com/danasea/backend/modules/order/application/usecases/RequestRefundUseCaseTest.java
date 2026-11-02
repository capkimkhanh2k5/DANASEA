package com.danasea.backend.modules.order.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.order.application.dtos.OrderRefundResult;
import com.danasea.backend.modules.order.application.dtos.RequestRefundCommand;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestRefundUseCase Unit Tests")
class RequestRefundUseCaseTest {

    @Mock
    private MasterOrderRepositoryPort masterOrderRepository;
    @Mock
    private SubOrderRepositoryPort subOrderRepository;
    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    private final RefundPolicyEngine refundPolicyEngine = new RefundPolicyEngine();
    private RequestRefundUseCase useCase;
    private JpaRefundRepository refunds;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID subOrderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        refunds = mock(JpaRefundRepository.class);
        org.mockito.Mockito.lenient().when(refunds.save(any())).thenAnswer(inv -> {
            RefundJpaEntity refund = inv.getArgument(0); refund.setId(UUID.randomUUID()); return refund;
        });
        useCase = new RequestRefundUseCase(
                masterOrderRepository,
                subOrderRepository,
                paymentGatewayPort,
                refundPolicyEngine, null, refunds, null, null
        );
    }

    @Test
    @DisplayName("Thành công: Hủy sớm >= 24h trước giờ khởi hành -> Hoàn 100% tiền")
    void shouldRefundFullAmountWhenCancelledMoreThan24hBeforeDeparture() {
        MasterOrder order = MasterOrder.createFromBooking(
                customerId, UUID.randomUUID(), new BigDecimal("100.00"), OffsetDateTime.now(), "key-1");
        order.setId(orderId);
        order.setStatus(MasterOrderStatus.PAID);

        SubOrder subOrder = new SubOrder();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(orderId);
        subOrder.setSubtotalAmount(new BigDecimal("100.00"));
        subOrder.setStatus(SubOrderStatus.CONFIRMED);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByMasterOrderIdForUpdate(orderId)).thenReturn(List.of(subOrder));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime departureTime = now.plusHours(72); // Hủy trước 72h (> 48h) -> 100%

        RequestRefundCommand command = new RequestRefundCommand(
                customerId, orderId, RefundReason.CUSTOMER_CANCEL, "idem-ref-1", now);

        List<OrderRefundResult> results = useCase.execute(command, departureTime);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(results.get(0).refundPercentage()).isEqualByComparingTo("100.0");
        assertThat(results.get(0).status()).isEqualTo(RefundStatus.PENDING);
        var persisted = org.mockito.ArgumentCaptor.forClass(RefundJpaEntity.class);
        verify(refunds).save(persisted.capture());
        assertThat(persisted.getValue().getSubOrderId()).isEqualTo(subOrderId);
        assertThat(persisted.getValue().getRequestedBy()).isEqualTo(customerId);
        assertThat(persisted.getValue().getIdempotencyKey()).isEqualTo("idem-ref-1");
        assertThat(persisted.getValue().getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(persisted.getValue().getAmount()).isEqualByComparingTo("100.00");
        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    @DisplayName("Hủy trễ < 2h trước giờ khởi hành: Mất toàn bộ tiền (0%), ném InvalidOrderStateException")
    void shouldThrowInvalidStateWhenCancelledLessThan2hBeforeDeparture() {
        MasterOrder order = MasterOrder.createFromBooking(
                customerId, UUID.randomUUID(), new BigDecimal("100.00"), OffsetDateTime.now(), "key-1");
        order.setId(orderId);
        order.setStatus(MasterOrderStatus.PAID);

        SubOrder subOrder = new SubOrder();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(orderId);
        subOrder.setSubtotalAmount(new BigDecimal("100.00"));
        subOrder.setStatus(SubOrderStatus.CONFIRMED);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByMasterOrderIdForUpdate(orderId)).thenReturn(List.of(subOrder));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime departureTime = now.plusMinutes(30); // Hủy sát giờ (< 2h) -> 0% hoàn

        RequestRefundCommand command = new RequestRefundCommand(
                customerId, orderId, RefundReason.CUSTOMER_CANCEL, "idem-ref-2", now);

        assertThatThrownBy(() -> useCase.execute(command, departureTime))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("does not allow a refund");
    }

    @Test
    @DisplayName("Chặn IDOR (403): Khách hàng không sở hữu đơn yêu cầu hoàn tiền")
    void shouldThrowUnauthorizedWhenCustomerDoesNotOwnOrder() {
        UUID strangerId = UUID.randomUUID();
        MasterOrder order = MasterOrder.createFromBooking(
                customerId, UUID.randomUUID(), new BigDecimal("100.00"), OffsetDateTime.now(), "key-1");
        order.setId(orderId);
        order.setStatus(MasterOrderStatus.PAID);

        when(masterOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        RequestRefundCommand command = new RequestRefundCommand(
                strangerId, orderId, RefundReason.CUSTOMER_CANCEL, "idem-ref-3", LocalDateTime.now());

        assertThatThrownBy(() -> useCase.execute(command, LocalDateTime.now().plusHours(48)))
                .isInstanceOf(UnauthorizedOrderAccessException.class);
    }
}

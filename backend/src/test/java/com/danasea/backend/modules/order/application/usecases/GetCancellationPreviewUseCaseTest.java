package com.danasea.backend.modules.order.application.usecases;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.danasea.backend.modules.order.application.dtos.CancellationPreviewResult;
import com.danasea.backend.modules.order.application.dtos.GetCancellationPreviewQuery;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.ServiceSlotDepartureLookupPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCancellationPreviewUseCase Unit Tests")
class GetCancellationPreviewUseCaseTest {

    @Mock
    private MasterOrderRepositoryPort masterOrderRepository;

    @Mock
    private SubOrderRepositoryPort subOrderRepository;

    @Mock
    private ServiceSlotDepartureLookupPort serviceSlotDepartureLookupPort;

    @Spy
    private RefundPolicyEngine refundPolicyEngine = new RefundPolicyEngine();

    private GetCancellationPreviewUseCase useCase;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID masterOrderId;
    private UUID subOrderId;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        useCase = new GetCancellationPreviewUseCase(
                masterOrderRepository,
                subOrderRepository,
                serviceSlotDepartureLookupPort,
                refundPolicyEngine
        );

        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        masterOrderId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();
        slotId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Owner previews cancellation by MasterOrder ID -> returns preview with aggregated items")
    void preview_ByMasterOrderId_AsOwner_Success() {
        MasterOrder order = new MasterOrder();
        order.setId(masterOrderId);
        order.setCustomerId(customerId);
        order.setStatus(MasterOrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("1000000"));

        SubOrder subOrder = new SubOrder();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setSubtotalAmount(new BigDecimal("1000000"));

        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of(subOrder));
        when(serviceSlotDepartureLookupPort.findDepartureTime(slotId))
                .thenReturn(Optional.of(LocalDateTime.now().plusDays(5)));

        GetCancellationPreviewQuery query = new GetCancellationPreviewQuery(customerId, masterOrderId, false);
        CancellationPreviewResult result = useCase.execute(query);

        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(masterOrderId);
        assertThat(result.originalAmount()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(result.refundAmount()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(result.items()).hasSize(1);
    }

    @Test
    @DisplayName("Preview by SubOrder ID -> looks up MasterOrder and returns preview")
    void preview_BySubOrderId_Success() {
        MasterOrder order = new MasterOrder();
        order.setId(masterOrderId);
        order.setCustomerId(customerId);
        order.setStatus(MasterOrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("500000"));

        SubOrder subOrder = new SubOrder();
        subOrder.setId(subOrderId);
        subOrder.setMasterOrderId(masterOrderId);
        subOrder.setSlotId(slotId);
        subOrder.setStatus(SubOrderStatus.CONFIRMED);
        subOrder.setSubtotalAmount(new BigDecimal("500000"));

        when(masterOrderRepository.findById(subOrderId)).thenReturn(Optional.empty());
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(order));
        when(serviceSlotDepartureLookupPort.findDepartureTime(slotId))
                .thenReturn(Optional.of(LocalDateTime.now().plusDays(3)));

        GetCancellationPreviewQuery query = new GetCancellationPreviewQuery(customerId, subOrderId, false);
        CancellationPreviewResult result = useCase.execute(query);

        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(subOrderId);
        assertThat(result.items()).hasSize(1);
        assertThat(result.originalAmount()).isEqualByComparingTo(new BigDecimal("500000"));
    }

    @Test
    @DisplayName("Non-owner non-admin user previews someone else's order -> throws AccessDeniedException")
    void preview_AsNonOwner_ThrowsAccessDeniedException() {
        MasterOrder order = new MasterOrder();
        order.setId(masterOrderId);
        order.setCustomerId(customerId);

        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(order));

        GetCancellationPreviewQuery query = new GetCancellationPreviewQuery(otherCustomerId, masterOrderId, false);
        assertThatThrownBy(() -> useCase.execute(query))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission");
    }

    @Test
    @DisplayName("Admin user previews someone else's order -> bypasses IDOR check and succeeds")
    void preview_AsAdmin_BypassesIdorCheck() {
        MasterOrder order = new MasterOrder();
        order.setId(masterOrderId);
        order.setCustomerId(customerId);
        order.setStatus(MasterOrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("200000"));

        when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByMasterOrderId(masterOrderId)).thenReturn(List.of());

        GetCancellationPreviewQuery query = new GetCancellationPreviewQuery(otherCustomerId, masterOrderId, true);
        CancellationPreviewResult result = useCase.execute(query);

        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(masterOrderId);
    }

    @Test
    @DisplayName("Order not found by ID -> throws OrderNotFoundException")
    void preview_OrderNotFound_ThrowsOrderNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(masterOrderRepository.findById(unknownId)).thenReturn(Optional.empty());
        when(subOrderRepository.findById(unknownId)).thenReturn(Optional.empty());

        GetCancellationPreviewQuery query = new GetCancellationPreviewQuery(customerId, unknownId, false);
        assertThatThrownBy(() -> useCase.execute(query))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Null query or missing orderId -> throws IllegalArgumentException")
    void preview_NullQuery_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(IllegalArgumentException.class);

        GetCancellationPreviewQuery emptyQuery = new GetCancellationPreviewQuery(customerId, null, false);
        assertThatThrownBy(() -> useCase.execute(emptyQuery))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vendorPreviewRejectsForeignSubOrderBeforeLoadingItsMasterOrder() {
        var sub = new SubOrder();
        sub.setId(subOrderId);
        sub.setMasterOrderId(masterOrderId);
        sub.setVendorId(UUID.randomUUID());
        when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> useCase.execute(
                new GetCancellationPreviewQuery(otherCustomerId, subOrderId, false, UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(masterOrderRepository);
    }
}

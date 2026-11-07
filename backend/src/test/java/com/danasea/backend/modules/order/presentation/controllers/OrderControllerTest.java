package com.danasea.backend.modules.order.presentation.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.dtos.CancellationPreviewResult.SubOrderCancellationPreviewResult;
import com.danasea.backend.modules.order.application.dtos.CancellationPreviewResult;
import com.danasea.backend.modules.order.application.dtos.CreateOrderCommand;
import com.danasea.backend.modules.order.application.dtos.GetCancellationPreviewQuery;
import com.danasea.backend.modules.order.application.dtos.GetCustomerOrdersQuery;
import com.danasea.backend.modules.order.application.dtos.GetOrderDetailQuery;
import com.danasea.backend.modules.order.application.dtos.MasterOrderDetailResult;
import com.danasea.backend.modules.order.application.dtos.OrderRefundResult;
import com.danasea.backend.modules.order.application.dtos.RequestRefundCommand;
import com.danasea.backend.modules.order.application.usecases.CreateOrderUseCase;
import com.danasea.backend.modules.order.application.usecases.GetCancellationPreviewUseCase;
import com.danasea.backend.modules.order.application.usecases.GetCustomerOrdersUseCase;
import com.danasea.backend.modules.order.application.usecases.GetOrderDetailUseCase;
import com.danasea.backend.modules.order.application.usecases.RequestRefundUseCase;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.OrderPagedResult;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.presentation.dtos.CancellationPreviewResponse;
import com.danasea.backend.modules.order.presentation.dtos.CreateOrderRequest;
import com.danasea.backend.modules.order.presentation.dtos.OrderPageResponse;
import com.danasea.backend.modules.order.presentation.dtos.OrderResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentResponse;
import com.danasea.backend.modules.order.presentation.dtos.RefundDetailResponse;
import com.danasea.backend.modules.order.presentation.dtos.RefundRequest;
import com.danasea.backend.modules.order.presentation.dtos.RefundResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController Unit Tests")
class OrderControllerTest {

    @Mock
    private CreateOrderUseCase createOrderUseCase;

    @Mock
    private GetOrderDetailUseCase getOrderDetailUseCase;

    @Mock
    private GetCustomerOrdersUseCase getCustomerOrdersUseCase;

    @Mock
    private RequestRefundUseCase requestRefundUseCase;

    @Mock
    private GetCancellationPreviewUseCase getCancellationPreviewUseCase;

    @Mock
    private OrderPaymentService orderPaymentService;

    @InjectMocks
    private OrderController orderController;

    private UUID customerId;
    private UUID masterOrderId;
    private UUID subOrderId;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        masterOrderId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();
        slotId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityUser(UUID userId, String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), "credentials", authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("Owner previews cancellation by MasterOrder ID -> 200 OK with aggregated items")
    void preview_ByMasterOrderId_AsOwner_Success200() {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");

        SubOrderCancellationPreviewResult itemResult = new SubOrderCancellationPreviewResult(
                subOrderId,
                UUID.randomUUID(),
                slotId,
                LocalDateTime.now().plusDays(5),
                new BigDecimal("1000000.00"),
                BigDecimal.valueOf(100.0),
                new BigDecimal("1000000.00"),
                "FULL_REFUND (>48h)"
        );

        CancellationPreviewResult result = new CancellationPreviewResult(
                masterOrderId,
                new BigDecimal("1000000.00"),
                BigDecimal.valueOf(100.0),
                new BigDecimal("1000000.00"),
                "FULL_REFUND (>48h)",
                List.of(itemResult)
        );

        when(getCancellationPreviewUseCase.execute(any(GetCancellationPreviewQuery.class)))
                .thenReturn(result);

        ResponseEntity<CancellationPreviewResponse> response = orderController.getCancellationPreview(masterOrderId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        CancellationPreviewResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(masterOrderId, body.orderId());
        assertEquals(new BigDecimal("1000000.00"), body.originalAmount());
        assertEquals(BigDecimal.valueOf(100.0), body.refundPercentage());
        assertEquals(new BigDecimal("1000000.00"), body.refundAmount());
        assertEquals(1, body.items().size());
        verify(getCancellationPreviewUseCase).execute(any(GetCancellationPreviewQuery.class));
    }

    @Test
    @DisplayName("Owner previews cancellation by SubOrder ID -> 200 OK polymorphic resolution")
    void preview_BySubOrderId_AsOwner_Success200() {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");

        SubOrderCancellationPreviewResult itemResult = new SubOrderCancellationPreviewResult(
                subOrderId,
                UUID.randomUUID(),
                slotId,
                LocalDateTime.now().plusDays(1),
                new BigDecimal("1000000.00"),
                BigDecimal.valueOf(70.0),
                new BigDecimal("700000.00"),
                "PARTIAL_REFUND_70 (24h-48h)"
        );

        CancellationPreviewResult result = new CancellationPreviewResult(
                subOrderId,
                new BigDecimal("1000000.00"),
                BigDecimal.valueOf(70.0),
                new BigDecimal("700000.00"),
                "PARTIAL_REFUND_70 (24h-48h)",
                List.of(itemResult)
        );

        when(getCancellationPreviewUseCase.execute(any(GetCancellationPreviewQuery.class)))
                .thenReturn(result);

        ResponseEntity<CancellationPreviewResponse> response = orderController.getCancellationPreview(subOrderId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        CancellationPreviewResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(subOrderId, body.orderId());
        assertEquals(1, body.items().size());
        assertEquals(new BigDecimal("1000000.00"), body.originalAmount());
    }

    @Test
    @DisplayName("Admin previews cancellation for any customer order -> 200 OK bypasses IDOR")
    void preview_AsAdmin_Success200() {
        mockSecurityUser(UUID.randomUUID(), "ROLE_ADMIN");

        CancellationPreviewResult result = new CancellationPreviewResult(
                masterOrderId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "STANDARD_CANCELLATION_POLICY",
                List.of()
        );

        when(getCancellationPreviewUseCase.execute(any(GetCancellationPreviewQuery.class)))
                .thenReturn(result);

        ResponseEntity<CancellationPreviewResponse> response = orderController.getCancellationPreview(masterOrderId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Unauthorized customer accesses another customer's order -> 403 Forbidden")
    void preview_IdorViolation_ThrowsAccessDeniedException() {
        mockSecurityUser(UUID.randomUUID(), "ROLE_CUSTOMER");

        when(getCancellationPreviewUseCase.execute(any(GetCancellationPreviewQuery.class)))
                .thenThrow(new AccessDeniedException("User does not have permission to view cancellation preview for this order"));

        assertThrows(AccessDeniedException.class, () -> orderController.getCancellationPreview(masterOrderId));
    }

    @Test
    @DisplayName("Order not found with given ID -> throws OrderNotFoundException")
    void preview_OrderNotFound_ThrowsOrderNotFoundException() {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");

        when(getCancellationPreviewUseCase.execute(any(GetCancellationPreviewQuery.class)))
                .thenThrow(new OrderNotFoundException(masterOrderId));

        assertThrows(OrderNotFoundException.class, () -> orderController.getCancellationPreview(masterOrderId));
    }

    @Test
    @DisplayName("SubOrder missing slot -> preview handles null departure time with 0.0% refund")
    void preview_SubOrderMissingSlot_ReturnsZeroPercent() {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");

        CancellationPreviewResult result = new CancellationPreviewResult(
                masterOrderId,
                new BigDecimal("500000.00"),
                BigDecimal.valueOf(0.0),
                BigDecimal.ZERO,
                "ZERO_REFUND_OR_EXPIRED",
                List.of()
        );

        when(getCancellationPreviewUseCase.execute(any(GetCancellationPreviewQuery.class)))
                .thenReturn(result);

        ResponseEntity<CancellationPreviewResponse> response = orderController.getCancellationPreview(masterOrderId);

        assertNotNull(response);
        CancellationPreviewResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(BigDecimal.valueOf(0.0), body.refundPercentage());
        assertEquals(0, BigDecimal.ZERO.compareTo(body.refundAmount()));
    }

    @Test
    @DisplayName("Customer creates order -> delegates to CreateOrderUseCase and returns 201 Created")
    void createOrder_CallsUseCase_Returns201Created() {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");
        UUID bookingId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(bookingId);
        String idempotencyKey = "order-create-idemp-12345";

        MasterOrderDetailResult detailResult = new MasterOrderDetailResult(
                masterOrderId,
                bookingId,
                customerId,
                MasterOrderStatus.PENDING_PAYMENT,
                PaymentOrderStatus.UNPAID,
                new BigDecimal("1000000"),
                BigDecimal.ZERO,
                null,
                null,
                idempotencyKey,
                null,
                List.of()
        );

        when(createOrderUseCase.execute(any(CreateOrderCommand.class)))
                .thenReturn(detailResult);

        ResponseEntity<OrderResponse> response =
                orderController.createOrder(request, idempotencyKey);

        assertNotNull(response);
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(masterOrderId, response.getBody().id());
        assertEquals(MasterOrderStatus.PENDING_PAYMENT, response.getBody().status());
    }

    @Test
    @DisplayName("Customer gets order list -> delegates to GetCustomerOrdersUseCase and returns 200 OK")
    void getMyOrders_CallsUseCase_Returns200Ok() {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");

        OffsetDateTime deadline = OffsetDateTime.parse("2026-11-07T08:10:00+07:00");
        MasterOrderDetailResult item = new MasterOrderDetailResult(
                masterOrderId,
                UUID.randomUUID(),
                customerId,
                MasterOrderStatus.PENDING_PAYMENT,
                PaymentOrderStatus.UNPAID,
                new BigDecimal("500000"),
                BigDecimal.ZERO,
                null,
                deadline,
                "key",
                null,
                List.of()
        );

        OrderPagedResult<MasterOrderDetailResult> paged = new OrderPagedResult<>(
                List.of(item), 0, 20, 1L, 1
        );

        when(getCustomerOrdersUseCase.execute(any(GetCustomerOrdersQuery.class)))
                .thenReturn(paged);

        ResponseEntity<OrderPageResponse<OrderResponse>> response =
                orderController.getMyOrders(0, 20);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().totalElements());
        assertEquals(masterOrderId, response.getBody().content().get(0).id());
        assertEquals(deadline, response.getBody().content().get(0).paymentDeadline());
    }

    @Test
    @DisplayName("User gets order detail -> delegates to GetOrderDetailUseCase and returns 200 OK")
    void getOrder_CallsUseCase_Returns200Ok() {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");

        MasterOrderDetailResult detailResult = new MasterOrderDetailResult(
                masterOrderId,
                UUID.randomUUID(),
                customerId,
                MasterOrderStatus.PAID,
                PaymentOrderStatus.PAID,
                new BigDecimal("1000000"),
                BigDecimal.ZERO,
                null,
                null,
                "key",
                null,
                List.of()
        );

        when(getOrderDetailUseCase.execute(any(GetOrderDetailQuery.class)))
                .thenReturn(detailResult);

        ResponseEntity<OrderResponse> response =
                orderController.getOrder(masterOrderId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(masterOrderId, response.getBody().id());
        assertEquals(MasterOrderStatus.PAID, response.getBody().status());
    }

    @Test
    @DisplayName("Customer requests refund -> delegates to RequestRefundUseCase and returns 202 Accepted")
    void requestRefund_CallsUseCase_Returns202Accepted() {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");
        String idempotencyKey = "refund-idemp-12345";
        RefundRequest request = new RefundRequest(RefundReason.CUSTOMER_REQUEST);

        OrderRefundResult refundResult = new OrderRefundResult(
                UUID.randomUUID(),
                subOrderId,
                new BigDecimal("500000.00"),
                BigDecimal.valueOf(100.0),
                RefundReason.CUSTOMER_REQUEST,
                RefundStatus.PENDING,
                "FULL_REFUND (>48h)"
        );

        when(requestRefundUseCase.execute(any(RequestRefundCommand.class)))
                .thenReturn(List.of(refundResult));

        ResponseEntity<List<RefundResponse>> response =
                orderController.requestRefund(masterOrderId, request, idempotencyKey);

        assertNotNull(response);
        assertEquals(202, response.getStatusCode().value());
        List<RefundResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.size());
        assertEquals(subOrderId, body.get(0).subOrderId());
        assertEquals(new BigDecimal("500000.00"), body.get(0).amount());
        verify(requestRefundUseCase).execute(any(RequestRefundCommand.class));
    }

    @Test
    @DisplayName("Should get order refunds successfully")
    void shouldGetOrderRefundsSuccessfully() {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");
        RefundDetailResponse detail = new RefundDetailResponse(
                UUID.randomUUID(),
                subOrderId,
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(100),
                RefundReason.CUSTOMER_REQUEST,
                RefundStatus.PROCESSED,
                null,
                "REF-1",
                "TXN-1",
                0,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(orderPaymentService.getOrderRefunds(customerId, masterOrderId, false))
                .thenReturn(List.of(detail));

        ResponseEntity<List<RefundDetailResponse>> response = orderController.getOrderRefunds(masterOrderId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(subOrderId, response.getBody().get(0).subOrderId());
    }

    @Test
    @DisplayName("Should get order payments successfully")
    void shouldGetOrderPaymentsSuccessfully() {
        mockSecurityUser(customerId, "ROLE_CUSTOMER");
        PaymentResponse paymentResponse = new PaymentResponse(
                UUID.randomUUID(),
                masterOrderId,
                null,
                "TXN-123",
                BigDecimal.valueOf(200000),
                null,
                "idem-1",
                null,
                null,
                null,
                OffsetDateTime.now().plusMinutes(15),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(orderPaymentService.getOrderPayments(customerId, masterOrderId, false))
                .thenReturn(List.of(paymentResponse));

        ResponseEntity<List<PaymentResponse>> response = orderController.getOrderPayments(masterOrderId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(masterOrderId, response.getBody().get(0).masterOrderId());
    }
}

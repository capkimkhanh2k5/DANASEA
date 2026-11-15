package com.danasea.backend.modules.order.presentation.controllers;

import java.math.BigDecimal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.dtos.GetVendorOrdersQuery;
import com.danasea.backend.modules.order.application.dtos.SubOrderDetailResult;
import com.danasea.backend.modules.order.application.usecases.GetVendorOrdersUseCase;
import com.danasea.backend.modules.order.domain.models.OrderPagedResult;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.presentation.dtos.OrderPageResponse;
import com.danasea.backend.modules.order.presentation.dtos.SubOrderResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VendorOrderController Unit Tests")
class VendorOrderControllerTest {

    @Mock
    private OrderPaymentService orderPaymentService;

    @Mock
    private GetVendorOrdersUseCase getVendorOrdersUseCase;

    @InjectMocks
    private VendorOrderController vendorOrderController;

    private UUID vendorId;

    @BeforeEach
    void setUp() {
        vendorId = UUID.randomUUID();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_VENDOR"));
        var auth = new UsernamePasswordAuthenticationToken(vendorId.toString(), "credentials", authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Vendor views their orders -> delegates to GetVendorOrdersUseCase and returns 200 OK")
    void getVendorOrders_CallsUseCase_Returns200Ok() {
        UUID subOrderId = UUID.randomUUID();
        SubOrderDetailResult item = new SubOrderDetailResult(
                subOrderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                vendorId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                new BigDecimal("500000"),
                new BigDecimal("1000000"),
                new BigDecimal("0.10"),
                new BigDecimal("100000"),
                new BigDecimal("900000"),
                SubOrderStatus.CONFIRMED,
                true,
                null,
                null
        );

        OrderPagedResult<SubOrderDetailResult> pagedResult = new OrderPagedResult<>(
                List.of(item), 0, 20, 1L, 1
        );

        when(getVendorOrdersUseCase.execute(any(GetVendorOrdersQuery.class))).thenReturn(pagedResult);

        ResponseEntity<OrderPageResponse<SubOrderResponse>> response =
                vendorOrderController.getVendorOrders(0, 20);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        OrderPageResponse<SubOrderResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.totalElements());
        assertEquals(subOrderId, body.content().get(0).id());
        assertEquals(SubOrderStatus.CONFIRMED, body.content().get(0).status());

        verify(getVendorOrdersUseCase).execute(any(GetVendorOrdersQuery.class));
    }
}

package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.usecases.CreatePaymentIntentUseCase;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.controllers.AdminPaymentController;
import com.danasea.backend.modules.order.presentation.dtos.PaymentResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.fasterxml.jackson.databind.ObjectMapper;

class PaymentReadAuthorizationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("14967a40-3cdf-478c-b19a-60419d32c3a1");
    private static final UUID ORDER_ID = UUID.fromString("33b24696-4bb3-45d8-8a14-97f6ac6cf743");
    private static final UUID PAYMENT_ID = UUID.fromString("f807c80c-6ff4-40d7-8ef9-bd0e2cef211d");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("655c347a-3881-44f2-9785-e37dc7487268");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-11-01T00:00:00Z");

    private JpaMasterOrderRepository orders;
    private JpaPaymentRepository payments;
    private PaymentGatewayPort gateway;
    private OrderPaymentService service;
    private MasterOrderJpaEntity order;
    private PaymentJpaEntity payment;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        orders = mock(JpaMasterOrderRepository.class);
        payments = mock(JpaPaymentRepository.class);
        gateway = mock(PaymentGatewayPort.class);
        service = new OrderPaymentService(mock(JpaBookingRepository.class), orders,
                mock(JpaSubOrderRepository.class), payments, mock(JpaRefundRepository.class),
                mock(JpaServiceSlotRepository.class), mock(VendorInternalApi.class), new RefundPolicyEngine(),
                mock(PaymentWebhookSigner.class), mock(ConfirmBookingUseCase.class), new ObjectMapper(),
                mock(CreatePaymentIntentUseCase.class), mock(RefundProcessingService.class), gateway);
        order = new MasterOrderJpaEntity();
        order.setId(ORDER_ID);
        order.setCustomerId(CUSTOMER_ID);
        payment = new PaymentJpaEntity();
        payment.setId(PAYMENT_ID);
        payment.setMasterOrderId(ORDER_ID);
        payment.setProvider(PaymentProvider.PAYPAL);
        payment.setProviderOrderId("ORDER-PROVIDER");
        payment.setProviderTransactionId("CAPTURE-ORIGINAL");
        payment.setAmount(new BigDecimal("500000"));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey("payment-read-key");
        payment.setPaymentUrl("https://www.sandbox.paypal.com/checkoutnow?token=ORDER-PROVIDER");
        payment.setCreatedAt(CREATED_AT);
        payment.setLastError("GATEWAY_QUERY_AWAITING_VERIFICATION");
        payment.setReconciliationAttempts(2);
        payment.setReconciliationNextAttemptAt(CREATED_AT.plusMinutes(2));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theOwnerCanReadPaymentHistoryWithProviderAndVerificationDetails() {
        when(payments.findByMasterOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(List.of(payment));

        List<PaymentResponse> history = service.getPaymentsByOrderId(CUSTOMER_ID, ORDER_ID);

        assertThat(history).hasSize(1);
        assertPaymentDetails(history.get(0));
        verify(payments).findByMasterOrderIdOrderByCreatedAtDesc(ORDER_ID);
        verifyNoInteractions(gateway);
    }

    @Test
    void anotherCustomerCannotQueryThePaymentHistoryRepository() {
        assertThatThrownBy(() -> service.getPaymentsByOrderId(OTHER_CUSTOMER_ID, ORDER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(payments, gateway);
    }

    @Test
    void anUnownedOrderCannotExposePaymentHistoryEvenToANullCaller() {
        order.setCustomerId(null);

        assertThatThrownBy(() -> service.getPaymentsByOrderId(null, ORDER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(payments, gateway);
    }

    @Test
    void aMissingOrderRejectsHistoryBeforeAnyPaymentLookup() {
        when(orders.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPaymentsByOrderId(CUSTOMER_ID, ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class).hasMessageContaining(ORDER_ID.toString());

        verifyNoInteractions(payments, gateway);
    }

    @Test
    void anOwnedOrderWithoutPaymentAttemptsReturnsEmptyHistory() {
        when(payments.findByMasterOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(List.of());

        assertThat(service.getPaymentsByOrderId(CUSTOMER_ID, ORDER_ID)).isEmpty();

        verify(payments).findByMasterOrderIdOrderByCreatedAtDesc(ORDER_ID);
        verifyNoInteractions(gateway);
    }

    @Test
    void theOwnerCanReadTheRequestedPaymentDetail() {
        assertPaymentDetails(service.getPaymentDetail(CUSTOMER_ID, PAYMENT_ID));
        verify(payments).findById(PAYMENT_ID);
        verify(orders).findById(ORDER_ID);
        verifyNoInteractions(gateway);
    }

    @Test
    void anotherCustomerCannotReadPaymentDetailsThroughThePaymentId() {
        assertThatThrownBy(() -> service.getPaymentDetail(OTHER_CUSTOMER_ID, PAYMENT_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(gateway);
    }

    @Test
    void aMissingPaymentRejectsDetailBeforeAnyOrderLookup() {
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPaymentDetail(CUSTOMER_ID, PAYMENT_ID))
                .isInstanceOf(OrderNotFoundException.class).hasMessageContaining(PAYMENT_ID.toString());

        verifyNoInteractions(orders, gateway);
    }

    @Test
    void aPaymentWithoutItsParentOrderCannotExposeDetail() {
        when(orders.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPaymentDetail(CUSTOMER_ID, PAYMENT_ID))
                .isInstanceOf(OrderNotFoundException.class).hasMessageContaining(ORDER_ID.toString());

        verifyNoInteractions(gateway);
    }

    @Test
    void anAdminCanReadPaymentDetailThroughTheRealMethodSecurityGate() {
        authenticate("ROLE_ADMIN");
        try (var context = securedAdminController()) {
            var response = context.getBean(AdminPaymentController.class).getPaymentDetail(PAYMENT_ID);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertPaymentDetails(response.getBody());
        }
        verifyNoInteractions(orders, gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_CUSTOMER", "ROLE_VENDOR"})
    void aNonAdminCannotReachThePaymentRepositoryThroughTheAdminEndpoint(String authority) {
        authenticate(authority);
        try (var context = securedAdminController()) {
            assertThatThrownBy(() -> context.getBean(AdminPaymentController.class).getPaymentDetail(PAYMENT_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(payments, orders, gateway);
    }

    @Test
    void anUnauthenticatedCallerCannotReachTheAdminPaymentRepository() {
        try (var context = securedAdminController()) {
            assertThatThrownBy(() -> context.getBean(AdminPaymentController.class).getPaymentDetail(PAYMENT_ID))
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        }
        verifyNoInteractions(payments, orders, gateway);
    }

    @Test
    void anAdminGetsNotFoundForAMissingPaymentAfterPassingTheRoleGate() {
        authenticate("ROLE_ADMIN");
        when(payments.findById(PAYMENT_ID)).thenReturn(Optional.empty());
        try (var context = securedAdminController()) {
            assertThatThrownBy(() -> context.getBean(AdminPaymentController.class).getPaymentDetail(PAYMENT_ID))
                    .isInstanceOf(OrderNotFoundException.class).hasMessageContaining(PAYMENT_ID.toString());
        }
        verify(payments).findById(PAYMENT_ID);
        verifyNoInteractions(orders, gateway);
    }

    private AnnotationConfigApplicationContext securedAdminController() {
        var context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfiguration.class);
        context.registerBean(AdminPaymentController.class, () -> new AdminPaymentController(service));
        context.refresh();
        return context;
    }

    private static void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "payment-reader", null, List.of(new SimpleGrantedAuthority(authority))));
    }

    private static void assertPaymentDetails(PaymentResponse response) {
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(PAYMENT_ID);
        assertThat(response.masterOrderId()).isEqualTo(ORDER_ID);
        assertThat(response.provider()).isEqualTo(PaymentProvider.PAYPAL);
        assertThat(response.providerOrderId()).isEqualTo("ORDER-PROVIDER");
        assertThat(response.providerTransactionId()).isEqualTo("CAPTURE-ORIGINAL");
        assertThat(response.amount()).isEqualByComparingTo("500000");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.paymentUrl()).isEqualTo("https://www.sandbox.paypal.com/checkoutnow?token=ORDER-PROVIDER");
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.lastError()).isEqualTo("GATEWAY_QUERY_AWAITING_VERIFICATION");
        assertThat(response.reconciliationAttempts()).isEqualTo(2);
        assertThat(response.reconciliationNextAttemptAt()).isEqualTo(CREATED_AT.plusMinutes(2));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}

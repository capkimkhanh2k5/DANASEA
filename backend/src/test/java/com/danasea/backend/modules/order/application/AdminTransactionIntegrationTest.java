package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.GatewayRefundStatus;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.RefundResult;
import com.danasea.backend.modules.order.infrastructure.jobs.PaymentReconciliationJob;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;

import com.danasea.backend.configs.AbstractExternalInfrastructureIntegrationTest;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminTransactionIntegrationTest extends AbstractExternalInfrastructureIntegrationTest {
    @Container
    static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withEnv("POSTGRES_DB", "admin_transactions").withEnv("POSTGRES_USER", "test").withEnv("POSTGRES_PASSWORD", "test")
            .withExposedPorts(5432).waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AdminTransactionIntegrationTest::databaseUrl);
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    private static String databaseUrl() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/admin_transactions";
    }

    @Autowired OrderPaymentService service;
    @Autowired PaymentReconciliationJob job;
    @Autowired RefundProcessingService refundWorker;
    @Autowired JpaPaymentRepository payments;
    @Autowired JpaMasterOrderRepository orders;
    @Autowired JpaSubOrderRepository subs;
    @Autowired JpaRefundRepository refunds;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean(name = "payPalPaymentAdapter") PaymentGatewayPort gateway;
    @MockitoBean ConfirmBookingUseCase confirm;

    @BeforeEach
    void clearFinancialFixtures() {
        jdbc.execute("TRUNCATE refunds, payments, sub_orders, master_orders, audit_logs CASCADE");
    }

    private PaymentJpaEntity seed(PaymentProvider provider) {
        var order = new MasterOrderJpaEntity();
        order.setCustomerId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("500000"));
        order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        order = orders.saveAndFlush(order);
        var payment = new PaymentJpaEntity();
        payment.setMasterOrderId(order.getId());
        payment.setAmount(order.getTotalAmount());
        payment.setProvider(provider);
        payment.setProviderAmount(provider == PaymentProvider.PAYPAL ? new BigDecimal("19.31") : order.getTotalAmount());
        payment.setProviderCurrency(provider == PaymentProvider.PAYPAL ? "USD" : "VND");
        payment.setProviderOrderId(UUID.randomUUID().toString());
        payment.setProviderTransactionDate(provider == PaymentProvider.VNPAY ? "20261009100000" : null);
        payment.setStatus(PaymentStatus.PENDING);
        payment = payments.saveAndFlush(payment);
        if (provider == PaymentProvider.VNPAY) {
            payment.setProviderOrderId(payment.getId().toString());
            payment = payments.saveAndFlush(payment);
        }
        return payment;
    }

    private SubOrderJpaEntity sub(PaymentJpaEntity payment, UUID vendorId) {
        var sub = new SubOrderJpaEntity();
        sub.setMasterOrderId(payment.getMasterOrderId());
        sub.setVendorId(vendorId);
        sub.setSubtotalAmount(payment.getAmount());
        sub.setStatus(SubOrderStatus.PENDING);
        return subs.saveAndFlush(sub);
    }

    private RefundJpaEntity refund(SubOrderJpaEntity sub) {
        var refund = new RefundJpaEntity();
        refund.setSubOrderId(sub.getId());
        refund.setAmount(new BigDecimal("100000"));
        refund.setStatus(RefundStatus.PENDING);
        refund.setProvider(PaymentProvider.VNPAY);
        refund.setReason(RefundReason.CUSTOMER_REQUEST);
        refund.setVerificationAttempts(2);
        refund.setNextAttemptAt(OffsetDateTime.now().plusMinutes(1));
        return refunds.saveAndFlush(refund);
    }

    private PaymentCaptureResult success(PaymentJpaEntity payment) {
        return new PaymentCaptureResult(true, UUID.randomUUID().toString(), payment.getProviderAmount(),
                payment.getProviderCurrency(), "COMPLETED", "Success");
    }

    @Test
    void allThreeAdminListsWorkWithoutDatesAndWithOptionalDateBounds() throws Exception {
        var payment = seed(PaymentProvider.VNPAY);
        refund(sub(payment, UUID.randomUUID()));
        for (String resource : List.of("orders", "payments", "refunds")) {
            mvc.perform(get("/api/admin/" + resource).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
            mvc.perform(get("/api/admin/" + resource).param("from", OffsetDateTime.now().minusDays(1).toString())
                    .with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
            mvc.perform(get("/api/admin/" + resource).param("to", OffsetDateTime.now().plusDays(1).toString())
                    .with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        }
    }

    @Test
    void combinedFiltersPreserveVendorCustomerIsolationAndDoNotDuplicateOrders() {
        var selected = seed(PaymentProvider.VNPAY);
        UUID vendor = UUID.randomUUID();
        var first = sub(selected, vendor);
        sub(selected, vendor);
        var expectedRefund = refund(first);
        refund(sub(seed(PaymentProvider.VNPAY), UUID.randomUUID()));
        var customer = orders.findById(selected.getMasterOrderId()).orElseThrow().getCustomerId();
        var from = OffsetDateTime.now().minusDays(1);
        var to = OffsetDateTime.now().plusDays(1);
        var page = PageRequest.of(0, 1);
        assertThat(service.getAdminOrders(MasterOrderStatus.PENDING_PAYMENT, PaymentOrderStatus.UNPAID, customer, vendor, from, to, page)
                .getTotalElements()).isEqualTo(1);
        assertThat(service.getAdminPayments(PaymentStatus.PENDING, PaymentProvider.VNPAY, selected.getMasterOrderId(), customer, vendor, from, to, page)
                .getContent()).extracting(response -> response.id()).containsExactly(selected.getId());
        assertThat(service.getAdminRefunds(RefundStatus.PENDING, RefundReason.CUSTOMER_REQUEST, null, PaymentProvider.VNPAY,
                vendor, customer, selected.getMasterOrderId(), from, to, page).getContent())
                .extracting(response -> response.id()).containsExactly(expectedRefund.getId());
        assertThat(service.getAdminOrders(null, null, customer, UUID.randomUUID(), from, to, page)).isEmpty();
    }

    @Test
    void inclusiveDateBoundariesUseInstantsAcrossTimezones() {
        var payment = seed(PaymentProvider.VNPAY);
        var boundary = OffsetDateTime.parse("2026-10-09T03:00:00Z");
        jdbc.update("UPDATE master_orders SET created_at = ? WHERE id = ?", boundary, payment.getMasterOrderId());
        assertThat(service.getAdminOrders(null, null, null, null, boundary.withOffsetSameInstant(java.time.ZoneOffset.ofHours(7)),
                boundary, PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    void reversedDateBoundsAreRejectedByEveryAdminList() throws Exception {
        for (String resource : List.of("orders", "payments", "refunds")) {
            mvc.perform(get("/api/admin/" + resource).param("from", OffsetDateTime.now().plusDays(1).toString())
                    .param("to", OffsetDateTime.now().toString()).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void nonAdminCannotReadWholePlatformTransactions() throws Exception {
        for (String resource : List.of("orders", "payments", "refunds")) {
            mvc.perform(get("/api/admin/" + resource).with(user("customer").roles("CUSTOMER")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void orderHistoryIsRestrictedToOwnerOrAdmin() throws Exception {
        var payment = seed(PaymentProvider.VNPAY);
        refund(sub(payment, UUID.randomUUID()));
        var order = orders.findById(payment.getMasterOrderId()).orElseThrow();
        for (String resource : List.of("payments", "refunds")) {
            String url = "/api/orders/" + order.getId() + "/" + resource;
            mvc.perform(get(url).with(user(order.getCustomerId().toString()).roles("CUSTOMER"))).andExpect(status().isOk());
            mvc.perform(get(url).with(user(UUID.randomUUID().toString()).roles("CUSTOMER"))).andExpect(status().isForbidden());
            mvc.perform(get(url).with(user(UUID.randomUUID().toString()).roles("ADMIN"))).andExpect(status().isOk());
        }
    }

    @Test
    void confirmedPaymentAppearsInPaidPaymentStatusFilterAndAudit() {
        var payment = seed(PaymentProvider.VNPAY);
        sub(payment, UUID.randomUUID());
        when(gateway.queryPayment(any(), anyString(), anyString())).thenReturn(success(payment));
        assertThat(service.reconcilePayment(payment.getId())).isTrue();
        var order = orders.findById(payment.getMasterOrderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.PAID);
        assertThat(service.getAdminOrders(null, PaymentOrderStatus.PAID, order.getCustomerId(), null, null, null, PageRequest.of(0, 20))
                .getContent()).extracting(response -> response.id()).containsExactly(order.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='RECONCILE_PAYMENT_SUCCESS' AND entity_id=?",
                Integer.class, payment.getId())).isEqualTo(1);
    }

    @Test
    void bookingFailureRollsBackPaymentOrderAndSubOrderAndRecordsReviewMarker() {
        var payment = seed(PaymentProvider.VNPAY);
        var sub = sub(payment, UUID.randomUUID());
        when(gateway.queryPayment(any(), anyString(), anyString())).thenReturn(success(payment));
        doThrow(new IllegalStateException("Hold expired / capacity unavailable")).when(confirm).execute(any());
        assertThat(service.reconcilePayment(payment.getId())).isFalse();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(orders.findById(payment.getMasterOrderId()).orElseThrow().getStatus()).isEqualTo(MasterOrderStatus.PENDING_PAYMENT);
        assertThat(subs.findById(sub.getId()).orElseThrow().getStatus()).isEqualTo(SubOrderStatus.PENDING);
        assertThat(payments.findById(payment.getId()).orElseThrow().getLastError()).contains("APPLY_REQUIRES_REVIEW");
    }

    @Test
    void staleFailureQueryCannotOverwriteARealSuccessfulPayPalWebhook() {
        var payment = seed(PaymentProvider.PAYPAL);
        when(gateway.verifyWebhookSignature(any(Map.class), anyString())).thenReturn(true);
        when(gateway.queryPayment(any(), anyString(), any())).thenAnswer(invocation -> {
            service.processPayPalWebhook(webhook(payment), "transmission", "timestamp", "signature", "https://api.paypal.com/cert", "SHA256withRSA");
            return new PaymentCaptureResult(false, null, null, null, "VOIDED", "stale query");
        });
        assertThat(service.reconcilePayment(payment.getId())).isFalse();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(orders.findById(payment.getMasterOrderId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentOrderStatus.PAID);
        verify(confirm, times(1)).execute(any());
    }

    private String webhook(PaymentJpaEntity payment) {
        return "{\"id\":\"EVENT-" + payment.getId() + "\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":"
                + "{\"id\":\"CAPTURE-" + payment.getId() + "\",\"status\":\"COMPLETED\",\"custom_id\":\"" + payment.getId()
                + "\",\"amount\":{\"value\":\"19.31\",\"currency_code\":\"USD\"},\"supplementary_data\":{\"related_ids\":{\"order_id\":\""
                + payment.getProviderOrderId() + "\"}}}}";
    }

    @Test
    void concurrentReconciliationClaimsPerformOneLookupAndOneConfirmation() throws Exception {
        var payment = seed(PaymentProvider.VNPAY);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(gateway.queryPayment(any(), anyString(), anyString())).thenAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return success(payment);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.reconcilePayment(payment.getId()));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(executor.submit(() -> service.reconcilePayment(payment.getId())).get(5, TimeUnit.SECONDS)).isFalse();
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        }
        verify(gateway, times(1)).queryPayment(any(), anyString(), anyString());
        verify(confirm, times(1)).execute(any());
    }

    @Test
    void timeoutAfterExpiryRemainsPendingAndDoesNotSendFinancialCommands() {
        var payment = seed(PaymentProvider.VNPAY);
        payment.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        payments.saveAndFlush(payment);
        when(gateway.queryPayment(any(), anyString(), anyString())).thenThrow(new PaymentGatewayException("Read timeout"));
        assertThat(service.reconcilePayment(payment.getId())).isFalse();
        var persisted = payments.findById(payment.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(persisted.getLastError()).contains("AWAITING_VERIFICATION");
        assertThat(persisted.getReconciliationNextAttemptAt()).isAfter(OffsetDateTime.now());
        verify(gateway, never()).captureOrder(any(), any(), any());
        verify(gateway, never()).requestRefund(any());
    }

    @Test
    void wrongGatewayMoneyRemainsPendingWithoutConfirmingBooking() {
        var payment = seed(PaymentProvider.VNPAY);
        when(gateway.queryPayment(any(), anyString(), anyString()))
                .thenReturn(new PaymentCaptureResult(true, "WRONG-MONEY", BigDecimal.ONE, "USD", "COMPLETED", "Success"));
        assertThat(service.reconcilePayment(payment.getId())).isFalse();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(confirm);
    }

    @Test
    void changingIntentWhileQueryRunsRejectsStaleResponse() {
        var payment = seed(PaymentProvider.VNPAY);
        when(gateway.queryPayment(any(), anyString(), anyString())).thenAnswer(invocation -> {
            jdbc.update("UPDATE payments SET provider_order_id='replacement-intent' WHERE id=?", payment.getId());
            return success(payment);
        });
        assertThat(service.reconcilePayment(payment.getId())).isFalse();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(confirm);
    }

    @Test
    void paypalCaptureRecoveryIsQueryOnlyAndProducesAudit() {
        var payment = seed(PaymentProvider.PAYPAL);
        payment.setCaptureRequestedAt(OffsetDateTime.now());
        payment.setCaptureRequestId(UUID.randomUUID().toString());
        payments.saveAndFlush(payment);
        when(gateway.queryCapture(PaymentProvider.PAYPAL, payment.getProviderOrderId())).thenReturn(success(payment));
        service.reconcilePayPalCapture(payment.getId());
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='RECONCILE_PAYMENT_SUCCESS' AND entity_id=?",
                Integer.class, payment.getId())).isEqualTo(1);
        verify(gateway, never()).captureOrder(any(), any(), any());
    }

    @Test
    void backoffAllowsTheNextBatchToReachPaymentsBeyondTheFirstFifty() {
        for (int index = 0; index < 51; index++) {
            seed(PaymentProvider.VNPAY);
        }
        jdbc.update("UPDATE payments SET created_at=now()-interval '3 minutes'");
        when(gateway.queryPayment(any(), anyString(), anyString()))
                .thenReturn(new PaymentCaptureResult(false, null, null, null, "UNKNOWN", "Unavailable"));
        job.reconcilePendingCaptures();
        verify(gateway, times(50)).queryPayment(any(), anyString(), anyString());
        assertThat(payments.findReconciliationCandidates(PaymentStatus.PENDING, List.of(PaymentProvider.PAYPAL, PaymentProvider.VNPAY),
                OffsetDateTime.now().minusMinutes(2), OffsetDateTime.now(), PageRequest.of(0, 50))).hasSize(1);
    }

    @Test
    void refundDetailExposesVerificationSchedule() {
        var refund = refund(sub(seed(PaymentProvider.VNPAY), UUID.randomUUID()));
        var detail = service.getAdminRefundDetail(refund.getId());
        assertThat(detail.verificationAttempts()).isEqualTo(2);
        assertThat(detail.nextAttemptAt()).isNotNull();
    }

    @Test
    void migrationBackfillsHistoricalSuccessfulAndFullyRefundedOrders() throws Exception {
        String schema = "historical_money";
        Flyway.configure().dataSource(databaseUrl(), "test", "test").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("20").load().migrate();
        UUID paid = UUID.randomUUID();
        UUID refunded = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(databaseUrl(), "test", "test");
                var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            for (UUID id : List.of(paid, refunded)) {
                statement.execute("INSERT INTO master_orders(id,status,total_amount,created_at,updated_at) VALUES ('" + id + "','PAID',500000,now(),now())");
                statement.execute("INSERT INTO payments(id,master_order_id,provider,status,amount,created_at,updated_at) VALUES ('"
                        + UUID.randomUUID() + "','" + id + "','VNPAY','" + (id.equals(paid) ? "SUCCESS" : "REFUNDED") + "',500000,now(),now())");
            }
        }
        Flyway.configure().dataSource(databaseUrl(), "test", "test").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(databaseUrl(), "test", "test");
                var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT payment_status FROM " + schema + ".master_orders WHERE id='" + paid + "'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("PAID");
            }
            try (var result = statement.executeQuery("SELECT payment_status FROM " + schema + ".master_orders WHERE id='" + refunded + "'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("REFUNDED");
            }
        }
    }

    @Test
    void financialAuditFailureRollsBackSuccessAndLeavesPaymentForReview() {
        var payment = seed(PaymentProvider.VNPAY);
        when(gateway.queryPayment(any(), anyString(), anyString())).thenReturn(success(payment));
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_reject_success_audit CHECK (action <> 'RECONCILE_PAYMENT_SUCCESS')");
        try {
            assertThat(service.reconcilePayment(payment.getId())).isFalse();
            assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(orders.findById(payment.getMasterOrderId()).orElseThrow().getStatus()).isEqualTo(MasterOrderStatus.PENDING_PAYMENT);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='RECONCILE_PAYMENT_SUCCESS'",
                    Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT test_reject_success_audit");
        }
    }

    @Test
    void lookupBeforeBuyerApprovalDoesNotBlockTheFirstCapture() {
        var payment = seed(PaymentProvider.PAYPAL);
        when(gateway.queryPayment(any(), anyString(), any())).thenReturn(
                new PaymentCaptureResult(false, null, null, null, "CREATED", "Buyer has not approved"));
        assertThat(service.reconcilePayment(payment.getId())).isFalse();
        when(gateway.captureOrder(org.mockito.ArgumentMatchers.eq(PaymentProvider.PAYPAL), org.mockito.ArgumentMatchers.eq(payment.getProviderOrderId()), anyString())).thenReturn(success(payment));
        var customerId = orders.findById(payment.getMasterOrderId()).orElseThrow().getCustomerId();
        assertThat(service.capturePayPalOrder(customerId, payment.getId(), payment.getProviderOrderId(), "first-capture-after-query").status())
                .isEqualTo(PaymentStatus.SUCCESS);
        verify(gateway, times(1)).captureOrder(any(), any(), any());
        verify(gateway, never()).queryCapture(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "04", "07", "09", "99"})
    void ambiguousVNPayIpnDoesNotClosePaymentBeforeReconciliation(String transactionStatus) {
        var payment = seed(PaymentProvider.VNPAY);
        when(gateway.verifyWebhookSignature(any(Map.class), anyString())).thenReturn(true);
        var params = Map.of("vnp_TxnRef", payment.getId().toString(), "vnp_Amount", "50000000",
                "vnp_ResponseCode", "07", "vnp_TransactionStatus", transactionStatus,
                "vnp_TransactionNo", "TXN-" + payment.getId(), "vnp_SecureHash", "signature");
        assertThat(service.processVNPayIpn(params).get("RspCode")).isEqualTo("99");
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(confirm);
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "02"})
    void definitiveVNPayIpnUpdatesPaymentAndMoneyStatus(String transactionStatus) {
        var payment = seed(PaymentProvider.VNPAY);
        when(gateway.verifyWebhookSignature(any(Map.class), anyString())).thenReturn(true);
        var params = Map.of("vnp_TxnRef", payment.getId().toString(), "vnp_Amount", "50000000",
                "vnp_ResponseCode", transactionStatus.equals("00") ? "00" : "24", "vnp_TransactionStatus", transactionStatus,
                "vnp_TransactionNo", "TXN-" + payment.getId(), "vnp_SecureHash", "signature");
        assertThat(service.processVNPayIpn(params).get("RspCode")).isEqualTo("00");
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(transactionStatus.equals("00") ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        assertThat(orders.findById(payment.getMasterOrderId()).orElseThrow().getPaymentStatus())
                .isEqualTo(transactionStatus.equals("00") ? PaymentOrderStatus.PAID : PaymentOrderStatus.UNPAID);
    }

    @ParameterizedTest
    @ValueSource(ints = {100000, 500000})
    void processedRefundSynchronizesMoneyStatusAndRepeatedProcessingIsIdempotent(int amount) {
        var payment = seed(PaymentProvider.VNPAY);
        var sub = sub(payment, UUID.randomUUID());
        when(gateway.queryPayment(any(), anyString(), anyString())).thenReturn(success(payment));
        assertThat(service.reconcilePayment(payment.getId())).isTrue();
        var refund = refund(sub);
        refund.setAmount(BigDecimal.valueOf(amount));
        refund.setRefundPercentage(BigDecimal.valueOf(amount == 500000 ? 100 : 20));
        refund.setGatewayRequestedAt(OffsetDateTime.now());
        refunds.saveAndFlush(refund);
        when(gateway.queryRefund(any(), any())).thenReturn(new RefundResult(true, "REFUND-" + refund.getId(),
                BigDecimal.valueOf(amount), "Completed", GatewayRefundStatus.COMPLETED, "VND"));
        assertThat(refundWorker.processRefund(refund.getId())).isTrue();
        var expectedStatus = amount == 500000 ? PaymentOrderStatus.REFUNDED : PaymentOrderStatus.PAID;
        var order = orders.findById(payment.getMasterOrderId()).orElseThrow();
        assertThat(order.getPaymentStatus()).isEqualTo(expectedStatus);
        assertThat(service.getAdminOrders(null, expectedStatus, order.getCustomerId(), null, null, null, PageRequest.of(0, 20))
                .getContent()).extracting(response -> response.id()).containsExactly(order.getId());
        assertThat(refundWorker.processRefund(refund.getId())).isTrue();
        verify(gateway, never()).requestRefund(any());
        verify(gateway, times(1)).queryRefund(any(), any());
    }

    @Test
    void legacyCaptureTimeoutWithoutOperationIdCannotBeTurnedIntoANewCapture() {
        var payment = seed(PaymentProvider.PAYPAL);
        payment.setLastError("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        payments.saveAndFlush(payment);
        var unresolved = new PaymentCaptureResult(false, null, null, null, "UNKNOWN", "Unavailable");
        when(gateway.queryPayment(any(), anyString(), any())).thenReturn(unresolved);
        assertThat(service.reconcilePayment(payment.getId())).isFalse();
        assertThat(payments.findById(payment.getId()).orElseThrow().getLastError())
                .startsWith("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        when(gateway.queryCapture(PaymentProvider.PAYPAL, payment.getProviderOrderId())).thenReturn(unresolved);
        var customerId = orders.findById(payment.getMasterOrderId()).orElseThrow().getCustomerId();
        assertThat(service.capturePayPalOrder(customerId, payment.getId(), payment.getProviderOrderId(), "retry-legacy-capture").status())
                .isEqualTo(PaymentStatus.PENDING);
        verify(gateway, never()).captureOrder(any(), any(), any());
    }
}

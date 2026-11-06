package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import com.danasea.backend.configs.TrackingAsyncTestExecutor;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.models.*;
import com.danasea.backend.modules.order.domain.ports.*;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.*;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.*;
import com.danasea.backend.modules.order.presentation.dtos.RefundWebhookResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RefundProcessingConcurrencyIntegrationTest.AsyncTestConfiguration.class)
@DisplayName("Refund Processing Concurrency and Recovery Integration Tests on Real PostgreSQL")
class RefundProcessingConcurrencyIntegrationTest extends BaseSecurityIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class AsyncTestConfiguration {
        @Bean(name = "taskExecutor", destroyMethod = "close")
        TrackingAsyncTestExecutor taskExecutor() {
            return new TrackingAsyncTestExecutor();
        }
    }

    @Autowired private RefundProcessingService worker;
    @Autowired private JpaRefundRepository refunds;
    @Autowired private JpaSubOrderRepository subs;
    @Autowired private JpaMasterOrderRepository orders;
    @Autowired private JpaPaymentRepository payments;
    @Autowired private JpaBookingRepository bookings;
    @Autowired private JpaServiceSlotRepository serviceSlots;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TrackingAsyncTestExecutor testExecutor;
    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private MockMvc mockMvc;

    @MockitoBean(name = "payPalPaymentAdapter")
    private PaymentGatewayPort gateway;

    @AfterEach
    void drainAsyncTasks() throws Exception {
        testExecutor.awaitAll(Duration.ofSeconds(10));
    }

    @Test
    void twoWorkersCreateOneRefundOperation() throws Exception {
        var fixture = createRefundFixture();
        var requestStarted = new CountDownLatch(1);
        var releaseGateway = new CountDownLatch(1);

        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> {
            requestStarted.countDown();
            assertThat(releaseGateway.await(10, TimeUnit.SECONDS)).isTrue();
            return new RefundResult(true, "REFUND-CONCURRENT-01", new BigDecimal("19.31"), "Refund succeeded",
                    GatewayRefundStatus.COMPLETED, "USD");
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var worker1 = executor.submit(() -> worker.processRefund(fixture.refundId()));
            assertThat(requestStarted.await(10, TimeUnit.SECONDS)).isTrue();
            var worker2 = executor.submit(() -> worker.processRefund(fixture.refundId()));

            awaitBlockedRefundLock();
            releaseGateway.countDown();

            boolean result1 = worker1.get(10, TimeUnit.SECONDS);
            boolean result2 = worker2.get(10, TimeUnit.SECONDS);

            assertThat(result1 || result2).isTrue();
        }

        var persistedRefund = refunds.findById(fixture.refundId()).orElseThrow();
        assertThat(persistedRefund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(persistedRefund.getGatewayRequestId()).isNotBlank();
        assertThat(persistedRefund.getProviderAmount()).isEqualByComparingTo(new BigDecimal("19.31"));
        assertThat(persistedRefund.getProviderCurrency()).isEqualTo("USD");
        assertThat(persistedRefund.getProviderRefundId()).isEqualTo("REFUND-CONCURRENT-01");
        assertThat(persistedRefund.getProvider()).isEqualTo(PaymentProvider.PAYPAL);

        verify(gateway, times(1)).requestRefund(any(GatewayRefundRequest.class));
    }

    @Test
    void workerRacingWithVerifiedNotificationAppliesEffectsOnce() throws Exception {
        var fixture = createRefundFixture();
        var workerGatewayStarted = new CountDownLatch(1);
        var releaseWorkerGateway = new CountDownLatch(1);

        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> {
            workerGatewayStarted.countDown();
            assertThat(releaseWorkerGateway.await(10, TimeUnit.SECONDS)).isTrue();
            return new RefundResult(true, "REFUND-RACE-01", new BigDecimal("19.31"), "Refund succeeded",
                    GatewayRefundStatus.COMPLETED, "USD");
        });

        when(gateway.queryRefund(any(GatewayRefundRequest.class), eq("REFUND-RACE-01"))).thenReturn(
                new RefundResult(true, "REFUND-RACE-01", new BigDecimal("19.31"), "Success",
                        GatewayRefundStatus.COMPLETED, "USD"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var workerFuture = executor.submit(() -> worker.processRefund(fixture.refundId()));
            assertThat(workerGatewayStarted.await(10, TimeUnit.SECONDS)).isTrue();

            String operationId;
            try (var reader = Executors.newSingleThreadExecutor()) {
                operationId = reader.submit(() -> jdbc.queryForObject(
                        "SELECT gateway_request_id FROM refunds WHERE id = ?", String.class, fixture.refundId())).get(5, TimeUnit.SECONDS);
            }
            assertThat(operationId).isNotBlank();

            var webhookFuture = executor.submit(() -> worker.processPayPalNotification(
                    "EVENT-RACE-" + fixture.refundId(), "REFUND-RACE-01", operationId));

            awaitBlockedRefundLock();
            releaseWorkerGateway.countDown();

            boolean workerResult = workerFuture.get(10, TimeUnit.SECONDS);
            RefundWebhookResponse webhookResult = webhookFuture.get(10, TimeUnit.SECONDS);

            assertThat(workerResult).isTrue();
            assertThat(webhookResult.status()).isEqualTo(RefundStatus.PROCESSED);
        }

        // Drain any async event listeners
        testExecutor.awaitAll(Duration.ofSeconds(5));

        // 1. Invariant: SubOrder status is REFUNDED
        var subOrder = subs.findById(fixture.subOrderId()).orElseThrow();
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.REFUNDED);

        // 2. Invariant: MasterOrder status is CANCELLED and payment status is REFUNDED
        var order = orders.findById(fixture.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(MasterOrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);

        // 3. Invariant: Booking status is CANCELLED
        var booking = bookings.findById(fixture.bookingId()).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // 4. Invariant: ServiceSlot capacity decrement was applied EXACTLY ONCE (5 booked -> 3 booked)
        var slot = serviceSlots.findById(fixture.slotId()).orElseThrow();
        assertThat(slot.getBookedCount()).isEqualTo(3);

        // 5. Invariant: Notification count in database for this refund is exactly 1
        Long notifCount = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE related_entity_id = ? AND type = 'REFUND_COMPLETED'",
                Long.class, fixture.refundId());
        assertThat(notifCount).isEqualTo(1L);
    }

    @Test
    void administratorCompletesOnlyTheDuplicateCaptureAndAuditsTheEvidenceOnce() throws Exception {
        var fixture = createRefundFixture();
        var canonical = payments.findById(fixture.paymentId()).orElseThrow();
        canonical.setIsCanonical(true);
        payments.saveAndFlush(canonical);

        var duplicate = new PaymentJpaEntity();
        duplicate.setMasterOrderId(fixture.orderId());
        duplicate.setProvider(PaymentProvider.PAYOS);
        duplicate.setProviderTransactionId("PAYOS-" + fixture.orderId());
        duplicate.setAmount(new BigDecimal("1000000"));
        duplicate.setProviderAmount(new BigDecimal("1000000"));
        duplicate.setProviderCurrency("VND");
        duplicate.setStatus(PaymentStatus.SUCCESS);
        duplicate = payments.saveAndFlush(duplicate);

        var extraRefund = new RefundJpaEntity();
        extraRefund.setPaymentId(duplicate.getId());
        extraRefund.setAmount(new BigDecimal("1000000"));
        extraRefund.setRefundPercentage(new BigDecimal("100"));
        extraRefund.setReason(RefundReason.SYSTEM_FAULT);
        extraRefund.setStatus(RefundStatus.PENDING);
        UUID refundId = refunds.saveAndFlush(extraRefund).getId();
        UUID operatorId = createFinanceOperator();
        String reference = "PAYOS-REFUND-" + refundId;

        assertThat(worker.processRefund(refundId)).isFalse();
        var queued = refunds.findById(refundId).orElseThrow();
        assertThat(queued.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(queued.getLastError()).isEqualTo(RefundProcessingService.MANUAL_REVIEW_REQUIRED);
        assertThat(queued.getRetryCount()).isZero();
        assertThat(refunds.findForAutomaticProcessing(RefundStatus.PENDING, RefundProcessingService.MAX_RETRIES,
                RefundProcessingService.MANUAL_REVIEW_REQUIRED)).extracting(RefundJpaEntity::getId).doesNotContain(refundId);
        assertThat(refunds.findByAdvancedFilters(RefundStatus.PENDING, RefundReason.SYSTEM_FAULT, null,
                PaymentProvider.PAYOS, null, fixture.customerId(), fixture.orderId(), null, null, Pageable.unpaged()))
                .extracting(RefundJpaEntity::getId).containsExactly(refundId);

        assertThatThrownBy(() -> worker.completeManualRefund(refundId, operatorId, reference, BigDecimal.ONE, "VND"))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThat(refunds.findById(refundId).orElseThrow().getStatus()).isEqualTo(RefundStatus.PENDING);

        String requestBody = """
                {"providerRefundId":"%s","providerAmount":1000000,"providerCurrency":"VND"}
                """.formatted(reference);
        mockMvc.perform(post("/api/admin/refunds/{id}/manual-completion", refundId)
                        .with(user(fixture.customerId().toString()).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/refunds/{id}/manual-completion", refundId)
                        .with(user(operatorId.toString()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.providerRefundId").value(reference));
        var completed = refunds.findById(refundId).orElseThrow();
        var replayed = worker.completeManualRefund(refundId, operatorId, reference, new BigDecimal("1000000.00"), "VND");
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(completed.getProviderRefundId()).isEqualTo(reference);
        assertThat(replayed.getProcessedAt()).isEqualTo(completed.getProcessedAt());
        assertThat(payments.findById(duplicate.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payments.findById(fixture.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(orders.findById(fixture.orderId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(subs.findById(fixture.subOrderId()).orElseThrow().getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);
        assertThat(bookings.findById(fixture.bookingId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(serviceSlots.findById(fixture.slotId()).orElseThrow().getBookedCount()).isEqualTo(5);
        assertThat(jdbc.queryForList("SELECT metadata FROM audit_logs WHERE action = 'REFUND_MANUALLY_COMPLETED' AND entity_id = ? AND actor_user_id = ?",
                String.class, refundId, operatorId)).singleElement().asString().contains(reference, "1000000", "VND");
    }

    @Test
    void manuallyVerifiedCanonicalZaloPayRefundUpdatesOrderAccounting() {
        var fixture = createRefundFixture();
        var canonical = payments.findById(fixture.paymentId()).orElseThrow();
        canonical.setIsCanonical(true);
        canonical.setProvider(PaymentProvider.ZALOPAY);
        canonical.setProviderCurrency("VND");
        canonical.setProviderAmount(new BigDecimal("1000000"));
        payments.saveAndFlush(canonical);

        assertThat(worker.processRefund(fixture.refundId())).isFalse();
        var completed = worker.completeManualRefund(fixture.refundId(), createFinanceOperator(), "ZALO-REFUND-" + fixture.refundId(),
                new BigDecimal("1000000"), "VND");
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(payments.findById(fixture.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(orders.findById(fixture.orderId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentOrderStatus.REFUNDED);
        assertThat(subs.findById(fixture.subOrderId()).orElseThrow().getStatus()).isEqualTo(SubOrderStatus.REFUNDED);
        assertThat(bookings.findById(fixture.bookingId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(serviceSlots.findById(fixture.slotId()).orElseThrow().getBookedCount()).isEqualTo(3);
    }

    @Test
    void freshProcessorReconcilesTimedOutRequestWithoutResendingMoney() throws Exception {
        var fixture = createRefundFixture();

        when(gateway.requestRefund(any(GatewayRefundRequest.class))).thenAnswer(inv -> {
            GatewayRefundRequest request = inv.getArgument(0);
            try (var reader = Executors.newSingleThreadExecutor()) {
                String committedRequest = reader.submit(() -> jdbc.queryForObject(
                        "SELECT gateway_request_id FROM refunds WHERE id = ?", String.class, fixture.refundId())).get(5, TimeUnit.SECONDS);
                assertThat(committedRequest).isEqualTo(request.requestId());
            }
            throw new PaymentGatewayException("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
        });

        // Worker 1 execution fails with timeout
        assertThat(worker.processRefund(fixture.refundId())).isFalse();

        var refundAfterTimeout = refunds.findById(fixture.refundId()).orElseThrow();
        String stableOperationId = refundAfterTimeout.getGatewayRequestId();
        assertThat(stableOperationId).isNotBlank();
        assertThat(refundAfterTimeout.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refundAfterTimeout.getLastError()).contains("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");

        // Prepare reconcile mock
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenReturn(
                new RefundResult(true, "REFUND-RECONCILED-" + fixture.refundId(), new BigDecimal("19.31"),
                        "Success", GatewayRefundStatus.COMPLETED, "USD"));

        RefundProcessingService restarted = new RefundProcessingService(refunds, subs, orders, payments,
                bookings, serviceSlots, gateway, transactionManager);
        restarted.setEventPublisher(publisher);
        assertThat(restarted).isNotSameAs(worker);
        assertThat(restarted.processRefund(fixture.refundId())).isTrue();

        var processedRefund = refunds.findById(fixture.refundId()).orElseThrow();
        assertThat(processedRefund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(processedRefund.getGatewayRequestId()).isEqualTo(stableOperationId);
        assertThat(processedRefund.getProviderRefundId()).isEqualTo("REFUND-RECONCILED-" + fixture.refundId());

        verify(gateway, times(1)).requestRefund(any(GatewayRefundRequest.class));
        verify(gateway, times(1)).queryRefund(any(GatewayRefundRequest.class), any());
    }

    @Test
    void unverifiableResultRemainsPendingWithoutMoneyEffects() throws Exception {
        var fixture = createRefundFixture();

        // 1. Simulate initial request timeout to enter RECONCILE phase
        when(gateway.requestRefund(any(GatewayRefundRequest.class)))
                .thenThrow(new PaymentGatewayException("GATEWAY_TIMEOUT_AWAITING_VERIFICATION"));
        assertThat(worker.processRefund(fixture.refundId())).isFalse();

        var refundPending = refunds.findById(fixture.refundId()).orElseThrow();
        String initialOperationId = refundPending.getGatewayRequestId();
        assertThat(initialOperationId).isNotBlank();
        assertThat(refundPending.getStatus()).isEqualTo(RefundStatus.PENDING);

        // Case A: Query throws timeout exception
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any()))
                .thenThrow(new PaymentGatewayException("Query connection timeout"));
        assertThat(worker.processRefund(fixture.refundId())).isFalse();

        var afterQueryTimeout = refunds.findById(fixture.refundId()).orElseThrow();
        assertThat(afterQueryTimeout.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(afterQueryTimeout.getGatewayRequestId()).isEqualTo(initialOperationId);
        assertThat(afterQueryTimeout.getLastError()).contains("GATEWAY_TIMEOUT_AWAITING_VERIFICATION: Query connection timeout");

        // Case B: Query returns UNKNOWN / NOT_FOUND
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenReturn(
                new RefundResult(false, null, null, "Not found on gateway", GatewayRefundStatus.UNKNOWN, null));
        assertThat(worker.processRefund(fixture.refundId())).isFalse();

        var afterNotFound = refunds.findById(fixture.refundId()).orElseThrow();
        assertThat(afterNotFound.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(afterNotFound.getGatewayRequestId()).isEqualTo(initialOperationId);

        // Case C: Amount mismatch
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenReturn(
                new RefundResult(true, "REF-MISMATCH-AMT", new BigDecimal("10.00"), "Success", GatewayRefundStatus.COMPLETED, "USD"));
        assertThat(worker.processRefund(fixture.refundId())).isFalse();

        var afterAmtMismatch = refunds.findById(fixture.refundId()).orElseThrow();
        assertThat(afterAmtMismatch.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(afterAmtMismatch.getLastError()).contains("Gateway refund ID, amount, or currency does not match.");

        // Case D: Currency mismatch
        when(gateway.queryRefund(any(GatewayRefundRequest.class), any())).thenReturn(
                new RefundResult(true, "REF-MISMATCH-CURR", new BigDecimal("19.31"), "Success", GatewayRefundStatus.COMPLETED, "EUR"));
        assertThat(worker.processRefund(fixture.refundId())).isFalse();

        var afterCurrMismatch = refunds.findById(fixture.refundId()).orElseThrow();
        assertThat(afterCurrMismatch.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(afterCurrMismatch.getLastError()).contains("Gateway refund ID, amount, or currency does not match.");

        // Invariant: SubOrder is still CONFIRMED, inventory not released, bookedCount still 5
        var subOrder = subs.findById(fixture.subOrderId()).orElseThrow();
        assertThat(subOrder.getStatus()).isEqualTo(SubOrderStatus.CONFIRMED);

        var slot = serviceSlots.findById(fixture.slotId()).orElseThrow();
        assertThat(slot.getBookedCount()).isEqualTo(5);

        testExecutor.awaitAll(Duration.ofSeconds(5));
        // Invariant: No REFUND_COMPLETED notification
        Long notifCount = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE related_entity_id = ? AND type = 'REFUND_COMPLETED'",
                Long.class, fixture.refundId());
        assertThat(notifCount).isZero();
    }

    private void awaitBlockedRefundLock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            Integer blocked = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() "
                    + "AND wait_event_type='Lock' AND state='active' AND query ILIKE '%refunds%'", Integer.class);
            if (blocked != null && blocked > 0) return;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Competing refund transaction never reached the PostgreSQL row lock");
    }

    private record Fixture(
            UUID customerId,
            UUID vendorId,
            UUID serviceId,
            UUID slotId,
            UUID bookingId,
            UUID orderId,
            UUID subOrderId,
            UUID paymentId,
            UUID refundId
    ) {}

    private UUID createFinanceOperator() {
        UUID operatorId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, role, locale, created_at, updated_at, session_version) VALUES (?, ?, 'ADMIN', 'en', now(), now(), 0)",
                operatorId, "finance-" + operatorId + "@example.com");
        return operatorId;
    }

    private Fixture createRefundFixture() {
        UUID customerId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID subOrderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();

        // 1. Customer User
        jdbc.update("INSERT INTO users(id, email, role, locale, created_at, updated_at, session_version) VALUES (?, ?, 'CUSTOMER', 'en', now(), now(), 0)",
                customerId, "cust-" + customerId + "@example.com");

        // 2. Vendor User & Vendor
        UUID vendorUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, role, locale, created_at, updated_at, session_version) VALUES (?, ?, 'VENDOR', 'en', now(), now(), 0)",
                vendorUserId, "vendor-" + vendorUserId + "@example.com");
        jdbc.update("INSERT INTO vendors(id, user_id, business_name, verification_status, created_at, updated_at) VALUES (?, ?, 'Vendor Business', 'APPROVED', now(), now())",
                vendorId, vendorUserId);

        // 3. Category & Service
        jdbc.update("INSERT INTO categories(id, name, slug, is_active, requires_safety_cert, created_at, updated_at) VALUES (?, 'Snorkeling', ?, true, false, now(), now())",
                categoryId, "cat-" + categoryId);
        jdbc.update("INSERT INTO services(id, vendor_id, category_id, name, status, price, weather_sensitive, view_count, created_at, updated_at) VALUES (?, ?, ?, 'Snorkeling Tour', 'PUBLISHED', 1000000, false, 0, now(), now())",
                serviceId, vendorId, categoryId);

        // 4. Slot (capacity = 10, booked_count = 5)
        jdbc.update("INSERT INTO service_slots(id, service_id, date, start_time, end_time, capacity, booked_count, status, inventory_type, created_at, updated_at) VALUES (?, ?, current_date + 5, '08:00', '10:00', 10, 5, 'OPEN', 'PERSON_LIMIT', now(), now())",
                slotId, serviceId);

        // 5. Booking (CONFIRMED)
        jdbc.update("INSERT INTO bookings(id, customer_id, status, total_amount, hold_expires_at, created_at, updated_at) VALUES (?, ?, 'CONFIRMED', 1000000, now() + interval '1 hour', now(), now())",
                bookingId, customerId);

        // 6. MasterOrder (PAID, 1,000,000 VND)
        var order = new MasterOrderJpaEntity();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setBookingId(bookingId);
        order.setStatus(MasterOrderStatus.PAID);
        order.setPaymentStatus(PaymentOrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("1000000"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setIdempotencyKey("order-" + orderId);
        order.setPaymentDeadline(OffsetDateTime.now().plusHours(1));
        order = orders.saveAndFlush(order);

        // 7. SubOrder (CONFIRMED, quantity = 2, slotId)
        var sub = new SubOrderJpaEntity();
        sub.setId(subOrderId);
        sub.setMasterOrderId(orderId);
        sub.setVendorId(vendorId);
        sub.setServiceId(serviceId);
        sub.setSlotId(slotId);
        sub.setQuantity(2);
        sub.setUnitPrice(new BigDecimal("500000"));
        sub.setSubtotalAmount(new BigDecimal("1000000"));
        sub.setFinalAmount(new BigDecimal("1000000"));
        sub.setStatus(SubOrderStatus.CONFIRMED);
        sub = subs.saveAndFlush(sub);

        // 8. Payment (SUCCESS, 1,000,000 VND / 19.31 USD)
        var payment = new PaymentJpaEntity();
        payment.setId(paymentId);
        payment.setMasterOrderId(orderId);
        payment.setProvider(PaymentProvider.PAYPAL);
        payment.setProviderTransactionId("CAPTURE-" + paymentId);
        payment.setProviderOrderId("ORDER-" + paymentId);
        payment.setAmount(new BigDecimal("1000000"));
        payment.setProviderAmount(new BigDecimal("19.31"));
        payment.setProviderCurrency("USD");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setIdempotencyKey("pay-" + paymentId);
        payment = payments.saveAndFlush(payment);

        // 9. Refund (PENDING, 1,000,000 VND, 100%)
        var refund = new RefundJpaEntity();
        refund.setId(refundId);
        refund.setSubOrderId(subOrderId);
        refund.setPaymentId(paymentId);
        refund.setAmount(new BigDecimal("1000000"));
        refund.setRefundPercentage(new BigDecimal("100"));
        refund.setReason(RefundReason.CUSTOMER_REQUEST);
        refund.setStatus(RefundStatus.PENDING);
        refund = refunds.saveAndFlush(refund);

        return new Fixture(customerId, vendorId, serviceId, slotId, bookingId, orderId, subOrderId, paymentId, refundId);
    }
}

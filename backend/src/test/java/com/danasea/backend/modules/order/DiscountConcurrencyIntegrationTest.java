package com.danasea.backend.modules.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.booking.domain.events.BookingHoldExpiredEvent;
import com.danasea.backend.modules.order.application.DiscountReservationService;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.application.dtos.CreateOrderCommand;
import com.danasea.backend.modules.order.application.dtos.CreatePaymentIntentCommand;
import com.danasea.backend.modules.order.application.dtos.MasterOrderDetailResult;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.application.usecases.AdminDiscountCodeUseCase;
import com.danasea.backend.modules.order.application.usecases.CreateOrderUseCase;
import com.danasea.backend.modules.order.application.usecases.CreatePaymentIntentUseCase;
import com.danasea.backend.modules.order.domain.exceptions.InvalidDiscountException;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.ports.OrderEventPublisherPort;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.infrastructure.BookingCancellationFinancialAdapter;
import com.danasea.backend.modules.order.infrastructure.listeners.OrderExpiryEventListener;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountRedemptionRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.dtos.UpdateDiscountCodeRequest;
import com.danasea.backend.security.authorization.BaseSecurityIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest(
        properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
class DiscountConcurrencyIntegrationTest extends BaseSecurityIntegrationTest {
    @Autowired private CreateOrderUseCase creator;
    @Autowired private OrderPaymentService orderService;
    @Autowired private JpaRefundRepository refunds;
    @Autowired private CreatePaymentIntentUseCase intents;
    @Autowired private AdminDiscountCodeUseCase admin;
    @Autowired private JpaDiscountCodeRepository codes;
    @Autowired private JpaDiscountRedemptionRepository redemptions;
    @Autowired private JpaMasterOrderRepository orders;
    @Autowired private JpaSubOrderRepository subOrders;
    @Autowired private JpaPaymentRepository payments;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DisruptionDecisionService decisions;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private BookingCancellationFinancialAdapter cancellation;
    @Autowired private OrderExpiryEventListener expiry;
    @Autowired private DiscountReservationService reservations;
    @MockitoBean private OrderEventPublisherPort publisher;
    @MockitoBean private CommissionPolicyPort commissions;

    @MockitoBean(name = "payPalPaymentAdapter")
    private PaymentGatewayPort gateway;

    @BeforeEach
    void setupCommissions() {
        org.mockito.Mockito.lenient().when(commissions.getCommissionRate(any())).thenReturn(new BigDecimal("0.10"));
    }

    @Test
    void simultaneousOrdersRespectPerCustomerLimit() throws Exception {
        UUID customer = newUser("CUSTOMER");
        UUID first = newBooking(customer);
        UUID second = newBooking(customer);
        var code = newCode(10, 1);
        when(commissions.getCommissionRate(any())).thenReturn(new BigDecimal("0.10"));
        List<String> outcomes =
                raceWhileVoucherLocked(
                        code.getId(),
                        () -> createOutcome(customer, first, code),
                        () -> createOutcome(customer, second, code));
        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "DISCOUNT_USER_LIMIT_EXCEEDED");
        assertThat(codes.findById(code.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
        assertThat(redemptions.countByDiscountCodeIdAndCustomerId(code.getId(), customer))
                .isEqualTo(1);
    }

    @Test
    void simultaneousCustomersRespectGlobalQuota() throws Exception {
        UUID firstCustomer = newUser("CUSTOMER");
        UUID secondCustomer = newUser("CUSTOMER");
        UUID first = newBooking(firstCustomer);
        UUID second = newBooking(secondCustomer);
        var code = newCode(1, null);
        when(commissions.getCommissionRate(any())).thenReturn(new BigDecimal("0.10"));
        List<String> outcomes =
                raceWhileVoucherLocked(
                        code.getId(),
                        () -> createOutcome(firstCustomer, first, code),
                        () -> createOutcome(secondCustomer, second, code));
        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "DISCOUNT_QUOTA_EXCEEDED");
        assertThat(codes.findById(code.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void editCannotOverwriteAnUncommittedCheckoutIncrement() throws Exception {
        var code = newCode(10, null);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var checkout =
                    pool.submit(
                            () ->
                                    new TransactionTemplate(manager)
                                            .executeWithoutResult(
                                                    tx -> {
                                                        codes.findByIdForUpdate(code.getId())
                                                                .orElseThrow();
                                                        codes.incrementUsedCount(code.getId());
                                                        locked.countDown();
                                                        awaitRelease(release);
                                                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var patch =
                    pool.submit(
                            () ->
                                    admin.updateDiscountCode(
                                            code.getId(),
                                            new UpdateDiscountCodeRequest(
                                                    new BigDecimal("25000"),
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null)));
            try {
                awaitBlockedTransactions(1);
            } finally {
                release.countDown();
            }
            checkout.get(15, TimeUnit.SECONDS);
            assertThat(patch.get(15, TimeUnit.SECONDS).discountValue())
                    .isEqualByComparingTo("25000");
        }
        assertThat(codes.findById(code.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void disabledVoucherIsRecheckedAfterCheckoutWaitsForLock() throws Exception {
        UUID customer = newUser("CUSTOMER");
        UUID booking = newBooking(customer);
        var code = newCode(10, null);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var edit =
                    pool.submit(
                            () ->
                                    new TransactionTemplate(manager)
                                            .executeWithoutResult(
                                                    tx -> {
                                                        var managed =
                                                                codes.findByIdForUpdate(
                                                                                code.getId())
                                                                        .orElseThrow();
                                                        managed.setIsActive(false);
                                                        codes.saveAndFlush(managed);
                                                        locked.countDown();
                                                        awaitRelease(release);
                                                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var checkout = pool.submit(() -> createOutcome(customer, booking, code));
            try {
                awaitBlockedTransactions(1);
            } finally {
                release.countDown();
            }
            edit.get(15, TimeUnit.SECONDS);
            assertThat(checkout.get(15, TimeUnit.SECONDS)).isEqualTo("DISCOUNT_INACTIVE");
        }
        assertThat(codes.findById(code.getId()).orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void paymentIntentUsesNetSnapshotAndRetryDoesNotConsumeAnotherVoucher() {
        UUID customer = newUser("CUSTOMER");
        UUID booking = newBooking(customer);
        var code = newCode(10, 1);
        var order = create(customer, booking, code);
        assertThat(order.totalAmount()).isEqualByComparingTo("80000");
        assertThat(order.subOrders().get(0).finalAmount()).isEqualByComparingTo("80000");
        when(gateway.createPaymentIntent(any(), any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            assertThat((BigDecimal) inv.getArgument(2))
                                    .isEqualByComparingTo("80000");
                            return new PaymentIntentResult(
                                    inv.getArgument(0),
                                    inv.getArgument(1),
                                    inv.getArgument(3),
                                    inv.getArgument(2),
                                    "https://sandbox.example.com/approval",
                                    null,
                                    OffsetDateTime.now().plusMinutes(10));
                        });
        var intent =
                intents.execute(
                        new CreatePaymentIntentCommand(
                                customer,
                                order.id(),
                                PaymentProvider.PAYPAL,
                                "discount-payment-" + booking));
        assertThat(payments.findById(intent.paymentId()).orElseThrow().getAmount())
                .isEqualByComparingTo("80000");
        assertThat(create(customer, booking, code).id()).isEqualTo(order.id());
        assertThat(codes.findById(code.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void cancellationReleasesQuotaExactlyOnceAndAllowsReuse() {
        UUID customer = newUser("CUSTOMER");
        UUID booking = newBooking(customer);
        var code = newCode(10, 1);
        var order = create(customer, booking, code);
        cancellation.cancelUnpaidOrder(booking);
        cancellation.cancelUnpaidOrder(booking);
        reservations.release(order.id(), code.getId());
        assertThat(orders.findById(order.id()).orElseThrow().getStatus())
                .isEqualTo(MasterOrderStatus.CANCELLED);
        assertThat(codes.findById(code.getId()).orElseThrow().getUsedCount()).isZero();
        assertThat(redemptions.findByMasterOrderId(order.id())).isEmpty();
        assertThat(create(customer, newBooking(customer), code).totalAmount())
                .isEqualByComparingTo("80000");
    }

    @Test
    void concurrentExpiryReplaysDoNotReleaseAnotherOrdersQuota() throws Exception {
        var code = newCode(10, null);
        UUID customer = newUser("CUSTOMER");
        UUID first = newBooking(customer);
        create(customer, first, code);
        create(customer, newBooking(customer), code);
        var event = new BookingHoldExpiredEvent(first, OffsetDateTime.now());
        raceWhileVoucherLocked(
                code.getId(),
                () -> {
                    expiry.handleBookingHoldExpired(event);
                    return "DONE";
                },
                () -> {
                    expiry.handleBookingHoldExpired(event);
                    return "DONE";
                });
        assertThat(codes.findById(code.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
        assertThat(redemptions.countByDiscountCodeIdAndCustomerId(code.getId(), customer))
                .isEqualTo(1);
    }

    @Test
    void expiryDoesNotCancelPaidOrderOrReturnItsQuota() {
        UUID customer = newUser("CUSTOMER");
        UUID booking = newBooking(customer);
        var code = newCode(10, 1);
        var order = create(customer, booking, code);
        var paid = orders.findById(order.id()).orElseThrow();
        paid.setStatus(MasterOrderStatus.PAID);
        orders.saveAndFlush(paid);
        expiry.handleBookingHoldExpired(new BookingHoldExpiredEvent(booking, OffsetDateTime.now()));
        assertThat(orders.findById(order.id()).orElseThrow().getStatus())
                .isEqualTo(MasterOrderStatus.PAID);
        assertThat(codes.findById(code.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void failedOrderCreationRollsBackAllFinancialAndBookingChanges() {
        UUID customer = newUser("CUSTOMER");
        UUID booking = newBooking(customer);
        var code = newCode(10, 1);
        doThrow(new IllegalStateException("Publisher unavailable"))
                .when(publisher)
                .publishOrderCreatedEvent(any());
        assertThatThrownBy(() -> create(customer, booking, code))
                .isInstanceOf(IllegalStateException.class);
        assertThat(codes.findById(code.getId()).orElseThrow().getUsedCount()).isZero();
        assertThat(redemptions.countByDiscountCodeIdAndCustomerId(code.getId(), customer)).isZero();
        assertThat(orders.findByBookingId(booking)).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM bookings WHERE id=?", String.class, booking))
                .isEqualTo("HOLD");
    }

    @Test
    void zeroPayableVoucherDoesNotReserveQuotaOrCreateOrder() {
        UUID customer = newUser("CUSTOMER");
        UUID booking = newBooking(customer);
        var code = newCode(10, 1);
        code.setDiscountValue(new BigDecimal("100000"));
        codes.saveAndFlush(code);
        assertThatThrownBy(() -> create(customer, booking, code))
                .isInstanceOf(InvalidDiscountException.class)
                .extracting(ex -> ((InvalidDiscountException) ex).getErrorCode())
                .isEqualTo("DISCOUNT_ZERO_PAYABLE_UNSUPPORTED");
        assertThat(codes.findById(code.getId()).orElseThrow().getUsedCount()).isZero();
        assertThat(orders.findByBookingId(booking)).isEmpty();
    }

    @Test
    void discountedCancellationRefundsOnlyCustomerPaidAmount() {
        UUID customer = newUser("CUSTOMER");
        UUID booking = newBooking(customer);
        var code = newCode(10, 1);
        var order = create(customer, booking, code);
        var paid = orders.findById(order.id()).orElseThrow();
        paid.setStatus(MasterOrderStatus.PAID);
        orders.saveAndFlush(paid);
        assertThat(
                        cancellation
                                .requestRefund(
                                        booking,
                                        customer,
                                        "cancel-" + booking,
                                        OffsetDateTime.now())
                                .refundAmount())
                .isEqualByComparingTo("80000");
        assertThat(subOrders.findByMasterOrderId(order.id()).get(0).getFinalAmount())
                .isEqualByComparingTo("80000");
    }

    @Test
    void vendorRejectionRefundsOnlyCustomerPaidSnapshot() {
        UUID customer = newUser("CUSTOMER");
        UUID booking = newBooking(customer);
        var order = create(customer, booking, newCode(10, null));
        var sub = subOrders.findByMasterOrderId(order.id()).get(0);
        sub.setStatus(SubOrderStatus.CONFIRMED);
        subOrders.saveAndFlush(sub);
        var paid = orders.findById(order.id()).orElseThrow();
        paid.setStatus(MasterOrderStatus.PAID);
        paid.setPaymentStatus(PaymentOrderStatus.PAID);
        orders.saveAndFlush(paid);
        orderService.rejectVendorBooking(
                sub.getVendorId(),
                sub.getBookingItemId(),
                "Unable to operate",
                "reject-" + booking);
        assertThat(refunds.findBySubOrderId(sub.getId())).isEmpty();
        decisions.chooseRefund(sub.getId(), customer);
        var refund = refunds.findBySubOrderId(sub.getId()).get(0);
        assertThat(refund.getAmount()).isEqualByComparingTo("80000");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
    }

    private MasterOrderDetailResult create(
            UUID customer, UUID booking, DiscountCodeJpaEntity code) {
        return creator.execute(
                new CreateOrderCommand(
                        customer, booking, "discount-order-" + booking, code.getCode()));
    }

    private String createOutcome(UUID customer, UUID booking, DiscountCodeJpaEntity code) {
        try {
            create(customer, booking, code);
            return "SUCCESS";
        } catch (InvalidDiscountException ex) {
            return ex.getErrorCode();
        }
    }

    private <T> List<T> raceWhileVoucherLocked(UUID codeId, Callable<T> first, Callable<T> second)
            throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var blocker =
                    pool.submit(
                            () ->
                                    new TransactionTemplate(manager)
                                            .executeWithoutResult(
                                                    tx -> {
                                                        codes.findByIdForUpdate(codeId)
                                                                .orElseThrow();
                                                        locked.countDown();
                                                        awaitRelease(release);
                                                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var a = pool.submit(first);
            var b = pool.submit(second);
            try {
                awaitBlockedTransactions(2);
            } finally {
                release.countDown();
            }
            blocker.get(15, TimeUnit.SECONDS);
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private void awaitBlockedTransactions(int count) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            Integer blocked =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()"
                                + " AND wait_event_type='Lock'",
                            Integer.class);
            if (blocked != null && blocked >= count) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Concurrent transactions did not reach the database locks.");
    }

    private void awaitRelease(CountDownLatch release) {
        try {
            assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private UUID newUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id,email,role,locale,created_at,updated_at,session_version) VALUES"
                    + " (?,?,?,'en',now(),now(),0)",
                id,
                "discount-integration-" + id + "@example.com",
                role);
        return id;
    }

    private UUID newBooking(UUID customer) {
        UUID id = UUID.randomUUID();
        UUID vendor = newUser("VENDOR");
        UUID service = UUID.randomUUID();
        UUID slot = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO vendors(id,user_id,business_name,created_at,updated_at) VALUES"
                    + " (?,?,'Discount vendor',now(),now())",
                vendor,
                vendor);
        jdbc.update(
                "INSERT INTO services(id,name,price,status,vendor_id,created_at,updated_at) "
                        + "VALUES (?,'Discount integration',100000,'PUBLISHED',?,now(),now())",
                service,
                vendor);
        jdbc.update(
                "INSERT INTO"
                    + " service_slots(id,service_id,date,start_time,end_time,capacity,booked_count,status,created_at,updated_at)"
                    + " VALUES (?,?,CURRENT_DATE+4,'11:00','12:00',10,0,'OPEN',now(),now())",
                slot,
                service);
        jdbc.update(
                "INSERT INTO"
                    + " bookings(id,customer_id,status,total_amount,hold_expires_at,created_at,updated_at)"
                    + " VALUES (?,?,'HOLD',100000,now()+interval '15 minutes',now(),now())",
                id,
                customer);
        jdbc.update(
                "INSERT INTO"
                    + " booking_items(id,booking_id,service_id,vendor_id,slot_id,quantity,booking_date,booking_time,price,allow_split,capacity_released,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,1,CURRENT_DATE+4,'11:00',100000,false,false,now(),now())",
                UUID.randomUUID(),
                id,
                service,
                vendor,
                slot);
        return id;
    }

    private DiscountCodeJpaEntity newCode(Integer maximum, Integer perCustomer) {
        var code = new DiscountCodeJpaEntity();
        code.setCode("TEST" + UUID.randomUUID().toString().replace("-", ""));
        code.setScope(DiscountScope.PLATFORM);
        code.setSponsorType(DiscountSponsorType.PLATFORM);
        code.setDiscountType(DiscountType.FIXED);
        code.setDiscountValue(new BigDecimal("20000"));
        code.setMaxUses(maximum);
        code.setMaxUsesPerUser(perCustomer);
        return codes.saveAndFlush(code);
    }
}

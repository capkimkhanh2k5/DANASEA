package com.danasea.backend.modules.order.application.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.danasea.backend.modules.booking.domain.exceptions.BookingHoldExpiredException;
import com.danasea.backend.modules.booking.domain.exceptions.BookingNotFoundException;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.domain.exceptions.ExchangeRateUnavailableException;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentAttemptConflictException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.exceptions.WaiverAcceptanceRequiredException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.MissingWaiverItem;
import com.danasea.backend.modules.order.domain.models.PaymentAttemptState;
import com.danasea.backend.modules.order.domain.models.PaymentCancelStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.ports.PaymentCancelResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.ports.ServiceWaiverLookupPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.infrastructure.adapters.PaymentGatewayRegistry;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.presentation.dtos.PaymentStatusResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentWindowResponse;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;

import lombok.extern.slf4j.Slf4j;

/**
 * PaymentCoordinator — Trọng tài điều phối thanh toán 5+5 thống nhất cho DANASEA.
 * Thực thi các nguyên tắc cốt lõi:
 * 1. HOLD 300s + PAYMENT 300s cố định (t0 không bị reset khi retry/switch/refresh).
 * 2. 3-Phase attempt creation ngoài long-running DB transaction.
 * 3. Ngăn chặn 2 provider attempts mở đồng thời (hủy link cũ trước khi tạo link mới).
 * 4. Đồng bộ Redis Hold extension (420s) và closing grace (120s) bảo vệ inventory.
 */
@Service
@Slf4j
public class PaymentCoordinator {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");
    private static final int PAYMENT_WINDOW_SECONDS = 300;
    private static final int REDIS_PAYMENT_GRACE_SECONDS = 120; // 300 + 120 = 420s total

    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaPaymentRepository paymentRepository;
    private final JpaBookingRepository bookingRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final InventoryLockPort inventoryLockPort;
    private final TransactionTemplate transaction;
    private final SubOrderRepositoryPort subOrderRepository;
    private final ServiceWaiverLookupPort serviceWaiverLookupPort;
    private final LocalizedContentSelector localizedContentSelector;
    private final OrderPaymentService orderPaymentService;
    private DepartureCheckoutGuard departureCheckout;
    private PaymentProviderAvailability providerAvailability;

    @Autowired
    public void setProviderAvailability(PaymentProviderAvailability providerAvailability) {
        this.providerAvailability = providerAvailability;
    }

    @Autowired
    public void setDepartureCheckout(DepartureCheckoutGuard departureCheckout) {
        this.departureCheckout = departureCheckout;
    }

    @Autowired
    public PaymentCoordinator(
            JpaMasterOrderRepository masterOrderRepository,
            JpaPaymentRepository paymentRepository,
            JpaBookingRepository bookingRepository,
            PaymentGatewayRegistry gatewayRegistry,
            InventoryLockPort inventoryLockPort,
            PlatformTransactionManager transactionManager,
            SubOrderRepositoryPort subOrderRepository,
            ServiceWaiverLookupPort serviceWaiverLookupPort,
            LocalizedContentSelector localizedContentSelector,
            @Lazy OrderPaymentService orderPaymentService) {
        this.masterOrderRepository = masterOrderRepository;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.inventoryLockPort = inventoryLockPort;
        this.subOrderRepository = subOrderRepository;
        this.serviceWaiverLookupPort = serviceWaiverLookupPort;
        this.localizedContentSelector = localizedContentSelector;
        this.orderPaymentService = orderPaymentService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Bắt đầu hoặc lấy lại payment attempt đang hoạt động cho đơn hàng.
     */
    public PaymentWindowResponse startPayment(UUID customerId, UUID orderId, PaymentProvider provider, String idempotencyKey) {
        validateInputs(customerId, orderId, provider, idempotencyKey);
        requireEnabledProvider(provider);

        // Phase 1 (DB transaction): Khởi tạo hoặc tái sử dụng attempt, cố định payment_deadline
        PrepareResult prepareResult = Objects.requireNonNull(transaction.execute(status -> prepareAttempt(customerId, orderId, provider, idempotencyKey)));

        if (prepareResult.isReused()) {
            return toPaymentWindowResponse(prepareResult.payment(), prepareResult.deadline());
        }

        UUID paymentId = prepareResult.payment().getId();
        OffsetDateTime fixedDeadline = prepareResult.deadline();

        // Phase 2 (Network call ngoài DB transaction): Gọi cổng thanh toán
        PaymentIntentResult gatewayResult;
        try {
            PaymentGatewayPort adapter = gatewayRegistry.getAdapter(provider);
            gatewayResult = adapter.createPaymentIntent(
                    paymentId,
                    orderId,
                    prepareResult.payment().getAmount(),
                    provider,
                    prepareResult.gatewayOrderCode(),
                    fixedDeadline,
                    prepareResult.payment().getProviderOrderId()
            );
            if (gatewayResult == null || gatewayResult.paymentUrl() == null || gatewayResult.paymentUrl().isBlank()) {
                throw new PaymentGatewayException("Gateway returned empty payment URL.");
            }
        } catch (Exception ex) {
            log.warn("Could not confirm creation of {} payment {}", provider, paymentId, ex);
            transaction.executeWithoutResult(status -> paymentRepository.findByIdForUpdate(paymentId)
                    .filter(payment -> payment.getStatus() == PaymentStatus.PENDING
                            && payment.getAttemptState() == PaymentAttemptState.CREATING)
                    .ifPresent(payment -> {
                        payment.setLastError(ex.getMessage());
                        if (ex instanceof InvalidOrderStateException || ex instanceof IllegalArgumentException
                                || ex instanceof ExchangeRateUnavailableException) {
                            payment.setStatus(PaymentStatus.FAILED);
                            payment.setAttemptState(PaymentAttemptState.FAILED);
                        } else {
                            payment.setCancelStatus(PaymentCancelStatus.CANCEL_UNKNOWN);
                        }
                        paymentRepository.save(payment);
                    }));
            throw (ex instanceof RuntimeException re) ? re : new PaymentGatewayException(ex.getMessage(), ex);
        }

        // Phase 3 (DB transaction): Kích hoạt attempt thành công
        PaymentJpaEntity activatedPayment = Objects.requireNonNull(transaction.execute(status -> activateAttempt(paymentId, gatewayResult, fixedDeadline)));

        if (!Boolean.TRUE.equals(activatedPayment.getIsActive()) || !fixedDeadline.isAfter(OffsetDateTime.now())) {
            throw PaymentAttemptConflictException.expiredWindow();
        }
        return toPaymentWindowResponse(activatedPayment, fixedDeadline);
    }

    /**
     * Chuyển đổi an toàn sang cổng thanh toán khác trong cùng cửa sổ 5+5.
     * Hủy provider attempt cũ trước khi mở link mới.
     */
    public PaymentWindowResponse switchProvider(UUID customerId, UUID orderId, PaymentProvider targetProvider, String idempotencyKey) {
        validateInputs(customerId, orderId, targetProvider, idempotencyKey);
        requireEnabledProvider(targetProvider);

        // Bước 1: Khóa MasterOrder và kiểm tra attempt hiện tại
        AttemptSwitchContext switchCtx = Objects.requireNonNull(transaction.execute(status -> prepareSwitchContext(customerId, orderId, targetProvider, idempotencyKey)));

        // Bước 2: Hủy server-side cổng cũ (ngoài DB transaction nếu có thể)
        if (switchCtx.hasActiveToCancel()) {
            PaymentJpaEntity oldPayment = switchCtx.activePayment();
            PaymentGatewayPort oldAdapter = gatewayRegistry.getAdapter(oldPayment.getProvider());

            if (!oldAdapter.canCancelServerSide(oldPayment.getProvider())) {
                throw new InvalidOrderStateException("The previous payment link cannot be canceled by DANASEA.");
            }
            PaymentCancelResult cancellation = oldAdapter.cancelPayment(
                    oldPayment.getProvider(), oldPayment.getProviderOrderId(), "User switched provider to " + targetProvider);
            if (cancellation == null || !cancellation.success() || cancellation.status() != PaymentCancelStatus.CANCELED) {
                throw new InvalidOrderStateException("The previous payment has not been verified as canceled. Check its status first.");
            }
            transaction.executeWithoutResult(status -> paymentRepository.findByIdForUpdate(oldPayment.getId())
                    .filter(payment -> payment.getStatus() == PaymentStatus.PENDING && Boolean.TRUE.equals(payment.getIsActive()))
                    .ifPresent(payment -> {
                        markCanceled(payment);
                        paymentRepository.save(payment);
                    }));
        }

        // Bước 3: Tạo attempt mới cho targetProvider
        return startPayment(customerId, orderId, targetProvider, idempotencyKey);
    }

    /**
     * Tra cứu trạng thái thanh toán và đồng hồ đếm ngược.
     */
    public PaymentStatusResponse getPaymentStatus(UUID customerId, UUID orderId) {
        MasterOrderJpaEntity order = masterOrderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getCustomerId().equals(customerId)) {
            throw new UnauthorizedOrderAccessException(orderId, customerId);
        }

        if (order.getStatus() == MasterOrderStatus.PENDING_PAYMENT && orderPaymentService != null) {
            try {
                if (orderPaymentService.reconcilePaymentWithGateway(orderId)) {
                    order = masterOrderRepository.findById(orderId).orElse(order);
                }
            } catch (Exception ex) {
                log.warn("Reconcile payment failed during getPaymentStatus for order {}: {}", orderId, ex.getMessage());
            }
        }

        OffsetDateTime deadline = order.getPaymentDeadline();
        OffsetDateTime now = OffsetDateTime.now();
        long remainingSeconds = deadline == null ? 0 : Math.max(0, Duration.between(now, deadline).toSeconds());
        boolean isExpired = deadline != null && !deadline.isAfter(now);
        PaymentJpaEntity activePayment = isExpired || order.getStatus() != MasterOrderStatus.PENDING_PAYMENT
                ? null : paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId).orElse(null);

        return new PaymentStatusResponse(
                order.getId(),
                order.getStatus(),
                order.getPaymentStatus(),
                deadline,
                remainingSeconds,
                isExpired,
                activePayment != null ? activePayment.getId() : null,
                activePayment != null ? activePayment.getProvider() : null,
                activePayment != null ? activePayment.getAttemptState() : null,
                activePayment != null ? activePayment.getAttemptNumber() : null,
                activePayment != null ? activePayment.getPaymentUrl() : null,
                activePayment != null ? activePayment.getQrCodeUrl() : null
        );
    }

    @Scheduled(fixedDelay = 30000)
    public void sweepExpiredClosingOrders() {
        OffsetDateTime now = OffsetDateTime.now();
        var attempts = paymentRepository.findExpiredActiveAttempts(PaymentStatus.PENDING,
                MasterOrderStatus.PENDING_PAYMENT, now);
        for (PaymentJpaEntity attempt : attempts) {
            try {
                closeExpiredAttempt(attempt);
            } catch (RuntimeException ex) {
                log.warn("Could not close expired payment {}", attempt.getId(), ex);
            }
        }
    }

    private void closeExpiredAttempt(PaymentJpaEntity snapshot) {
        PaymentCancelStatus outcome = PaymentCancelStatus.CANCEL_UNKNOWN;
        if (gatewayRegistry.supports(snapshot.getProvider())) {
            PaymentGatewayPort adapter = gatewayRegistry.getAdapter(snapshot.getProvider());
            if (adapter.canCancelServerSide(snapshot.getProvider()) && snapshot.getProviderOrderId() != null) {
                PaymentCancelResult cancellation = adapter.cancelPayment(
                        snapshot.getProvider(), snapshot.getProviderOrderId(), "DANASEA deadline expired");
                if (cancellation != null && cancellation.success() && cancellation.status() == PaymentCancelStatus.CANCELED) {
                    outcome = PaymentCancelStatus.CANCELED;
                } else if (cancellation != null && cancellation.status() == PaymentCancelStatus.ALREADY_CAPTURED) {
                    outcome = PaymentCancelStatus.ALREADY_CAPTURED;
                }
            }
        }

        PaymentCancelStatus verifiedOutcome = outcome;
        transaction.executeWithoutResult(status -> paymentRepository.findByIdForUpdate(snapshot.getId())
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING && Boolean.TRUE.equals(payment.getIsActive()))
                .ifPresent(payment -> {
                    if (verifiedOutcome == PaymentCancelStatus.CANCELED) {
                        markCanceled(payment);
                    } else {
                        payment.setIsActive(false);
                        payment.setCancelStatus(verifiedOutcome);
                        payment.setReconciliationNextAttemptAt(null);
                    }
                    paymentRepository.save(payment);
                }));
        // BookingHoldCleanupJob owns inventory release and order cancellation after the hold grace.
    }

    private void markCanceled(PaymentJpaEntity payment) {
        payment.setIsActive(false);
        payment.setAttemptState(PaymentAttemptState.TERMINATED);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setCancelStatus(PaymentCancelStatus.CANCELED);
        payment.setCanceledAt(OffsetDateTime.now());
    }

    private PrepareResult prepareAttempt(UUID customerId, UUID orderId, PaymentProvider provider, String idempotencyKey) {
        MasterOrderJpaEntity order = masterOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getCustomerId().equals(customerId)) {
            throw new UnauthorizedOrderAccessException(orderId, customerId);
        }

        if (order.getStatus() == MasterOrderStatus.CANCELLED
                && order.getPaymentDeadline() != null && !order.getPaymentDeadline().isAfter(OffsetDateTime.now())) {
            throw PaymentAttemptConflictException.expiredWindow();
        }
        if (order.getStatus() != MasterOrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderStateException("Payment can only be initiated for orders in PENDING_PAYMENT status; current: " + order.getStatus());
        }

        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderStateException("The order amount must be positive.");
        }

        // Kiểm tra Waiver
        validateWaivers(orderId);

        // Kiểm tra PR #185 Departure Guard
        if (departureCheckout != null && order.getBookingId() != null) {
            BookingJpaEntity b = departureCheckout.lockBooking(order.getBookingId());
            departureCheckout.requireBookable(b);
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime fixedDeadline = order.getPaymentDeadline();
        if (fixedDeadline != null && !fixedDeadline.isAfter(now)) {
            throw PaymentAttemptConflictException.expiredWindow();
        }

        PaymentJpaEntity previous = paymentRepository.findByMasterOrderIdAndIdempotencyKey(orderId, idempotencyKey.trim())
                .orElse(null);
        if (previous != null && (previous.getProvider() != provider || previous.getStatus() != PaymentStatus.PENDING)) {
            throw PaymentAttemptConflictException.closedAttempt();
        }

        var activeOpt = paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId);
        if (activeOpt.isPresent()) {
            PaymentJpaEntity active = activeOpt.get();
            if (active.getProvider() != provider) {
                throw new InvalidOrderStateException("An active payment attempt exists for provider "
                        + active.getProvider() + ". Use switch-provider to change gateways.");
            }
            if (active.getStatus() == PaymentStatus.PENDING && active.getAttemptState() == PaymentAttemptState.PENDING
                    && active.getPaymentUrl() != null && !active.getPaymentUrl().isBlank()
                    && fixedDeadline != null && (active.getExpiresAt() == null || active.getExpiresAt().isAfter(now))) {
                return new PrepareResult(active, fixedDeadline, true, active.getPayosOrderCode());
            }
            throw new InvalidOrderStateException("The previous payment is awaiting verification.");
        }

        var inFlight = paymentRepository.findInFlightAttempts(orderId, List.of(PaymentAttemptState.CREATING));
        if (!inFlight.isEmpty()) {
            PaymentJpaEntity pendingCreation = inFlight.getFirst();
            if (previous != null && previous.getId().equals(pendingCreation.getId()) && fixedDeadline != null
                    && pendingCreation.getCancelStatus() == PaymentCancelStatus.CANCEL_UNKNOWN
                    && (provider == PaymentProvider.PAYPAL || provider == PaymentProvider.VNPAY)) {
                // Both adapters reuse the persisted payment ID as the gateway idempotency key/reference.
                pendingCreation.setCancelStatus(PaymentCancelStatus.NONE);
                paymentRepository.save(pendingCreation);
                return new PrepareResult(pendingCreation, fixedDeadline, false, null);
            }
            throw new InvalidOrderStateException("A payment link is being generated or awaiting verification.");
        }
        if (paymentRepository.existsByMasterOrderIdAndStatus(orderId, PaymentStatus.PENDING)) {
            throw new InvalidOrderStateException("A previous payment is still awaiting verification.");
        }

        int previousAttempts = paymentRepository.findMaxAttemptNumberByMasterOrderId(orderId);
        // A new order initially stores its booking hold deadline. The first payment opens a separate window.
        if (previousAttempts == 0 || fixedDeadline == null) {
            fixedDeadline = now.plusSeconds(PAYMENT_WINDOW_SECONDS);
            if (order.getBookingId() != null) {
                BookingJpaEntity booking = bookingRepository.findById(order.getBookingId())
                        .orElseThrow(() -> new BookingNotFoundException(order.getBookingId()));
                if (booking.getHoldExpiresAt() != null && !booking.getHoldExpiresAt().isAfter(now)) {
                    throw new BookingHoldExpiredException(order.getBookingId());
                }

                List<InventoryLockItem> lockItems = toLockItems(booking);
                if (!inventoryLockPort.extendHolds(booking.getId(), lockItems,
                        Duration.ofSeconds(PAYMENT_WINDOW_SECONDS + REDIS_PAYMENT_GRACE_SECONDS))) {
                    throw new BookingHoldExpiredException(booking.getId());
                }
                booking.setHoldExpiresAt(fixedDeadline.plusSeconds(REDIS_PAYMENT_GRACE_SECONDS));
                bookingRepository.save(booking);
            }
            order.setPaymentDeadline(fixedDeadline);
            masterOrderRepository.save(order);
        }

        int nextAttemptNumber = previousAttempts + 1;
        Long gatewayOrderCode = (provider == PaymentProvider.PAYOS || provider == PaymentProvider.ZALOPAY)
                ? paymentRepository.getNextPayOSOrderCode()
                : null;

        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setMasterOrderId(orderId);
        payment.setProvider(provider);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAttemptState(PaymentAttemptState.CREATING);
        payment.setAttemptNumber(nextAttemptNumber);
        payment.setIsActive(false);
        payment.setIsCanonical(false);
        payment.setDanaseaDeadlineAt(fixedDeadline);
        payment.setIdempotencyKey(idempotencyKey.trim());
        if (provider == PaymentProvider.PAYOS) {
            payment.setPayosOrderCode(gatewayOrderCode);
            payment.setProviderOrderId(String.valueOf(gatewayOrderCode));
        } else if (provider == PaymentProvider.ZALOPAY) {
            String merchantDate = now.atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(DateTimeFormatter.ofPattern("yyMMdd"));
            payment.setProviderOrderId(merchantDate + "_" + gatewayOrderCode);
        }
        if (provider != PaymentProvider.PAYPAL) {
            payment.setProviderAmount(payment.getAmount());
            payment.setProviderCurrency("VND");
        }
        payment.setExpiresAt(fixedDeadline);

        payment = paymentRepository.save(payment);
        return new PrepareResult(payment, fixedDeadline, false, gatewayOrderCode);
    }

    private PaymentJpaEntity activateAttempt(UUID paymentId, PaymentIntentResult result, OffsetDateTime fixedDeadline) {
        PaymentJpaEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new InvalidOrderStateException("Payment record not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING || payment.getAttemptState() != PaymentAttemptState.CREATING) {
            return payment;
        }
        MasterOrderJpaEntity order = masterOrderRepository.findByIdForUpdate(payment.getMasterOrderId())
                .orElseThrow(() -> new OrderNotFoundException(payment.getMasterOrderId()));
        payment.setPaymentUrl(result.paymentUrl());
        payment.setQrCodeUrl(result.qrCodeUrl());
        if (result.providerOrderId() != null) {
            payment.setProviderOrderId(result.providerOrderId());
        }
        if (result.providerAmount() != null) {
            payment.setProviderAmount(result.providerAmount());
        }
        if (result.providerCurrency() != null) {
            payment.setProviderCurrency(result.providerCurrency());
        }
        payment.setExchangeRate(result.exchangeRate());
        payment.setExchangeRateDate(result.exchangeRateDate());
        payment.setExchangeRateSource(result.exchangeRateSource());
        payment.setProviderTransactionDate(result.providerTransactionDate());

        OffsetDateTime providerExpiresAt = result.expiresAt();
        payment.setProviderExpiresAt(providerExpiresAt);

        // provider_expires_at không được kéo dài quá danaseaDeadline
        if (providerExpiresAt != null && providerExpiresAt.isBefore(fixedDeadline)) {
            payment.setExpiresAt(providerExpiresAt);
        } else {
            payment.setExpiresAt(fixedDeadline);
        }

        payment.setAttemptState(PaymentAttemptState.PENDING);
        payment.setLastError(null);
        payment.setIsActive(order.getStatus() == MasterOrderStatus.PENDING_PAYMENT
                && fixedDeadline.isAfter(OffsetDateTime.now()));

        return paymentRepository.save(payment);
    }

    private AttemptSwitchContext prepareSwitchContext(UUID customerId, UUID orderId, PaymentProvider targetProvider, String idempotencyKey) {
        MasterOrderJpaEntity order = masterOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getCustomerId().equals(customerId)) {
            throw new UnauthorizedOrderAccessException(orderId, customerId);
        }

        if (order.getStatus() != MasterOrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderStateException("Cannot switch provider for non-pending order; status: " + order.getStatus());
        }

        OffsetDateTime deadline = order.getPaymentDeadline();
        if (deadline == null || OffsetDateTime.now().isAfter(deadline)) {
            throw PaymentAttemptConflictException.expiredWindow();
        }

        long remainingSeconds = Duration.between(OffsetDateTime.now(), deadline).toSeconds();
        if (remainingSeconds < 5) {
            throw new InvalidOrderStateException("Insufficient time remaining (" + remainingSeconds + "s) to switch payment provider.");
        }

        PaymentJpaEntity previous = paymentRepository.findByMasterOrderIdAndIdempotencyKey(orderId, idempotencyKey.trim())
                .orElse(null);
        if (previous != null && (previous.getProvider() != targetProvider || previous.getStatus() != PaymentStatus.PENDING)) {
            throw PaymentAttemptConflictException.closedAttempt();
        }
        PaymentJpaEntity activePayment = paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId).orElse(null);
        return new AttemptSwitchContext(activePayment, activePayment != null && activePayment.getProvider() != targetProvider);
    }

    private void requireEnabledProvider(PaymentProvider provider) {
        if (providerAvailability != null && !providerAvailability.isEnabled(provider)) {
            throw new InvalidOrderStateException("The selected payment provider is unavailable.");
        }
    }

    private void validateInputs(UUID customerId, UUID orderId, PaymentProvider provider, String idempotencyKey) {
        if (customerId == null || orderId == null || provider == null) {
            throw new IllegalArgumentException("Customer ID, Order ID and Payment Provider are required.");
        }
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey.trim()).matches()) {
            throw new IllegalArgumentException("Idempotency-Key must contain 8 to 100 letters, digits, dots, underscores, colons, or hyphens.");
        }
    }

    private void validateWaivers(UUID orderId) {
        if (subOrderRepository != null) {
            List<SubOrder> subOrders = subOrderRepository.findByMasterOrderIdForUpdate(orderId);
            List<MissingWaiverItem> missingSubOrders = new ArrayList<>();
            for (var sub : subOrders) {
                WaiverAcceptanceGuard.missing(sub.getId(), sub.getServiceId(), sub.getWaiverVersion(),
                        sub.getWaiverRequired(), sub.getWaiverAccepted(), sub.getWaiverContent(), sub.getWaiverContentEn(),
                        serviceWaiverLookupPort, localizedContentSelector).ifPresent(missingSubOrders::add);
            }
            if (!missingSubOrders.isEmpty()) {
                throw new WaiverAcceptanceRequiredException(orderId, missingSubOrders);
            }
        }
    }

    private List<InventoryLockItem> toLockItems(BookingJpaEntity booking) {
        if (booking == null || booking.getItems() == null) {
            return Collections.emptyList();
        }
        return booking.getItems().stream()
                .map(item -> InventoryLockItem.of(item.getSlotId(), item.getQuantity()))
                .toList();
    }

    private PaymentWindowResponse toPaymentWindowResponse(PaymentJpaEntity payment, OffsetDateTime fixedDeadline) {
        long remaining = Math.max(0, Duration.between(OffsetDateTime.now(), fixedDeadline).toSeconds());
        return new PaymentWindowResponse(
                payment.getId(),
                payment.getMasterOrderId(),
                payment.getProvider(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getAttemptState(),
                payment.getAttemptNumber(),
                payment.getPaymentUrl(),
                payment.getQrCodeUrl(),
                fixedDeadline,
                remaining,
                payment.getProviderExpiresAt()
        );
    }

    private record PrepareResult(PaymentJpaEntity payment, OffsetDateTime deadline, boolean isReused, Long gatewayOrderCode) {}
    private record AttemptSwitchContext(PaymentJpaEntity activePayment, boolean hasActiveToCancel) {}
}

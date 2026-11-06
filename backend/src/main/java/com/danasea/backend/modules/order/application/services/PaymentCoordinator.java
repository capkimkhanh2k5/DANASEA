package com.danasea.backend.modules.order.application.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
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
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.OrderPaymentService;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.exceptions.WaiverAcceptanceRequiredException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.MissingWaiverItem;
import com.danasea.backend.modules.order.domain.models.PaymentAttemptState;
import com.danasea.backend.modules.order.domain.models.PaymentCancelStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.ports.PaymentCancelResult;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
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
                    fixedDeadline
            );
            if (gatewayResult == null || gatewayResult.paymentUrl() == null || gatewayResult.paymentUrl().isBlank()) {
                throw new PaymentGatewayException("Gateway returned empty payment URL.");
            }
        } catch (Exception ex) {
            log.error("Failed to create payment intent with provider {} for paymentId={}", provider, paymentId, ex);
            transaction.executeWithoutResult(status -> {
                paymentRepository.findByIdForUpdate(paymentId).ifPresent(p -> {
                    p.setLastError(ex.getMessage());
                    // Nếu là exception mạng hoặc timeout, giữ attempt ở CREATING với CANCEL_UNKNOWN
                    // để không tạo attempt thứ 2 trùng lặp khi chưa kiểm chứng được trạng thái gateway
                    if (ex instanceof java.net.SocketTimeoutException
                            || ex instanceof java.util.concurrent.TimeoutException
                            || (ex.getCause() instanceof java.net.SocketTimeoutException)) {
                        p.setCancelStatus(PaymentCancelStatus.CANCEL_UNKNOWN);
                    } else {
                        p.setAttemptState(PaymentAttemptState.FAILED);
                        p.setStatus(PaymentStatus.FAILED);
                    }
                    paymentRepository.save(p);
                });
            });
            throw (ex instanceof RuntimeException re) ? re : new PaymentGatewayException(ex.getMessage(), ex);
        }

        // Phase 3 (DB transaction): Kích hoạt attempt thành công
        PaymentJpaEntity activatedPayment = Objects.requireNonNull(transaction.execute(status -> activateAttempt(paymentId, gatewayResult, fixedDeadline)));

        return toPaymentWindowResponse(activatedPayment, fixedDeadline);
    }

    /**
     * Chuyển đổi an toàn sang cổng thanh toán khác trong cùng cửa sổ 5+5.
     * Hủy provider attempt cũ trước khi mở link mới.
     */
    public PaymentWindowResponse switchProvider(UUID customerId, UUID orderId, PaymentProvider targetProvider, String idempotencyKey) {
        validateInputs(customerId, orderId, targetProvider, idempotencyKey);

        // Bước 1: Khóa MasterOrder và kiểm tra attempt hiện tại
        AttemptSwitchContext switchCtx = Objects.requireNonNull(transaction.execute(status -> prepareSwitchContext(customerId, orderId, targetProvider)));

        if (switchCtx.sameProviderActive()) {
            return toPaymentWindowResponse(switchCtx.activePayment(), switchCtx.deadline());
        }

        // Bước 2: Hủy server-side cổng cũ (ngoài DB transaction nếu có thể)
        if (switchCtx.hasActiveToCancel()) {
            PaymentJpaEntity oldPayment = switchCtx.activePayment();
            PaymentGatewayPort oldAdapter = gatewayRegistry.getAdapter(oldPayment.getProvider());

            if (oldAdapter.canCancelServerSide(oldPayment.getProvider())) {
                PaymentCancelResult cancelResult = oldAdapter.cancelPayment(
                        oldPayment.getProvider(),
                        oldPayment.getProviderOrderId(),
                        "User switched provider to " + targetProvider
                );

                if (cancelResult.status() == PaymentCancelStatus.ALREADY_CAPTURED) {
                    throw new InvalidOrderStateException("Previous payment on " + oldPayment.getProvider()
                            + " has already been captured. Please wait for confirmation.");
                }

                if (!cancelResult.success() && cancelResult.status() == PaymentCancelStatus.CANCEL_UNKNOWN) {
                    throw new InvalidOrderStateException("Unable to verify cancellation on " + oldPayment.getProvider()
                            + " (" + cancelResult.message() + "). Please try switching again shortly.");
                }

                // Cập nhật kết quả hủy attempt cũ trong DB
                transaction.executeWithoutResult(status -> {
                    paymentRepository.findByIdForUpdate(oldPayment.getId()).ifPresent(p -> {
                        p.setIsActive(false);
                        p.setAttemptState(PaymentAttemptState.TERMINATED);
                        p.setCanceledAt(OffsetDateTime.now());
                        p.setCancelStatus(cancelResult.status());
                        paymentRepository.save(p);
                    });
                });
            } else {
                // Provider không hỗ trợ hủy API (như VNPay) -> Kiểm tra xem attempt đã hết hiệu lực tự nhiên chưa
                OffsetDateTime providerExpiresAt = oldPayment.getProviderExpiresAt();
                boolean naturalExpired = providerExpiresAt != null && providerExpiresAt.isBefore(OffsetDateTime.now());

                if (!naturalExpired) {
                    // Nếu attempt chưa hết hạn và không thể hủy server-side: TỪ CHỐI CHUYỂN CỔNG AN TOÀN
                    throw new InvalidOrderStateException("Cannot switch payment provider: " + oldPayment.getProvider()
                            + " session is still active and cannot be canceled server-side. Please complete payment using "
                            + oldPayment.getProvider() + " or wait until the session expires.");
                }

                // Đã hết hiệu lực tự nhiên an toàn -> cập nhật attempt cũ
                transaction.executeWithoutResult(status -> {
                    paymentRepository.findByIdForUpdate(oldPayment.getId()).ifPresent(p -> {
                        p.setIsActive(false);
                        p.setAttemptState(PaymentAttemptState.TERMINATED);
                        p.setCanceledAt(OffsetDateTime.now());
                        p.setCancelStatus(PaymentCancelStatus.CANCELED);
                        paymentRepository.save(p);
                    });
                });
            }
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
        long remainingSeconds = 0;
        boolean isExpired = false;

        if (deadline != null) {
            remainingSeconds = Duration.between(OffsetDateTime.now(), deadline).toSeconds();
            if (remainingSeconds < 0) {
                remainingSeconds = 0;
                isExpired = true;
            }
        }

        PaymentJpaEntity activePayment = paymentRepository.findByMasterOrderIdAndIsActiveTrue(orderId).orElse(null);

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

    /**
     * Quét các đơn hàng PENDING_PAYMENT đã vượt quá payment_deadline để đóng an toàn.
     */
    @Scheduled(fixedDelay = 30000)
    public void sweepExpiredClosingOrders() {
        OffsetDateTime now = OffsetDateTime.now();
        List<MasterOrderJpaEntity> candidates = masterOrderRepository.findAll().stream()
                .filter(o -> o.getStatus() == MasterOrderStatus.PENDING_PAYMENT
                        && o.getPaymentDeadline() != null
                        && o.getPaymentDeadline().isBefore(now))
                .toList();

        for (MasterOrderJpaEntity order : candidates) {
            try {
                processClosingOrder(order.getId());
            } catch (Exception ex) {
                log.error("Error sweeping expired order {}", order.getId(), ex);
            }
        }
    }

    private void processClosingOrder(UUID orderId) {
        transaction.executeWithoutResult(status -> {
            MasterOrderJpaEntity order = masterOrderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order == null || order.getStatus() != MasterOrderStatus.PENDING_PAYMENT) {
                return;
            }

            var activeOpt = paymentRepository.findActiveAttemptForUpdate(orderId);
            if (activeOpt.isPresent()) {
                PaymentJpaEntity active = activeOpt.get();
                PaymentGatewayPort adapter = gatewayRegistry.getAdapter(active.getProvider());

                // Kiểm tra xem khách có kịp thanh toán sát giây cuối không
                try {
                    PaymentCaptureResult captureResult = adapter.queryPayment(
                            active.getProvider(),
                            active.getProviderOrderId(),
                            active.getProviderTransactionDate()
                    );
                    if (captureResult != null && captureResult.success()) {
                        log.info("Order {} captured at gateway {} right at deadline expiry.", orderId, active.getProvider());
                        return; // Sẽ được webhook hoặc capture flow xử lý
                    }
                } catch (Exception ignored) {
                }

                // Nếu chưa thanh toán, thực hiện hủy server-side nếu hỗ trợ
                if (adapter.canCancelServerSide(active.getProvider())) {
                    try {
                        adapter.cancelPayment(active.getProvider(), active.getProviderOrderId(), "DANASEA deadline expired");
                    } catch (Exception e) {
                        log.warn("Failed to cancel provider order {} on expiry: {}", active.getProviderOrderId(), e.getMessage());
                    }
                }

                active.setIsActive(false);
                active.setAttemptState(PaymentAttemptState.TERMINATED);
                active.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(active);
            }

            order.setStatus(MasterOrderStatus.CANCELLED);
            masterOrderRepository.save(order);

            // Giải phóng Redis inventory holds
            if (order.getBookingId() != null) {
                bookingRepository.findById(order.getBookingId()).ifPresent(booking -> {
                    List<InventoryLockItem> lockItems = toLockItems(booking);
                    inventoryLockPort.releaseHolds(booking.getId(), lockItems);
                    booking.setStatus(BookingStatus.CANCELLED);
                    bookingRepository.save(booking);
                });
            }
            log.info("Expired order {} successfully swept and inventory released.", orderId);
        });
    }

    private PrepareResult prepareAttempt(UUID customerId, UUID orderId, PaymentProvider provider, String idempotencyKey) {
        MasterOrderJpaEntity order = masterOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getCustomerId().equals(customerId)) {
            throw new UnauthorizedOrderAccessException(orderId, customerId);
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

        if (fixedDeadline == null) {
            // Lần đầu tiên kích hoạt PAYMENT: kiểm tra HOLD còn hạn và cố định deadline 300s
            if (order.getBookingId() != null) {
                BookingJpaEntity booking = bookingRepository.findById(order.getBookingId())
                        .orElseThrow(() -> new BookingNotFoundException(order.getBookingId()));

                if (booking.getHoldExpiresAt() != null && now.isAfter(booking.getHoldExpiresAt())) {
                    throw new BookingHoldExpiredException(order.getBookingId());
                }

                // Gia hạn Redis Hold thêm 420 giây (300s payment + 120s grace)
                List<InventoryLockItem> lockItems = toLockItems(booking);
                inventoryLockPort.extendHolds(booking.getId(), lockItems, Duration.ofSeconds(PAYMENT_WINDOW_SECONDS + REDIS_PAYMENT_GRACE_SECONDS));
            }

            fixedDeadline = now.plusSeconds(PAYMENT_WINDOW_SECONDS);
            order.setPaymentDeadline(fixedDeadline);
            masterOrderRepository.save(order);
        } else {
            // Các lần gọi sau: DEADLINE KHÔNG THAY ĐỔI
            if (now.isAfter(fixedDeadline)) {
                throw new InvalidOrderStateException("The payment deadline has expired.");
            }
        }

        // Kiểm tra active attempt hiện tại
        var activeOpt = paymentRepository.findActiveAttemptForUpdate(orderId);
        if (activeOpt.isPresent()) {
            PaymentJpaEntity active = activeOpt.get();
            if (active.getProvider() == provider && active.getAttemptState() == PaymentAttemptState.PENDING) {
                return new PrepareResult(active, fixedDeadline, true, active.getPayosOrderCode());
            }
            if (active.getProvider() != provider) {
                throw new InvalidOrderStateException("An active payment attempt exists for provider "
                        + active.getProvider() + ". Use switch-provider to change gateways.");
            }
        }

        // Kiểm tra in-flight attempt đang tạo
        var inFlight = paymentRepository.findInFlightAttempts(orderId, List.of(PaymentAttemptState.CREATING));
        if (!inFlight.isEmpty()) {
            PaymentJpaEntity pendingCreation = inFlight.get(0);
            if (pendingCreation.getCancelStatus() == PaymentCancelStatus.CANCEL_UNKNOWN) {
                // BUG-PAY-01 Recovery: Gateway timeout trước đó để lại attempt ở trạng thái CREATING + CANCEL_UNKNOWN
                PaymentGatewayPort adapter = gatewayRegistry.getAdapter(pendingCreation.getProvider());
                String queryRef = (pendingCreation.getProvider() == PaymentProvider.PAYOS && pendingCreation.getPayosOrderCode() != null)
                        ? String.valueOf(pendingCreation.getPayosOrderCode())
                        : pendingCreation.getProviderOrderId();
                boolean resolved = false;
                if (queryRef != null && !queryRef.isBlank()) {
                    try {
                        PaymentCaptureResult query = adapter.queryPayment(pendingCreation.getProvider(), queryRef, null);
                        if (query != null && (query.success() || "PENDING".equalsIgnoreCase(query.status()))) {
                            // Cổng đã tạo thành công giao dịch! Khôi phục và tái sử dụng attempt
                            pendingCreation.setAttemptState(PaymentAttemptState.PENDING);
                            pendingCreation.setIsActive(true);
                            pendingCreation.setCancelStatus(PaymentCancelStatus.NONE);
                            if (query.captureId() != null && pendingCreation.getPaymentUrl() == null) {
                                pendingCreation.setPaymentUrl(query.captureId());
                            }
                            paymentRepository.save(pendingCreation);
                            return new PrepareResult(pendingCreation, fixedDeadline, true, pendingCreation.getPayosOrderCode());
                        } else if (query != null && ("FAILED".equalsIgnoreCase(query.status()) || "CANCELLED".equalsIgnoreCase(query.status()))) {
                            pendingCreation.setAttemptState(PaymentAttemptState.TERMINATED);
                            pendingCreation.setCancelStatus(PaymentCancelStatus.CANCELED);
                            pendingCreation.setStatus(PaymentStatus.FAILED);
                            paymentRepository.save(pendingCreation);
                            resolved = true;
                        }
                    } catch (Exception qEx) {
                        log.warn("Recovery query for timed out attempt {} failed: {}", pendingCreation.getId(), qEx.getMessage());
                    }
                } else {
                    // Chưa có provider reference nghĩa là network timeout xảy ra trước khi request tới gateway
                    pendingCreation.setAttemptState(PaymentAttemptState.TERMINATED);
                    pendingCreation.setCancelStatus(PaymentCancelStatus.CANCELED);
                    pendingCreation.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(pendingCreation);
                    resolved = true;
                }
                if (!resolved) {
                    throw new InvalidOrderStateException("Previous payment request timed out and provider verification is pending. Please retry in a few moments.");
                }
            } else {
                throw new InvalidOrderStateException("A payment link is currently being generated. Please wait a moment.");
            }
        }

        int nextAttemptNumber = paymentRepository.findMaxAttemptNumberByMasterOrderId(orderId) + 1;
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
        payment.setPayosOrderCode(provider == PaymentProvider.PAYOS ? gatewayOrderCode : null);
        payment.setExpiresAt(fixedDeadline);

        payment = paymentRepository.save(payment);
        return new PrepareResult(payment, fixedDeadline, false, gatewayOrderCode);
    }

    private PaymentJpaEntity activateAttempt(UUID paymentId, PaymentIntentResult result, OffsetDateTime fixedDeadline) {
        PaymentJpaEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new InvalidOrderStateException("Payment record not found: " + paymentId));

        payment.setPaymentUrl(result.paymentUrl());
        payment.setQrCodeUrl(result.qrCodeUrl());
        payment.setProviderOrderId(result.providerOrderId());
        payment.setProviderAmount(result.providerAmount());
        payment.setProviderCurrency(result.providerCurrency());
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
        payment.setIsActive(true);

        return paymentRepository.save(payment);
    }

    private AttemptSwitchContext prepareSwitchContext(UUID customerId, UUID orderId, PaymentProvider targetProvider) {
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
            throw new InvalidOrderStateException("The payment deadline has expired.");
        }

        long remainingSeconds = Duration.between(OffsetDateTime.now(), deadline).toSeconds();
        if (remainingSeconds < 5) {
            throw new InvalidOrderStateException("Insufficient time remaining (" + remainingSeconds + "s) to switch payment provider.");
        }

        PaymentJpaEntity activePayment = paymentRepository.findActiveAttemptForUpdate(orderId).orElse(null);

        if (activePayment != null && activePayment.getProvider() == targetProvider) {
            return new AttemptSwitchContext(activePayment, deadline, true, false);
        }

        return new AttemptSwitchContext(activePayment, deadline, false, activePayment != null);
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
    private record AttemptSwitchContext(PaymentJpaEntity activePayment, OffsetDateTime deadline, boolean sameProviderActive, boolean hasActiveToCancel) {}
}

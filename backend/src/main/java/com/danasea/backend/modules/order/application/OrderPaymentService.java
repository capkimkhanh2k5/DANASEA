package com.danasea.backend.modules.order.application;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.booking.application.dtos.ConfirmBookingCommand;
import com.danasea.backend.modules.booking.application.usecases.ConfirmBookingUseCase;
import com.danasea.backend.modules.booking.domain.exceptions.BookingHoldExpiredException;
import com.danasea.backend.modules.booking.domain.exceptions.BookingNotFoundException;
import com.danasea.backend.modules.booking.domain.exceptions.InvalidBookingStateException;
import com.danasea.backend.modules.booking.domain.models.BookingStatus;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingRepository;
import com.danasea.backend.modules.order.application.dtos.CreatePaymentIntentCommand;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.application.services.WaiverAcceptanceGuard;
import com.danasea.backend.modules.order.application.usecases.CreatePaymentIntentUseCase;
import com.danasea.backend.modules.order.domain.events.PaymentSuccessEvent;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.InvalidWebhookException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.exceptions.RefundNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.WaiverAcceptanceRequiredException;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.MissingWaiverItem;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundEvaluationResult;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.ports.PaymentCaptureResult;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.ServiceWaiverLookupPort;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.presentation.dtos.AdminOrderSummaryResponse;
import com.danasea.backend.modules.order.presentation.dtos.OrderPageResponse;
import com.danasea.backend.modules.order.presentation.dtos.OrderResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentIntentResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentResponse;
import com.danasea.backend.modules.order.presentation.dtos.PaymentWebhookRequest;
import com.danasea.backend.modules.order.presentation.dtos.PaymentWebhookResponse;
import com.danasea.backend.modules.order.presentation.dtos.RefundDetailResponse;
import com.danasea.backend.modules.order.presentation.dtos.RefundResponse;
import com.danasea.backend.modules.order.presentation.dtos.RefundWebhookRequest;
import com.danasea.backend.modules.order.presentation.dtos.RefundWebhookResponse;
import com.danasea.backend.modules.order.presentation.dtos.SubOrderResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderPaymentService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");
    private CommissionPolicyPort configuredCommission;
    private com.danasea.backend.modules.booking.domain.ports.FlashSaleBookingPort flashSale;
    @Autowired
    public void setConfiguredCommission(CommissionPolicyPort configuredCommission) { this.configuredCommission=configuredCommission; }
    @Autowired
    public void setFlashSale(com.danasea.backend.modules.booking.domain.ports.FlashSaleBookingPort flashSale) { this.flashSale=flashSale; }

    private DisruptionDecisionService disruptionDecisions;

    @Autowired
    public void setDisruptionDecisions(DisruptionDecisionService disruptionDecisions) {
        this.disruptionDecisions = disruptionDecisions;
    }

    private ApplicationEventPublisher eventPublisher;

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private ServiceWaiverLookupPort serviceWaiverLookupPort;

    @Autowired
    public void setServiceWaiverLookupPort(ServiceWaiverLookupPort serviceWaiverLookupPort) {
        this.serviceWaiverLookupPort = serviceWaiverLookupPort;
    }

    private LocalizedContentSelector localizedContentSelector;

    @Autowired
    public void setLocalizedContentSelector(LocalizedContentSelector localizedContentSelector) {
        this.localizedContentSelector = localizedContentSelector;
    }

    private final JpaBookingRepository bookingRepository;
    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaPaymentRepository paymentRepository;
    private final JpaRefundRepository refundRepository;
    private final JpaServiceSlotRepository serviceSlotRepository;
    private final VendorInternalApi vendorInternalApi;
    private final RefundPolicyEngine refundPolicyEngine;
    private final PaymentWebhookSigner webhookSigner;
    private final ConfirmBookingUseCase confirmBookingUseCase;
    private final ObjectMapper objectMapper;
    private final CreatePaymentIntentUseCase createPaymentIntentUseCase;
    private final RefundProcessingService refundProcessingService;
    private final PaymentGatewayPort paymentGatewayPort;
    private final TransactionTemplate gatewayTransaction;
    private final AuditLogInternalApi auditLogInternalApi;

    @Autowired
    public OrderPaymentService(
            JpaBookingRepository bookingRepository,
            JpaMasterOrderRepository masterOrderRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaPaymentRepository paymentRepository,
            JpaRefundRepository refundRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            VendorInternalApi vendorInternalApi,
            RefundPolicyEngine refundPolicyEngine,
            PaymentWebhookSigner webhookSigner,
            ConfirmBookingUseCase confirmBookingUseCase,
            ObjectMapper objectMapper,
            CreatePaymentIntentUseCase createPaymentIntentUseCase,
            @Autowired(required = false) RefundProcessingService refundProcessingService,
            @Autowired(required = false) PaymentGatewayPort paymentGatewayPort,
            PlatformTransactionManager transactionManager,
            @Autowired(required = false) AuditLogInternalApi auditLogInternalApi) {
        this.bookingRepository = bookingRepository;
        this.masterOrderRepository = masterOrderRepository;
        this.subOrderRepository = subOrderRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.serviceSlotRepository = serviceSlotRepository;
        this.vendorInternalApi = vendorInternalApi;
        this.refundPolicyEngine = refundPolicyEngine;
        this.webhookSigner = webhookSigner;
        this.confirmBookingUseCase = confirmBookingUseCase;
        this.objectMapper = objectMapper;
        this.createPaymentIntentUseCase = createPaymentIntentUseCase;
        this.refundProcessingService = refundProcessingService;
        this.paymentGatewayPort = paymentGatewayPort;
        this.auditLogInternalApi = auditLogInternalApi;
        this.gatewayTransaction = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        if (gatewayTransaction != null) {
            gatewayTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
    }

    public OrderPaymentService(
            JpaBookingRepository bookingRepository,
            JpaMasterOrderRepository masterOrderRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaPaymentRepository paymentRepository,
            JpaRefundRepository refundRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            VendorInternalApi vendorInternalApi,
            RefundPolicyEngine refundPolicyEngine,
            PaymentWebhookSigner webhookSigner,
            ConfirmBookingUseCase confirmBookingUseCase,
            ObjectMapper objectMapper,
            CreatePaymentIntentUseCase createPaymentIntentUseCase,
            RefundProcessingService refundProcessingService,
            PaymentGatewayPort paymentGatewayPort,
            PlatformTransactionManager transactionManager) {
        this(bookingRepository, masterOrderRepository, subOrderRepository, paymentRepository, refundRepository,
                serviceSlotRepository, vendorInternalApi, refundPolicyEngine, webhookSigner, confirmBookingUseCase,
                objectMapper, createPaymentIntentUseCase, refundProcessingService, paymentGatewayPort, transactionManager, null);
    }

    public OrderPaymentService(
            JpaBookingRepository bookingRepository, JpaMasterOrderRepository masterOrderRepository,
            JpaSubOrderRepository subOrderRepository, JpaPaymentRepository paymentRepository,
            JpaRefundRepository refundRepository, JpaServiceSlotRepository serviceSlotRepository,
            VendorInternalApi vendorInternalApi, RefundPolicyEngine refundPolicyEngine,
            PaymentWebhookSigner webhookSigner, ConfirmBookingUseCase confirmBookingUseCase,
            ObjectMapper objectMapper, CreatePaymentIntentUseCase createPaymentIntentUseCase,
            RefundProcessingService refundProcessingService, PaymentGatewayPort paymentGatewayPort) {
        this(bookingRepository, masterOrderRepository, subOrderRepository, paymentRepository, refundRepository,
                serviceSlotRepository, vendorInternalApi, refundPolicyEngine, webhookSigner, confirmBookingUseCase,
                objectMapper, createPaymentIntentUseCase, refundProcessingService, paymentGatewayPort, null);
    }

    public OrderPaymentService(
            JpaBookingRepository bookingRepository,
            JpaMasterOrderRepository masterOrderRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaPaymentRepository paymentRepository,
            JpaRefundRepository refundRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            VendorInternalApi vendorInternalApi,
            RefundPolicyEngine refundPolicyEngine,
            PaymentWebhookSigner webhookSigner,
            ConfirmBookingUseCase confirmBookingUseCase,
            ObjectMapper objectMapper,
            CreatePaymentIntentUseCase createPaymentIntentUseCase,
            RefundProcessingService refundProcessingService) {
        this(bookingRepository, masterOrderRepository, subOrderRepository, paymentRepository, refundRepository,
                serviceSlotRepository, vendorInternalApi, refundPolicyEngine, webhookSigner, confirmBookingUseCase,
                objectMapper, createPaymentIntentUseCase, refundProcessingService, null);
    }

    public OrderPaymentService(
            JpaBookingRepository bookingRepository,
            JpaMasterOrderRepository masterOrderRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaPaymentRepository paymentRepository,
            JpaRefundRepository refundRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            VendorInternalApi vendorInternalApi,
            RefundPolicyEngine refundPolicyEngine,
            PaymentWebhookSigner webhookSigner,
            ConfirmBookingUseCase confirmBookingUseCase,
            ObjectMapper objectMapper,
            CreatePaymentIntentUseCase createPaymentIntentUseCase) {
        this(bookingRepository, masterOrderRepository, subOrderRepository, paymentRepository, refundRepository,
                serviceSlotRepository, vendorInternalApi, refundPolicyEngine, webhookSigner, confirmBookingUseCase,
                objectMapper, createPaymentIntentUseCase, null, null);
    }

    @Transactional
    public OrderResponse createOrder(UUID customerId, UUID bookingId, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        if (customerId == null || bookingId == null) {
            throw new IllegalArgumentException("Customer ID and booking ID are required.");
        }

        BookingJpaEntity booking = bookingRepository.findByIdWithItemsForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        assertOwner(booking.getCustomerId(), customerId);

        var existingForBooking = masterOrderRepository.findByBookingId(bookingId);
        if (existingForBooking.isPresent()) {
            return toOrderResponse(existingForBooking.get());
        }
        var existingForKey = masterOrderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existingForKey.isPresent()) {
            if (!bookingId.equals(existingForKey.get().getBookingId())) {
                throw new InvalidOrderStateException("The idempotency key was already used for another booking.");
            }
            return toOrderResponse(existingForKey.get());
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (!BookingStatus.HOLD.equals(booking.getStatus())) {
            throw new InvalidBookingStateException("Only a booking in HOLD status can be converted to an order.");
        }
        if (booking.getHoldExpiresAt() != null && now.isAfter(booking.getHoldExpiresAt())) {
            throw new BookingHoldExpiredException(bookingId);
        }
        if (booking.getItems() == null || booking.getItems().isEmpty()) {
            throw new InvalidBookingStateException("A booking must contain at least one item.");
        }

        MasterOrderJpaEntity order = new MasterOrderJpaEntity();
        order.setBookingId(bookingId);
        order.setCustomerId(customerId);
        order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(booking.getTotalAmount());
        order.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order.setIdempotencyKey(idempotencyKey);
        order = masterOrderRepository.save(order);

        List<SubOrderJpaEntity> subOrders = new ArrayList<>();
        for (BookingItemJpaEntity item : booking.getItems()) {
            var promotion=flashSale!=null?flashSale.snapshot(item.getId()).orElse(null):null;
            BigDecimal rate=promotion!=null?promotion.commissionRate():configuredCommission!=null?configuredCommission.getCommissionRate(item.getVendorId()):null;
            if(rate==null) throw new com.danasea.backend.modules.flashsale.domain.FlashSaleException("COMMISSION_POLICY_MISSING");
            BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal commission = subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);

            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setBookingItemId(item.getId());
            subOrder.setMasterOrderId(order.getId());
            subOrder.setVendorId(item.getVendorId());
            subOrder.setServiceId(item.getServiceId());
            subOrder.setSlotId(item.getSlotId());
            subOrder.setQuantity(item.getQuantity());
            subOrder.setUnitPrice(item.getPrice());
            subOrder.setSubtotalAmount(subtotal);
            subOrder.setCommissionRate(rate);
            subOrder.setCommissionAmount(commission);
            subOrder.setVendorPayoutAmount(subtotal.subtract(commission));
            if(promotion!=null) {
                subOrder.setUnitPrice(promotion.originalUnitPrice());
                subOrder.setSubtotalAmount(promotion.originalAmount());
                subOrder.setCommissionBasisAmount(promotion.originalAmount());
                subOrder.setCommissionAmount(promotion.commissionAmount());
                subOrder.setVendorPayoutAmount(promotion.vendorReceivable());
                subOrder.setDiscountAmount(promotion.subsidyAmount());
                subOrder.setPlatformDiscountAmount(promotion.subsidyAmount());
                subOrder.setVendorDiscountAmount(BigDecimal.ZERO);
                subOrder.setFinalAmount(promotion.customerPayable());
            }
            subOrder.setStatus(SubOrderStatus.PENDING);
            subOrder.setWaiverAccepted(false);

            if (serviceWaiverLookupPort != null) {
                serviceWaiverLookupPort.findWaiverSnapshot(item.getServiceId()).ifPresent(snapshot -> {
                    subOrder.setWaiverRequired(snapshot.waiverRequired());
                    subOrder.setWaiverVersion(snapshot.waiverVersion());
                    subOrder.setWaiverContent(snapshot.waiverContent());
                    subOrder.setWaiverContentEn(snapshot.waiverContentEn());
                });
            }

            subOrders.add(subOrder);
        }
        subOrderRepository.saveAll(subOrders);
        if(flashSale!=null && flashSale.hasAllocation(bookingId)) {
            order.setDiscountAmount(subOrders.stream().map(SubOrderJpaEntity::getPlatformDiscountAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
            masterOrderRepository.save(order);
        }
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        bookingRepository.save(booking);
        return toOrderResponse(order, subOrders);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID userId, UUID orderId, boolean admin) {
        MasterOrderJpaEntity order = findOrder(orderId);
        if (!admin) {
            assertOwner(order.getCustomerId(), userId);
        }
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderPageResponse<OrderResponse> getCustomerOrders(UUID customerId, int page, int size) {
        validatePage(page, size);
        Page<MasterOrderJpaEntity> result = masterOrderRepository.findByCustomerId(
                customerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new OrderPageResponse<>(result.getContent().stream().map(this::toOrderResponse).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrderPageResponse<SubOrderResponse> getVendorOrders(UUID userId, int page, int size) {
        validatePage(page, size);
        UUID vendorId = vendorInternalApi.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException("Vendor profile not found."))
                .getId();
        Page<SubOrderJpaEntity> result = subOrderRepository.findByVendorId(
                vendorId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new OrderPageResponse<>(result.getContent().stream().map(this::toSubOrderResponse).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public SubOrderResponse rejectVendorBooking(
            UUID userId,
            UUID bookingItemId,
            String reason,
            String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new IllegalArgumentException("A rejection reason of at most 500 characters is required.");
        }
        UUID vendorId = vendorInternalApi.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException("Vendor profile not found."))
                .getId();
        SubOrderJpaEntity candidate = subOrderRepository.findByBookingItemId(bookingItemId)
                .orElseThrow(() -> new OrderNotFoundException("Vendor booking item not found: " + bookingItemId));
        if (!vendorId.equals(candidate.getVendorId())) {
            throw new AccessDeniedException("Vendor does not own this booking item.");
        }
        disruptionDecisions.record(candidate.getId(), RefundReason.VENDOR_FAULT, reason.trim(),
                "vendor-reject-" + idempotencyKey, userId);
        return toSubOrderResponse(subOrderRepository.findById(candidate.getId()).orElseThrow());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentIntentResponse createPaymentIntent(
            UUID customerId,
            UUID orderId,
            PaymentProvider provider,
            String idempotencyKey) {
        var result = createPaymentIntentUseCase.execute(
                new CreatePaymentIntentCommand(customerId, orderId, provider, idempotencyKey));
        return new PaymentIntentResponse(result.paymentId(), result.orderId(), result.provider(),
                result.amount(), PaymentStatus.PENDING, result.paymentUrl(), result.expiresAt());
    }

    @Transactional
    public PaymentWebhookResponse processWebhook(
            PaymentProvider provider,
            String rawPayload,
            String signature) {
        if (!webhookSigner.isValid(rawPayload, signature)) {
            throw new InvalidWebhookException("Invalid webhook signature.");
        }
        PaymentWebhookRequest request = parseWebhook(rawPayload);
        validateWebhookRequest(request);

        var alreadyProcessed = paymentRepository.findByProviderAndWebhookEventId(provider, request.eventId());
        if (alreadyProcessed.isPresent()) {
            PaymentJpaEntity payment = alreadyProcessed.get();
            return new PaymentWebhookResponse(request.eventId(), payment.getId(), payment.getStatus(), true);
        }

        PaymentJpaEntity payment = paymentRepository.findByIdForUpdate(request.paymentId())
                .orElseThrow(() -> new InvalidWebhookException("Unknown payment."));
        if (payment.getProvider() != provider) {
            throw new InvalidWebhookException("The webhook provider does not match the payment provider.");
        }
        if (payment.getAmount() == null || payment.getAmount().compareTo(request.amount()) != 0) {
            throw new InvalidWebhookException("The webhook amount does not match the payment amount.");
        }
        if (!PaymentStatus.PENDING.equals(payment.getStatus())) {
            throw new InvalidOrderStateException("The payment has already reached a terminal state.");
        }

        payment.setWebhookEventId(request.eventId());
        payment.setProviderTransactionId(request.providerTransactionId());
        payment.setRawWebhookPayload(rawPayload);
        if (request.status() != PaymentStatus.SUCCESS) {
            payment.setStatus(request.status());
        }
        paymentRepository.save(payment);

        if (PaymentStatus.SUCCESS.equals(request.status())) {
            MasterOrderJpaEntity order = masterOrderRepository.findByIdForUpdate(payment.getMasterOrderId())
                    .orElseThrow(() -> new InvalidWebhookException("Payment order not found."));
            if (!MasterOrderStatus.PENDING_PAYMENT.equals(order.getStatus()) && !MasterOrderStatus.PAID.equals(order.getStatus())) {
                throw new InvalidOrderStateException("The order is not awaiting payment.");
            }
            if (order.getTotalAmount().compareTo(payment.getAmount()) != 0) {
                throw new InvalidWebhookException("The paid amount does not match the order total.");
            }

            applyPaymentSuccess(payment, order, request.providerTransactionId(), request.eventId(), rawPayload);
        }

        return new PaymentWebhookResponse(request.eventId(), payment.getId(), payment.getStatus(), false);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponse capturePayPalOrder(
            UUID customerId, UUID paymentId, String paypalOrderId, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        if (customerId == null || paymentId == null || paypalOrderId == null || paypalOrderId.isBlank()) {
            throw new IllegalArgumentException("Customer ID, payment ID, and PayPal order ID are required.");
        }
        if (gatewayTransaction == null || paymentGatewayPort == null) {
            throw new IllegalStateException("Transactional payment gateway is not configured.");
        }
        boolean fresh = Boolean.TRUE.equals(gatewayTransaction.execute(tx -> {
            PaymentJpaEntity payment = lockPayment(paymentId);
            MasterOrderJpaEntity order = lockPaymentOrder(payment);
            assertOwner(order.getCustomerId(), customerId);
            validatePayPalIntent(payment, paypalOrderId);
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                return false;
            }
            requirePendingPayment(payment, order);
            validateWaiverAcceptance(order);
            if (payment.getCaptureRequestId() != null || payment.getCaptureRequestedAt() != null
                    || (payment.getLastError() != null && !payment.getLastError().startsWith("GATEWAY_QUERY_AWAITING_VERIFICATION"))) {
                return false;
            }
            if ((payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(OffsetDateTime.now()))
                    || (order.getPaymentDeadline() != null && order.getPaymentDeadline().isBefore(OffsetDateTime.now()))) {
                throw new InvalidOrderStateException("The payment intent has expired.");
            }
            payment.setCaptureRequestId(UUID.randomUUID().toString());
            payment.setCaptureRequestedAt(OffsetDateTime.now());
            payment.setLastError("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
            paymentRepository.saveAndFlush(payment);
            return true;
        }));
        CaptureOutcome outcome = gatewayTransaction.execute(tx -> {
            PaymentJpaEntity payment = lockPayment(paymentId);
            MasterOrderJpaEntity order = lockPaymentOrder(payment);
            assertOwner(order.getCustomerId(), customerId);
            validatePayPalIntent(payment, paypalOrderId);
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                return new CaptureOutcome(toPaymentResponse(payment), null);
            }
            requirePendingPayment(payment, order);
            validateWaiverAcceptance(order);
            PaymentCaptureResult result;
            try {
                if (!fresh) {
                    result = paymentGatewayPort.queryCapture(PaymentProvider.PAYPAL, paypalOrderId);
                    if (result == null || !"COMPLETED".equalsIgnoreCase(result.status())) {
                        // Ambiguous outcomes are queried again; they never cause another capture command.
                        payment.setLastError("GATEWAY_TIMEOUT_AWAITING_VERIFICATION");
                        paymentRepository.save(payment);
                        return new CaptureOutcome(toPaymentResponse(payment), null);
                    }
                } else {
                    result = paymentGatewayPort.captureOrder(PaymentProvider.PAYPAL, paypalOrderId, payment.getCaptureRequestId());
                }
                validateCaptureResult(payment, result);
            } catch (PaymentGatewayException | IllegalArgumentException ex) {
                payment.setLastError("GATEWAY_TIMEOUT_AWAITING_VERIFICATION: " + ex.getMessage());
                paymentRepository.save(payment);
                return new CaptureOutcome(toPaymentResponse(payment), ex.getMessage());
            }
            applyPaymentSuccess(payment, order, result.captureId(), null, null);
            return new CaptureOutcome(toPaymentResponse(payment), null);
        });
        if (outcome.error() != null) {
            throw new PaymentGatewayException(outcome.error());
        }
        return outcome.response();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reconcilePayPalCapture(UUID paymentId) {
        reconcilePayment(paymentId);
    }

    private record CaptureOutcome(PaymentResponse response, String error) { }

    private PaymentJpaEntity lockPayment(UUID paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new OrderNotFoundException("Payment not found: " + paymentId));
    }

    private MasterOrderJpaEntity lockPaymentOrder(PaymentJpaEntity payment) {
        return masterOrderRepository.findByIdForUpdate(payment.getMasterOrderId())
                .orElseThrow(() -> new OrderNotFoundException("Payment order not found."));
    }

    private void validatePayPalIntent(PaymentJpaEntity payment, String providerOrderId) {
        if (payment.getProvider() != PaymentProvider.PAYPAL || payment.getProviderOrderId() == null
                || !payment.getProviderOrderId().equals(providerOrderId)) {
            throw new InvalidOrderStateException("PayPal order does not match the persisted payment intent.");
        }
        if (payment.getProviderAmount() == null || payment.getProviderAmount().signum() <= 0
                || !"USD".equals(payment.getProviderCurrency())) {
            throw new InvalidOrderStateException("Persisted gateway amount and currency are required.");
        }
    }

    private void validateCaptureResult(PaymentJpaEntity payment, PaymentCaptureResult result) {
        if (result == null || !result.success() || !"COMPLETED".equalsIgnoreCase(result.status())
                || result.captureId() == null || result.captureId().isBlank()
                || result.amount() == null || payment.getProviderAmount() == null
                || payment.getProviderAmount().signum() <= 0
                || payment.getProviderAmount().compareTo(result.amount()) != 0
                || payment.getProviderCurrency() == null || !payment.getProviderCurrency().equals(result.currency())) {
            throw new PaymentGatewayException("Capture ID, amount, currency, or status does not match the payment intent.");
        }
    }

    private void requirePendingPayment(PaymentJpaEntity payment, MasterOrderJpaEntity order) {
        if (payment.getStatus() != PaymentStatus.PENDING || order.getStatus() != MasterOrderStatus.PENDING_PAYMENT
                || payment.getAmount() == null || order.getTotalAmount().compareTo(payment.getAmount()) != 0) {
            throw new InvalidOrderStateException("Payment and order must be pending with matching amounts.");
        }
    }

    @Transactional
    public PaymentWebhookResponse processPayPalWebhook(
            String payload, String transmissionId, String transmissionTime,
            String signature, String certUrl, String authAlgo) {
        verifyPayPalWebhook(payload, transmissionId, transmissionTime, signature, certUrl, authAlgo);
        JsonNode root = parsePayPalEvent(payload);
        String eventType = root.path("event_type").asText();
        String eventId = root.path("id").asText();
        if (eventType.startsWith("PAYMENT.CAPTURE.REFUND") || eventType.startsWith("PAYMENT.REFUND.")) {
            RefundWebhookResponse result = dispatchPayPalRefund(root);
            RefundJpaEntity refund = refundRepository.findById(result.refundId()).orElseThrow();
            PaymentJpaEntity payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();
            return new PaymentWebhookResponse(eventId, payment.getId(), payment.getStatus(), result.alreadyProcessed());
        }
        if (!"PAYMENT.CAPTURE.COMPLETED".equals(eventType)) {
            return new PaymentWebhookResponse(eventId, null, PaymentStatus.PENDING, true);
        }
        JsonNode resource = root.path("resource");
        String captureId = resource.path("id").asText();
        String customId = resource.path("custom_id").asText();
        String providerOrderId = resource.path("supplementary_data").path("related_ids").path("order_id").asText();
        PaymentJpaEntity payment;
        if (!customId.isBlank()) {
            try {
                payment = paymentRepository.findByIdForUpdate(UUID.fromString(customId)).orElse(null);
            } catch (IllegalArgumentException ex) {
                throw new InvalidWebhookException("Invalid PayPal custom payment ID.");
            }
        } else {
            payment = paymentRepository.findByProviderAndProviderOrderIdForUpdate(PaymentProvider.PAYPAL, providerOrderId).orElse(null);
        }
        if (payment == null) {
            throw new InvalidWebhookException("PayPal payment intent was not found.");
        }
        try {
            validatePayPalIntent(payment, providerOrderId);
            BigDecimal amount = new BigDecimal(resource.path("amount").path("value").asText());
            validateCaptureResult(payment, new PaymentCaptureResult(
                    "COMPLETED".equals(resource.path("status").asText()), captureId, amount,
                    resource.path("amount").path("currency_code").asText(), resource.path("status").asText(), null));
        } catch (IllegalArgumentException | InvalidOrderStateException | PaymentGatewayException ex) {
            throw new InvalidWebhookException(ex.getMessage());
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            if (!captureId.equals(payment.getProviderTransactionId())) {
                throw new InvalidWebhookException("Capture ID does not match the completed payment.");
            }
            return new PaymentWebhookResponse(eventId, payment.getId(), payment.getStatus(), true);
        }
        MasterOrderJpaEntity order = lockPaymentOrder(payment);
        applyPaymentSuccess(payment, order, captureId, eventId, payload);
        return new PaymentWebhookResponse(eventId, payment.getId(), payment.getStatus(), false);
    }

    @Transactional
    public RefundWebhookResponse processPayPalRefundWebhook(
            String payload, String transmissionId, String transmissionTime,
            String signature, String certUrl, String authAlgo) {
        verifyPayPalWebhook(payload, transmissionId, transmissionTime, signature, certUrl, authAlgo);
        return dispatchPayPalRefund(parsePayPalEvent(payload));
    }

    private RefundWebhookResponse dispatchPayPalRefund(JsonNode root) {
        String eventType = root.path("event_type").asText();
        if (!eventType.startsWith("PAYMENT.CAPTURE.REFUND") && !eventType.startsWith("PAYMENT.REFUND.")) {
            throw new InvalidWebhookException("A PayPal refund event is required.");
        }
        if (refundProcessingService == null) {
            throw new IllegalStateException("Refund processor is not configured.");
        }
        JsonNode resource = root.path("resource");
        return refundProcessingService.processPayPalNotification(root.path("id").asText(),
                resource.path("id").asText(), resource.path("invoice_id").asText());
    }

    private JsonNode parsePayPalEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || root.path("id").asText().isBlank() || root.path("event_type").asText().isBlank()) {
                throw new InvalidWebhookException("PayPal event ID and type are required.");
            }
            return root;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new InvalidWebhookException("Malformed PayPal webhook payload.");
        }
    }

    private void verifyPayPalWebhook(String payload, String transmissionId, String transmissionTime,
            String signature, String certUrl, String authAlgo) {
        if (transmissionId == null || transmissionId.isBlank() || transmissionTime == null || transmissionTime.isBlank()
                || signature == null || signature.isBlank() || certUrl == null || certUrl.isBlank()
                || authAlgo == null || authAlgo.isBlank()) {
            throw new InvalidWebhookException("All PayPal webhook verification headers are required.");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("paypal-transmission-id", transmissionId);
        headers.put("paypal-transmission-time", transmissionTime);
        headers.put("paypal-cert-url", certUrl);
        headers.put("paypal-auth-algo", authAlgo);
        headers.put("rawPayload", payload);
        if (paymentGatewayPort == null || !paymentGatewayPort.verifyWebhookSignature(headers, signature)) {
            throw new InvalidWebhookException("Invalid PayPal webhook signature.");
        }
    }

    @Transactional
    public Map<String, String> processVNPayIpn(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return Map.of("RspCode", "99", "Message", "Unknown error");
        }

        String secureHash = params.get("vnp_SecureHash");
        if (paymentGatewayPort == null || !paymentGatewayPort.verifyWebhookSignature(params, secureHash)) {
            return Map.of("RspCode", "97", "Message", "Invalid Checksum");
        }

        String txnRef = params.get("vnp_TxnRef");
        UUID paymentId;
        try {
            paymentId = UUID.fromString(txnRef);
        } catch (Exception e) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        PaymentJpaEntity payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || payment.getProvider() != PaymentProvider.VNPAY) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        MasterOrderJpaEntity masterOrder = masterOrderRepository.findByIdForUpdate(payment.getMasterOrderId()).orElse(null);
        if (masterOrder == null) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        try {
            BigDecimal receivedAmount = new BigDecimal(params.get("vnp_Amount"));
            if (payment.getAmount() == null || payment.getAmount().multiply(BigDecimal.valueOf(100)).compareTo(receivedAmount) != 0
                    || masterOrder.getTotalAmount().compareTo(payment.getAmount()) != 0) {
                return Map.of("RspCode", "04", "Message", "Invalid Amount");
            }
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Map.of("RspCode", "04", "Message", "Invalid Amount");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        }

        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");

        if ("00".equals(responseCode) && "00".equals(params.get("vnp_TransactionStatus"))) {
            if (transactionNo == null || transactionNo.isBlank() || masterOrder.getStatus() != MasterOrderStatus.PENDING_PAYMENT) {
                return Map.of("RspCode", "99", "Message", "Payment requires reconciliation");
            }
            applyPaymentSuccess(payment, masterOrder, transactionNo, transactionNo, params.toString());
            return Map.of("RspCode", "00", "Message", "Confirm Success");
        } else {
            payment.setProviderTransactionId(transactionNo);
            payment.setRawWebhookPayload(params.toString());
            if ("02".equals(params.get("vnp_TransactionStatus"))) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setLastError("GATEWAY_PAYMENT_FAILED: " + responseCode);
                payment.setReconciliationNextAttemptAt(null);
                paymentRepository.save(payment);
                return Map.of("RspCode", "00", "Message", "Confirm Success");
            }
            payment.setLastError("GATEWAY_QUERY_AWAITING_VERIFICATION: VNPay transaction status "
                    + params.get("vnp_TransactionStatus"));
            paymentRepository.save(payment);
            return Map.of("RspCode", "99", "Message", "Payment requires reconciliation");
        }
    }

    private void applyPaymentSuccess(
            PaymentJpaEntity payment,
            MasterOrderJpaEntity order,
            String providerTransactionId,
            String webhookEventId,
            String rawPayload) {

        requirePendingPayment(payment, order);
        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            throw new InvalidWebhookException("Provider transaction ID is required.");
        }
        payment.setLastError(null);
        payment.setStatus(PaymentStatus.SUCCESS);
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(OffsetDateTime.now());
        }
        payment.setReconciliationNextAttemptAt(null);
        if (providerTransactionId != null && !providerTransactionId.isBlank()) {
            payment.setProviderTransactionId(providerTransactionId);
        }
        if (webhookEventId != null && !webhookEventId.isBlank()) {
            payment.setWebhookEventId(webhookEventId);
        }
        if (rawPayload != null && !rawPayload.isBlank()) {
            payment.setRawWebhookPayload(rawPayload);
        }
        paymentRepository.save(payment);

        if (MasterOrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
            order.setStatus(MasterOrderStatus.PAID);
            order.setPaymentStatus(PaymentOrderStatus.PAID);
            masterOrderRepository.save(order);

            List<SubOrderJpaEntity> subOrders = subOrderRepository.findByMasterOrderId(order.getId());
            subOrders.forEach(subOrder -> subOrder.setStatus(SubOrderStatus.CONFIRMED));
            subOrderRepository.saveAll(subOrders);

            confirmBookingUseCase.execute(new ConfirmBookingCommand(order.getBookingId(), order.getCustomerId()));
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new PaymentSuccessEvent(order.getId(), order.getCustomerId(), order.getBookingId()));
            }
        }
    }

    @Transactional
    public List<RefundResponse> requestRefund(
            UUID customerId,
            UUID orderOrSubOrderId,
            RefundReason requestedReason,
            String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        RefundReason reason = requestedReason == null ? RefundReason.CUSTOMER_REQUEST : requestedReason;
        if (reason != RefundReason.CUSTOMER_REQUEST && reason != RefundReason.CUSTOMER_CANCEL) {
            throw new IllegalArgumentException("Customers may only request a customer cancellation refund.");
        }

        MasterOrderJpaEntity order;
        List<SubOrderJpaEntity> subOrders;
        var master = masterOrderRepository.findById(orderOrSubOrderId);
        if (master.isPresent()) {
            order = master.get();
            subOrders = subOrderRepository.findByMasterOrderId(order.getId());
        } else {
            SubOrderJpaEntity subOrder = subOrderRepository.findById(orderOrSubOrderId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderOrSubOrderId));
            order = findOrder(subOrder.getMasterOrderId());
            subOrders = List.of(subOrder);
        }
        assertOwner(order.getCustomerId(), customerId);
        if (!MasterOrderStatus.PAID.equals(order.getStatus())
                && !MasterOrderStatus.PARTIALLY_COMPLETED.equals(order.getStatus())
                && !MasterOrderStatus.COMPLETED.equals(order.getStatus())) {
            throw new InvalidOrderStateException("Refunds can only be requested for a paid order.");
        }

        List<RefundResponse> responses = new ArrayList<>();
        for (SubOrderJpaEntity subOrder : subOrders) {
            var existing = refundRepository.findBySubOrderIdAndIdempotencyKey(subOrder.getId(), idempotencyKey);
            if (existing.isPresent()) {
                responses.add(toRefundResponse(existing.get()));
                continue;
            }
            if (!isRefundableStatus(subOrder.getStatus())) {
                throw new InvalidOrderStateException("Sub-order " + subOrder.getId() + " is not refundable.");
            }

            LocalDateTime departure = serviceSlotRepository.findById(subOrder.getSlotId())
                    .filter(slot -> slot.getDate() != null && slot.getStartTime() != null)
                    .map(slot -> LocalDateTime.of(slot.getDate(), slot.getStartTime()))
                    .orElse(null);
            RefundEvaluationResult evaluation = refundPolicyEngine.evaluate(
                    reason, departure, LocalDateTime.now(), subOrder.getFinalAmount());
            if (evaluation.refundAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidOrderStateException("The cancellation policy does not allow a refund for sub-order "
                        + subOrder.getId() + ".");
            }

            RefundJpaEntity refund = new RefundJpaEntity();
            refund.setSubOrderId(subOrder.getId());
            refund.setAmount(evaluation.refundAmount());
            refund.setRefundPercentage(evaluation.refundPercentage());
            refund.setReason(reason);
            refund.setStatus(RefundStatus.PENDING);
            refund.setRequestedBy(customerId);
            refund.setIdempotencyKey(idempotencyKey);
            responses.add(toRefundResponse(refundRepository.save(refund)));
        }
        return responses;
    }

    @Transactional
    public RefundWebhookResponse processRefundWebhook(
            PaymentProvider provider,
            String rawPayload,
            String signature) {
        if (!webhookSigner.isValid(rawPayload, signature)) {
            throw new InvalidWebhookException("Invalid webhook signature.");
        }
        RefundWebhookRequest request = parseRefundWebhook(rawPayload);
        validateRefundWebhookRequest(request);

        var processedEvent = refundRepository.findByProviderAndWebhookEventId(provider, request.eventId());
        if (processedEvent.isPresent()) {
            RefundJpaEntity refund = processedEvent.get();
            return new RefundWebhookResponse(request.eventId(), refund.getId(), refund.getStatus(), true);
        }

        RefundJpaEntity refund = refundRepository.findByIdForUpdate(request.refundId())
                .orElseThrow(() -> new InvalidWebhookException("Unknown refund."));
        if (!RefundStatus.PENDING.equals(refund.getStatus())) {
            throw new InvalidOrderStateException("The refund has already reached a terminal state.");
        }
        if (refund.getAmount() == null || refund.getAmount().compareTo(request.amount()) != 0) {
            throw new InvalidWebhookException("The webhook amount does not match the refund amount.");
        }

        SubOrderJpaEntity subOrder = subOrderRepository.findById(refund.getSubOrderId())
                .orElseThrow(() -> new InvalidWebhookException("Refund sub-order not found."));
        PaymentJpaEntity originalPayment = paymentRepository
                .findByMasterOrderIdOrderByCreatedAtDesc(subOrder.getMasterOrderId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS
                        || payment.getStatus() == PaymentStatus.REFUNDED)
                .findFirst()
                .orElseThrow(() -> new InvalidWebhookException("No successful original payment was found."));
        if (originalPayment.getProvider() != provider) {
            throw new InvalidWebhookException("Refund provider must match the original payment provider.");
        }

        refund.setProvider(provider);
        refund.setWebhookEventId(request.eventId());
        refund.setProviderRefundId(request.providerRefundId());
        refund.setStatus(request.status());
        refund.setProcessedAt(RefundStatus.PROCESSED.equals(request.status()) ? OffsetDateTime.now() : null);
        refundRepository.save(refund);

        if (RefundStatus.PROCESSED.equals(request.status())) {
            if (subOrder.getCancellationReason() == null && refund.getReason() != RefundReason.COMPENSATION
                    && refund.getReason() != RefundReason.DISPUTE) {
                subOrder.setCancellationReason(refund.getReason());
            }
            subOrder.setStatus(refund.getRefundPercentage().compareTo(BigDecimal.valueOf(100)) == 0
                    ? SubOrderStatus.REFUNDED
                    : SubOrderStatus.PARTIALLY_REFUNDED);
            subOrderRepository.save(subOrder);

            List<UUID> subOrderIds = subOrderRepository.findByMasterOrderId(subOrder.getMasterOrderId()).stream()
                    .map(SubOrderJpaEntity::getId)
                    .toList();
            BigDecimal processedTotal = refundRepository
                    .findBySubOrderIdInAndStatus(subOrderIds, RefundStatus.PROCESSED).stream()
                    .map(RefundJpaEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (processedTotal.compareTo(originalPayment.getAmount()) >= 0) {
                originalPayment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(originalPayment);
                MasterOrderJpaEntity refundedOrder = lockPaymentOrder(originalPayment);
                refundedOrder.setPaymentStatus(PaymentOrderStatus.REFUNDED);
                masterOrderRepository.save(refundedOrder);
            }
        }

        return new RefundWebhookResponse(request.eventId(), refund.getId(), refund.getStatus(), false);
    }

    private PaymentWebhookRequest parseWebhook(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, PaymentWebhookRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new InvalidWebhookException("Malformed webhook payload.");
        }
    }

    private RefundWebhookRequest parseRefundWebhook(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, RefundWebhookRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new InvalidWebhookException("Malformed refund webhook payload.");
        }
    }

    private void validateRefundWebhookRequest(RefundWebhookRequest request) {
        if (request == null || request.eventId() == null || request.eventId().isBlank()
                || request.eventId().length() > 150 || request.refundId() == null
                || request.providerRefundId() == null || request.providerRefundId().isBlank()
                || request.providerRefundId().length() > 150 || request.status() == null
                || request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidWebhookException("Missing or invalid refund webhook fields.");
        }
        if (request.status() != RefundStatus.PROCESSED && request.status() != RefundStatus.FAILED) {
            throw new InvalidWebhookException("Refund webhook status must be PROCESSED or FAILED.");
        }
    }

    private void validateWebhookRequest(PaymentWebhookRequest request) {
        if (request == null || request.eventId() == null || request.eventId().isBlank()
                || request.eventId().length() > 150 || request.paymentId() == null
                || request.providerTransactionId() == null || request.providerTransactionId().isBlank()
                || request.providerTransactionId().length() > 255 || request.status() == null
                || request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidWebhookException("Missing or invalid webhook fields.");
        }
        if (request.status() != PaymentStatus.SUCCESS && request.status() != PaymentStatus.FAILED) {
            throw new InvalidWebhookException("Webhook status must be SUCCESS or FAILED.");
        }
    }

    private MasterOrderJpaEntity findOrder(UUID id) {
        return masterOrderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));
    }

    private OrderResponse toOrderResponse(MasterOrderJpaEntity order) {
        return toOrderResponse(order, subOrderRepository.findByMasterOrderId(order.getId()));
    }

    private OrderResponse toOrderResponse(MasterOrderJpaEntity order, List<SubOrderJpaEntity> subOrders) {
        return new OrderResponse(order.getId(), order.getBookingId(), order.getCustomerId(), order.getStatus(),
                order.getTotalAmount(), order.getDiscountAmount(),
                subOrders.stream().map(this::toSubOrderResponse).toList(),
                order.getCreatedAt(), order.getUpdatedAt());
    }

    private SubOrderResponse toSubOrderResponse(SubOrderJpaEntity subOrder) {
        String waiverContent = null;
        String waiverLanguage = null;
        boolean fallbackUsed = false;

        if (Boolean.TRUE.equals(subOrder.getWaiverRequired())) {
            if (localizedContentSelector != null) {
                var sel = localizedContentSelector.selectDetailed(subOrder.getWaiverContent(), subOrder.getWaiverContentEn());
                waiverContent = sel.content();
                waiverLanguage = sel.language();
                fallbackUsed = sel.fallbackUsed();
            } else {
                waiverContent = subOrder.getWaiverContent() != null ? subOrder.getWaiverContent() : subOrder.getWaiverContentEn();
                waiverLanguage = "VI";
                fallbackUsed = false;
            }
        }

        return new SubOrderResponse(
                subOrder.getId(),
                subOrder.getVendorId(),
                subOrder.getServiceId(),
                subOrder.getSlotId(),
                subOrder.getQuantity(),
                subOrder.getUnitPrice(),
                subOrder.getSubtotalAmount(),
                subOrder.getStatus(),
                subOrder.getDiscountAmount(),
                subOrder.getFinalAmount(),
                subOrder.getWaiverRequired(),
                subOrder.getWaiverVersion(),
                waiverContent,
                waiverLanguage,
                fallbackUsed,
                subOrder.getWaiverAccepted(),
                subOrder.getWaiverAcceptedAt(),
                subOrder.getWaiverAcceptedBy(),
                subOrder.getWaiverAcceptedLanguage(),
                subOrder.getCancellationPolicyVersion()
        );
    }

    private void validateWaiverAcceptance(MasterOrderJpaEntity order) {
        List<SubOrderJpaEntity> subOrders = subOrderRepository.findByMasterOrderIdForUpdate(order.getId());
        List<MissingWaiverItem> missingSubOrders = new ArrayList<>();
        for (var sub : subOrders) {
            WaiverAcceptanceGuard.missing(sub.getId(), sub.getServiceId(), sub.getWaiverVersion(),
                    sub.getWaiverRequired(), sub.getWaiverAccepted(), sub.getWaiverContent(), sub.getWaiverContentEn(),
                    serviceWaiverLookupPort, localizedContentSelector).ifPresent(missingSubOrders::add);
        }
        if (!missingSubOrders.isEmpty()) {
            throw new WaiverAcceptanceRequiredException(order.getId(), missingSubOrders);
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrderId(UUID customerId, UUID orderId) {
        MasterOrderJpaEntity order = findOrder(orderId);
        assertOwner(order.getCustomerId(), customerId);
        return paymentRepository.findByMasterOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentDetail(UUID customerId, UUID paymentId) {
        PaymentJpaEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new OrderNotFoundException("Payment not found with id: " + paymentId));
        MasterOrderJpaEntity order = findOrder(payment.getMasterOrderId());
        assertOwner(order.getCustomerId(), customerId);
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public Page<AdminOrderSummaryResponse> getAdminOrders(
            MasterOrderStatus status,
            PaymentOrderStatus paymentStatus,
            UUID customerId,
            UUID vendorId,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable) {
        validateDateRange(fromDate, toDate);
        Page<MasterOrderJpaEntity> ordersPage = masterOrderRepository.findByAdminFilters(
                status, paymentStatus, customerId, vendorId, fromDate, toDate, pageable);

        if (ordersPage.isEmpty()) {
            return ordersPage.map(o -> null);
        }

        List<UUID> orderIds = ordersPage.getContent().stream()
                .map(MasterOrderJpaEntity::getId)
                .toList();

        List<SubOrderJpaEntity> subOrders = subOrderRepository.findByMasterOrderIdIn(orderIds);
        Map<UUID, List<SubOrderJpaEntity>> subOrdersByOrder = subOrders.stream()
                .collect(Collectors.groupingBy(SubOrderJpaEntity::getMasterOrderId));

        return ordersPage.map(order -> {
            List<SubOrderJpaEntity> items = subOrdersByOrder.getOrDefault(order.getId(), List.of());
            List<UUID> vendorIds = items.stream()
                    .map(SubOrderJpaEntity::getVendorId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return new AdminOrderSummaryResponse(
                    order.getId(),
                    order.getBookingId(),
                    order.getCustomerId(),
                    order.getStatus(),
                    order.getPaymentStatus(),
                    order.getTotalAmount(),
                    order.getDiscountAmount(),
                    items.size(),
                    vendorIds,
                    order.getPaymentDeadline(),
                    order.getCreatedAt(),
                    order.getUpdatedAt()
            );
        });
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getOrderPayments(UUID userId, UUID orderId, boolean isAdmin) {
        MasterOrderJpaEntity order = findOrder(orderId);
        if (!isAdmin) {
            assertOwner(order.getCustomerId(), userId);
        }
        return paymentRepository.findByMasterOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAdminPayments(
            PaymentStatus status,
            PaymentProvider provider,
            UUID orderId,
            UUID customerId,
            UUID vendorId,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable) {
        validateDateRange(fromDate, toDate);
        return paymentRepository.findByAdvancedFilters(
                status, provider, orderId, customerId, vendorId, fromDate, toDate, pageable)
                .map(this::toPaymentResponse);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAdminPayments(
            PaymentStatus status,
            PaymentProvider provider,
            UUID orderId,
            Pageable pageable) {
        return getAdminPayments(status, provider, orderId, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getAdminPaymentDetail(UUID paymentId) {
        PaymentJpaEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new OrderNotFoundException("Payment not found with id: " + paymentId));
        return toPaymentResponse(payment);
    }

    public PaymentResponse toPaymentResponse(PaymentJpaEntity payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMasterOrderId(),
                payment.getProvider(),
                payment.getProviderTransactionId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                payment.getWebhookEventId(),
                payment.getPaymentUrl(),
                payment.getQrCodeUrl(),
                payment.getExpiresAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getProviderOrderId(),
                payment.getLastError(),
                payment.getReconciliationAttempts(),
                payment.getReconciliationNextAttemptAt(),
                payment.getLastReconciledAt()
        );
    }

    private RefundResponse toRefundResponse(RefundJpaEntity refund) {
        return new RefundResponse(refund.getId(), refund.getSubOrderId(), refund.getAmount(),
                refund.getRefundPercentage(), refund.getReason(), refund.getStatus(), refund.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<RefundDetailResponse> getOrderRefunds(UUID userId, UUID orderId, boolean isAdmin) {
        MasterOrderJpaEntity order = masterOrderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!isAdmin) {
            assertOwner(order.getCustomerId(), userId);
        }
        List<UUID> subOrderIds = subOrderRepository.findByMasterOrderId(order.getId()).stream()
                .map(SubOrderJpaEntity::getId)
                .toList();
        if (subOrderIds.isEmpty()) {
            return List.of();
        }
        return refundRepository.findBySubOrderIdInOrderByCreatedAtDesc(subOrderIds).stream()
                .map(RefundDetailResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<RefundDetailResponse> getAdminRefunds(
            RefundStatus status,
            RefundReason reason,
            UUID subOrderId,
            PaymentProvider provider,
            UUID vendorId,
            UUID customerId,
            UUID orderId,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable) {
        validateDateRange(fromDate, toDate);
        return refundRepository.findByAdvancedFilters(
                status, reason, subOrderId, provider, vendorId, customerId, orderId, fromDate, toDate, pageable)
                .map(RefundDetailResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<RefundDetailResponse> getAdminRefunds(
            RefundStatus status,
            RefundReason reason,
            UUID subOrderId,
            Pageable pageable) {
        return getAdminRefunds(status, reason, subOrderId, null, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public RefundDetailResponse getAdminRefundDetail(UUID refundId) {
        RefundJpaEntity refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new RefundNotFoundException(refundId));
        return RefundDetailResponse.fromEntity(refund);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean reconcilePayment(UUID paymentId) {
        if (paymentGatewayPort == null || gatewayTransaction == null) {
            throw new IllegalStateException("Transactional payment gateway is not configured.");
        }
        PaymentJpaEntity snapshot = gatewayTransaction.execute(tx -> {
            PaymentJpaEntity payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
            OffsetDateTime now = OffsetDateTime.now();
            if (payment == null || payment.getStatus() != PaymentStatus.PENDING
                    || (payment.getProvider() != PaymentProvider.PAYPAL && payment.getProvider() != PaymentProvider.VNPAY)
                    || (payment.getReconciliationNextAttemptAt() != null && payment.getReconciliationNextAttemptAt().isAfter(now))) {
                return null;
            }
            int attempts = Math.min(20, payment.getReconciliationAttempts() + 1);
            payment.setReconciliationAttempts(attempts);
            payment.setReconciliationNextAttemptAt(now.plusSeconds(Math.min(300, 30L << Math.min(4, attempts - 1))));
            paymentRepository.saveAndFlush(payment);
            return payment;
        });
        if (snapshot == null) {
            return false;
        }

        PaymentCaptureResult result;
        try {
            if (snapshot.getProviderOrderId() == null || snapshot.getProviderOrderId().isBlank()
                    || (snapshot.getProvider() == PaymentProvider.VNPAY && snapshot.getProviderTransactionDate() == null)) {
                throw new PaymentGatewayException("Persisted gateway reference and transaction date are required.");
            }
            result = snapshot.getProvider() == PaymentProvider.PAYPAL && snapshot.getCaptureRequestedAt() != null
                    ? paymentGatewayPort.queryCapture(PaymentProvider.PAYPAL, snapshot.getProviderOrderId())
                    : paymentGatewayPort.queryPayment(snapshot.getProvider(), snapshot.getProviderOrderId(), snapshot.getProviderTransactionDate());
        } catch (Exception ex) {
            return recordReconciliationFailure(snapshot, "GATEWAY_QUERY_AWAITING_VERIFICATION: " + ex.getMessage());
        }

        try {
            return Boolean.TRUE.equals(gatewayTransaction.execute(tx -> {
                PaymentJpaEntity payment = lockPayment(paymentId);
                MasterOrderJpaEntity order = lockPaymentOrder(payment);
                if (payment.getStatus() != PaymentStatus.PENDING) {
                    recordReconciliationAudit(payment, "RECONCILE_PAYMENT_SKIPPED");
                    return false;
                }
                if (!samePaymentIntent(payment, snapshot)) {
                    throw new PaymentGatewayException("The persisted payment intent changed during reconciliation.");
                }
                payment.setLastReconciledAt(OffsetDateTime.now());
                if (result != null && result.success() && "COMPLETED".equalsIgnoreCase(result.status())) {
                    validateCaptureResult(payment, result);
                    applyPaymentSuccess(payment, order, result.captureId(), null, null);
                    recordReconciliationAudit(payment, "RECONCILE_PAYMENT_SUCCESS");
                    return true;
                }
                boolean failed = result != null && !result.success()
                        && ((payment.getProvider() == PaymentProvider.VNPAY && "FAILED".equalsIgnoreCase(result.status()))
                        || (payment.getProvider() == PaymentProvider.PAYPAL
                        && ("VOIDED".equalsIgnoreCase(result.status()) || "EXPIRED".equalsIgnoreCase(result.status())
                        || "DECLINED".equalsIgnoreCase(result.status()))));
                if (failed) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setLastError("GATEWAY_PAYMENT_FAILED: " + result.status());
                    payment.setReconciliationNextAttemptAt(null);
                    paymentRepository.save(payment);
                    recordReconciliationAudit(payment, "RECONCILE_PAYMENT_FAILED");
                    return true;
                }
                // Local URL expiry does not prove that the gateway has not collected money.
                setReconciliationError(payment, "GATEWAY_QUERY_AWAITING_VERIFICATION: "
                        + (result == null ? "No gateway result." : result.status()));
                paymentRepository.save(payment);
                recordReconciliationAudit(payment, "RECONCILE_PAYMENT_PENDING");
                return false;
            }));
        } catch (RuntimeException ex) {
            return recordReconciliationFailure(snapshot, "RECONCILIATION_APPLY_REQUIRES_REVIEW: " + ex.getMessage());
        }
    }

    private boolean samePaymentIntent(PaymentJpaEntity payment, PaymentJpaEntity snapshot) {
        return payment.getProvider() == snapshot.getProvider()
                && Objects.equals(payment.getMasterOrderId(), snapshot.getMasterOrderId())
                && Objects.equals(payment.getProviderOrderId(), snapshot.getProviderOrderId())
                && Objects.equals(payment.getProviderTransactionDate(), snapshot.getProviderTransactionDate())
                && Objects.equals(payment.getProviderAmount(), snapshot.getProviderAmount())
                && Objects.equals(payment.getProviderCurrency(), snapshot.getProviderCurrency())
                && Objects.equals(payment.getAmount(), snapshot.getAmount());
    }

    private boolean recordReconciliationFailure(PaymentJpaEntity snapshot, String error) {
        gatewayTransaction.executeWithoutResult(tx -> {
            PaymentJpaEntity payment = paymentRepository.findByIdForUpdate(snapshot.getId()).orElse(null);
            if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
                setReconciliationError(payment, error);
                payment.setLastReconciledAt(OffsetDateTime.now());
                paymentRepository.save(payment);
                recordReconciliationAudit(payment, "RECONCILE_PAYMENT_PENDING");
            }
        });
        return false;
    }

    private void setReconciliationError(PaymentJpaEntity payment, String error) {
        String queryMarker = "GATEWAY_QUERY_AWAITING_VERIFICATION";
        String captureMarker = "GATEWAY_TIMEOUT_AWAITING_VERIFICATION";
        boolean captureUnresolved = payment.getProvider() == PaymentProvider.PAYPAL
                && (payment.getCaptureRequestId() != null || payment.getCaptureRequestedAt() != null
                || (payment.getLastError() != null && payment.getLastError().startsWith(captureMarker)));
        payment.setLastError(captureUnresolved && error.startsWith(queryMarker)
                ? captureMarker + error.substring(queryMarker.length()) : error);
    }

    private void recordReconciliationAudit(PaymentJpaEntity payment, String action) {
        if (auditLogInternalApi != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("provider", payment.getProvider());
            metadata.put("status", payment.getStatus());
            metadata.put("transactionId", payment.getProviderTransactionId());
            metadata.put("lastError", payment.getLastError());
            metadata.put("attempts", payment.getReconciliationAttempts());
            try {
                auditLogInternalApi.recordTransactionalAuditLog(null, action, "PAYMENT", payment.getId(),
                        objectMapper.writeValueAsString(metadata));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Payment reconciliation audit could not be serialized.", ex);
            }
        }
    }

    private void validateDateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("From date must not be after to date.");
        }
    }

    private void assertOwner(UUID ownerId, UUID userId) {
        if (ownerId == null || userId == null || !ownerId.equals(userId)) {
            throw new AccessDeniedException("User does not have permission to access this order.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
        }
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 8 to 100 letters, digits, dots, underscores, colons, or hyphens.");
        }
    }

    private boolean isRefundableStatus(SubOrderStatus status) {
        return status == SubOrderStatus.CONFIRMED || status == SubOrderStatus.CHECKED_IN
                || status == SubOrderStatus.IN_PROGRESS || status == SubOrderStatus.COMPLETED;
    }
}

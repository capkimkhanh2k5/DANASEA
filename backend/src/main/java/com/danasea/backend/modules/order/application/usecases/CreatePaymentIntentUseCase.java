package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.modules.order.application.dtos.CreatePaymentIntentCommand;
import com.danasea.backend.modules.order.application.services.WaiverAcceptanceGuard;
import com.danasea.backend.modules.order.application.services.DepartureCheckoutGuard;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.exceptions.WaiverAcceptanceRequiredException;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.MissingWaiverItem;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.ports.ServiceWaiverLookupPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.infrastructure.adapters.PaymentGatewayRegistry;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CreatePaymentIntentUseCase {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");

    private final MasterOrderRepositoryPort masterOrderRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final JpaPaymentRepository paymentRepository;
    private final TransactionTemplate transaction;
    private final SubOrderRepositoryPort subOrderRepository;
    private final ServiceWaiverLookupPort serviceWaiverLookupPort;
    private final LocalizedContentSelector localizedContentSelector;

    private DepartureCheckoutGuard departureCheckout;
    private PaymentGatewayRegistry paymentGatewayRegistry;

    @Autowired
    public void setPaymentGatewayRegistry(@Autowired(required = false) PaymentGatewayRegistry paymentGatewayRegistry) {
        this.paymentGatewayRegistry = paymentGatewayRegistry;
    }

    @Autowired
    public void setDepartureCheckout(DepartureCheckoutGuard departureCheckout) {
        this.departureCheckout = departureCheckout;
    }

    @Autowired
    public CreatePaymentIntentUseCase(
            MasterOrderRepositoryPort masterOrderRepository,
            PaymentGatewayPort paymentGatewayPort,
            JpaPaymentRepository paymentRepository,
            PlatformTransactionManager transactionManager,
            SubOrderRepositoryPort subOrderRepository,
            ServiceWaiverLookupPort serviceWaiverLookupPort,
            LocalizedContentSelector localizedContentSelector) {
        this.masterOrderRepository = masterOrderRepository;
        this.paymentGatewayPort = paymentGatewayPort;
        this.paymentRepository = paymentRepository;
        this.subOrderRepository = subOrderRepository;
        this.serviceWaiverLookupPort = serviceWaiverLookupPort;
        this.localizedContentSelector = localizedContentSelector;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CreatePaymentIntentUseCase(
            MasterOrderRepositoryPort masterOrderRepository,
            PaymentGatewayPort paymentGatewayPort,
            JpaPaymentRepository paymentRepository,
            PlatformTransactionManager transactionManager) {
        this(masterOrderRepository, paymentGatewayPort, paymentRepository, transactionManager, null, null, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentIntentResult execute(CreatePaymentIntentCommand command) {
        if (command == null || command.customerId() == null || command.orderId() == null || command.provider() == null) {
            throw new IllegalArgumentException("Customer ID, Order ID and Payment Provider are required.");
        }
        if (command.idempotencyKey() == null || !IDEMPOTENCY_KEY_PATTERN.matcher(command.idempotencyKey().trim()).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 8 to 100 letters, digits, dots, underscores, colons, or hyphens.");
        }
        if (command.provider() != PaymentProvider.VNPAY && command.provider() != PaymentProvider.PAYPAL
                && command.provider() != PaymentProvider.PAYOS && command.provider() != PaymentProvider.ZALOPAY) {
            throw new InvalidOrderStateException("The selected payment provider is not supported.");
        }

        // Commit the identity before contacting the provider; a timeout must not erase it.
        UUID paymentId = Objects.requireNonNull(transaction.execute(status -> preparePayment(command)));
        IntentOutcome outcome = Objects.requireNonNull(transaction.execute(status -> initializeIntent(paymentId, command)));
        if (outcome.expired()) {
            throw new InvalidOrderStateException(
                    "Payment intent has expired. Please initiate a new payment with a new idempotency key.");
        }
        return outcome.result();
    }

    private UUID preparePayment(CreatePaymentIntentCommand command) {
        MasterOrder order = requirePayableOrder(command);

        if (subOrderRepository != null) {
            List<SubOrder> subOrders = subOrderRepository.findByMasterOrderIdForUpdate(order.getId());
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

        String key = command.idempotencyKey().trim();
        var existing = paymentRepository.findByMasterOrderIdAndIdempotencyKey(order.getId(), key);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setMasterOrderId(order.getId());
        payment.setProvider(command.provider());
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(key);
        OffsetDateTime expiry = OffsetDateTime.now().plusMinutes(15);
        if (order.getPaymentDeadline() != null && order.getPaymentDeadline().isBefore(expiry)) {
            expiry = order.getPaymentDeadline();
        }
        payment.setExpiresAt(expiry);
        return paymentRepository.save(payment).getId();
    }

    private IntentOutcome initializeIntent(UUID paymentId, CreatePaymentIntentCommand command) {
        // Serialize retries with the same row and use the same lock order as webhook processing.
        PaymentJpaEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new InvalidOrderStateException("The payment intent no longer exists."));
        if (payment.getProvider() != command.provider()) {
            throw new InvalidOrderStateException("The idempotency key was already used with another provider.");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidOrderStateException("The payment has already reached a terminal state: " + payment.getStatus());
        }
        if (payment.getExpiresAt() == null) {
            payment.setExpiresAt((payment.getCreatedAt() != null ? payment.getCreatedAt() : OffsetDateTime.now()).plusMinutes(15));
        }
        if (!payment.getExpiresAt().isAfter(OffsetDateTime.now())) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            // Reject outside the transaction so the FAILED transition remains committed.
            return new IntentOutcome(null, true);
        }
        MasterOrder order = requirePayableOrder(command);
        if (payment.getAmount() == null || payment.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new InvalidOrderStateException("The payment amount no longer matches the order amount.");
        }
        if (payment.getPaymentUrl() == null || payment.getPaymentUrl().isBlank()) {
            PaymentGatewayPort adapter = (payment.getProvider() == PaymentProvider.PAYOS || payment.getProvider() == PaymentProvider.ZALOPAY)
                    ? (paymentGatewayRegistry != null ? paymentGatewayRegistry.getAdapter(payment.getProvider()) : paymentGatewayPort)
                    : (paymentGatewayPort != null ? paymentGatewayPort : (paymentGatewayRegistry != null ? paymentGatewayRegistry.getAdapter(payment.getProvider()) : null));
            PaymentIntentResult result = adapter.createPaymentIntent(
                    payment.getId(), order.getId(), payment.getAmount(), payment.getProvider());
            if (result == null || !payment.getId().equals(result.paymentId())
                    || !order.getId().equals(result.orderId()) || payment.getProvider() != result.provider()
                    || result.amount() == null || payment.getAmount().compareTo(result.amount()) != 0
                    || result.paymentUrl() == null || result.paymentUrl().isBlank()) {
                throw new PaymentGatewayException("The gateway returned an invalid payment intent.");
            }
            payment.setPaymentUrl(result.paymentUrl());
            payment.setQrCodeUrl(result.qrCodeUrl());
            payment.setProviderAmount(result.providerAmount());
            payment.setProviderCurrency(result.providerCurrency());
            payment.setProviderTransactionDate(result.providerTransactionDate());
            if (result.providerOrderId() != null) {
                payment.setProviderOrderId(result.providerOrderId());
            }
            if (result.exchangeRate() != null) {
                payment.setExchangeRate(result.exchangeRate());
                payment.setExchangeRateDate(result.exchangeRateDate());
                payment.setExchangeRateSource(result.exchangeRateSource());
            }
            if (result.expiresAt() != null && result.expiresAt().isBefore(payment.getExpiresAt())) {
                payment.setExpiresAt(result.expiresAt());
            }
            paymentRepository.save(payment);
        }
        return new IntentOutcome(new PaymentIntentResult(payment.getId(), payment.getMasterOrderId(),
                payment.getProvider(), payment.getAmount(), payment.getPaymentUrl(), payment.getQrCodeUrl(),
                payment.getExpiresAt(), payment.getProviderOrderId(), payment.getProviderAmount(),
                payment.getProviderCurrency(), payment.getProviderTransactionDate(),
                payment.getExchangeRate(), payment.getExchangeRateDate(), payment.getExchangeRateSource()), false);
    }

    private MasterOrder requirePayableOrder(CreatePaymentIntentCommand command) {
        MasterOrder order = masterOrderRepository.findByIdForUpdate(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        if (!command.customerId().equals(order.getCustomerId())) {
            throw new UnauthorizedOrderAccessException(command.orderId(), command.customerId());
        }
        if (order.getStatus() != MasterOrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderStateException(
                    "Payment intent can only be created for an order in PENDING_PAYMENT status; current: " + order.getStatus());
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderStateException("The order amount must be positive.");
        }
        if (order.getPaymentDeadline() != null && !order.getPaymentDeadline().isAfter(OffsetDateTime.now())) {
            throw new InvalidOrderStateException("The order payment deadline has expired.");
        }
        if (departureCheckout != null && order.getBookingId() != null) {
            departureCheckout.requireBookable(departureCheckout.lockBooking(order.getBookingId()));
        }
        return order;
    }

    private record IntentOutcome(PaymentIntentResult result, boolean expired) {
    }
}

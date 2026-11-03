package com.danasea.backend.modules.order.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.danasea.backend.modules.order.application.dtos.CreatePaymentIntentCommand;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaPaymentRepository;

class PaymentIntentGatewayContractTest {

    private static final UUID PAYMENT_ID = UUID.fromString("a662644d-a809-497c-92eb-359da5311717");
    private static final UUID ORDER_ID = UUID.fromString("72a04539-b807-4305-aa10-3a8f2562ed92");
    private static final UUID CUSTOMER_ID = UUID.fromString("b7c6f522-7d0d-437e-8b93-6c0ffad6fa3e");
    private static final BigDecimal AMOUNT = new BigDecimal("500000");
    private static final String APPROVAL_URL = "https://www.sandbox.paypal.com/checkoutnow?token=ORDER-BOUND";
    private static final String KEY = "intent-contract-key";

    private final AtomicReference<PaymentJpaEntity> storedPayment = new AtomicReference<>();
    private JpaPaymentRepository payments;
    private PaymentGatewayPort gateway;
    private CreatePaymentIntentUseCase useCase;

    @BeforeEach
    void setUp() {
        payments = mock(JpaPaymentRepository.class);
        gateway = mock(PaymentGatewayPort.class);
        var orders = mock(MasterOrderRepositoryPort.class);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        useCase = new CreatePaymentIntentUseCase(orders, gateway, payments, transactions);
        MasterOrder order = MasterOrder.createFromBooking(CUSTOMER_ID, UUID.randomUUID(), AMOUNT,
                OffsetDateTime.now().plusMinutes(15), "order-contract-key");
        order.setId(ORDER_ID);
        order.setStatus(MasterOrderStatus.PENDING_PAYMENT);
        when(orders.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(payments.findByMasterOrderIdAndIdempotencyKey(ORDER_ID, KEY))
                .thenAnswer(invocation -> Optional.ofNullable(storedPayment.get()));
        when(payments.save(any(PaymentJpaEntity.class))).thenAnswer(invocation -> {
            PaymentJpaEntity payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(PAYMENT_ID);
            }
            storedPayment.set(payment);
            return payment;
        });
        when(payments.findByIdForUpdate(PAYMENT_ID))
                .thenAnswer(invocation -> Optional.ofNullable(storedPayment.get()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidGatewayIntents")
    void rejectsEachInvalidGatewayIntentWithoutPersistingItsFields(String scenario, PaymentIntentResult result) {
        when(gateway.createPaymentIntent(PAYMENT_ID, ORDER_ID, AMOUNT, PaymentProvider.PAYPAL)).thenReturn(result);

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("invalid payment intent");

        PaymentJpaEntity payment = storedPayment.get();
        assertThat(payment.getId()).isEqualTo(PAYMENT_ID);
        assertThat(payment.getMasterOrderId()).isEqualTo(ORDER_ID);
        assertThat(payment.getProvider()).isEqualTo(PaymentProvider.PAYPAL);
        assertThat(payment.getAmount()).isEqualByComparingTo("500000");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentUrl()).isNull();
        assertThat(payment.getProviderOrderId()).isNull();
        assertThat(payment.getProviderAmount()).isNull();
        assertThat(payment.getProviderCurrency()).isNull();
        verify(payments, times(1)).save(any(PaymentJpaEntity.class));
    }

    private static Stream<Arguments> invalidGatewayIntents() {
        return Stream.of(
                Arguments.of("missing result", null),
                Arguments.of("different payment identity", intent(
                        UUID.fromString("53cdbb94-7550-412b-b664-9a0e58177211"), ORDER_ID,
                        PaymentProvider.PAYPAL, AMOUNT, APPROVAL_URL)),
                Arguments.of("different order identity", intent(PAYMENT_ID,
                        UUID.fromString("1f551037-d08a-452c-bf5c-a0139cd6e047"),
                        PaymentProvider.PAYPAL, AMOUNT, APPROVAL_URL)),
                Arguments.of("different provider", intent(PAYMENT_ID, ORDER_ID,
                        PaymentProvider.VNPAY, AMOUNT, APPROVAL_URL)),
                Arguments.of("different amount", intent(PAYMENT_ID, ORDER_ID,
                        PaymentProvider.PAYPAL, new BigDecimal("1"), APPROVAL_URL)),
                Arguments.of("missing amount", intent(PAYMENT_ID, ORDER_ID,
                        PaymentProvider.PAYPAL, null, APPROVAL_URL)),
                Arguments.of("blank approval URL", intent(PAYMENT_ID, ORDER_ID,
                        PaymentProvider.PAYPAL, AMOUNT, " ")),
                Arguments.of("missing approval URL", intent(PAYMENT_ID, ORDER_ID,
                        PaymentProvider.PAYPAL, AMOUNT, null)));
    }

    @Test
    void aValidIntentPersistsTheProviderSnapshotAndTheEarlierExpiry() {
        OffsetDateTime providerExpiry = OffsetDateTime.now().plusMinutes(5);
        var providerResult = new PaymentIntentResult(PAYMENT_ID, ORDER_ID, PaymentProvider.PAYPAL,
                new BigDecimal("500000.00"), APPROVAL_URL, "https://qr.example/intent", providerExpiry,
                "ORDER-BOUND", new BigDecimal("19.31"), "USD", "20261101123045");
        when(gateway.createPaymentIntent(PAYMENT_ID, ORDER_ID, AMOUNT, PaymentProvider.PAYPAL))
                .thenReturn(providerResult);

        PaymentIntentResult result = useCase.execute(command());

        PaymentJpaEntity payment = storedPayment.get();
        assertThat(payment.getProviderOrderId()).isEqualTo("ORDER-BOUND");
        assertThat(payment.getProviderAmount()).isEqualByComparingTo("19.31");
        assertThat(payment.getProviderCurrency()).isEqualTo("USD");
        assertThat(payment.getProviderTransactionDate()).isEqualTo("20261101123045");
        assertThat(payment.getPaymentUrl()).isEqualTo(APPROVAL_URL);
        assertThat(payment.getQrCodeUrl()).isEqualTo("https://qr.example/intent");
        assertThat(payment.getExpiresAt()).isEqualTo(providerExpiry);
        assertThat(result.expiresAt()).isEqualTo(providerExpiry);
        assertThat(result.providerAmount()).isEqualByComparingTo("19.31");
        verify(payments, times(2)).save(payment);
    }

    @Test
    void retryAfterInvalidGatewayIdentityReusesTheOriginalPaymentIdentity() {
        var invalidResult = intent(UUID.fromString("53cdbb94-7550-412b-b664-9a0e58177211"), ORDER_ID,
                PaymentProvider.PAYPAL, AMOUNT, APPROVAL_URL);
        var validResult = intent(PAYMENT_ID, ORDER_ID, PaymentProvider.PAYPAL, AMOUNT, APPROVAL_URL);
        when(gateway.createPaymentIntent(PAYMENT_ID, ORDER_ID, AMOUNT, PaymentProvider.PAYPAL))
                .thenReturn(invalidResult, validResult);
        assertThatThrownBy(() -> useCase.execute(command())).isInstanceOf(PaymentGatewayException.class);

        PaymentIntentResult result = useCase.execute(command());

        assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.paymentUrl()).isEqualTo(APPROVAL_URL);
        verify(gateway, times(2)).createPaymentIntent(eq(PAYMENT_ID), eq(ORDER_ID), eq(AMOUNT), eq(PaymentProvider.PAYPAL));
        verify(payments, times(2)).save(any(PaymentJpaEntity.class));
    }

    private static PaymentIntentResult intent(UUID paymentId, UUID orderId, PaymentProvider provider,
            BigDecimal amount, String paymentUrl) {
        return new PaymentIntentResult(paymentId, orderId, provider, amount, paymentUrl, null,
                OffsetDateTime.now().plusMinutes(10), "ORDER-BOUND", new BigDecimal("19.31"), "USD", null);
    }

    private static CreatePaymentIntentCommand command() {
        return new CreatePaymentIntentCommand(CUSTOMER_ID, ORDER_ID, PaymentProvider.PAYPAL, KEY);
    }
}

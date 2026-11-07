package com.danasea.backend.modules.order.infrastructure.adapters;

import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.PaymentGatewayPort;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.danasea.backend.modules.order.domain.ports.RefundResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("PaymentGatewayRegistry Unit Tests")
class PaymentGatewayRegistryTest {

    static abstract class BaseDummyAdapter implements PaymentGatewayPort {
        @Override public PaymentIntentResult createPaymentIntent(UUID paymentId, UUID orderId, BigDecimal amount, PaymentProvider provider) { return null; }
        @Override public boolean verifyWebhookSignature(Map<String, String> rawParams, String signature) { return false; }
        @Override public RefundResult requestRefund(String providerTransactionId, BigDecimal amount) { return null; }
    }

    static class DummyPayPalAdapter extends BaseDummyAdapter {}
    static class DummyVNPayAdapter extends BaseDummyAdapter {}
    static class DummyPayOSAdapter extends BaseDummyAdapter {}
    static class DummyZaloPayAdapter extends BaseDummyAdapter {}
    static class DummyUnknownAdapter extends BaseDummyAdapter {}

    @Test
    void nullConstructor_initializesWithEmptyList() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(null);
        assertThat(registry.supports(PaymentProvider.PAYPAL)).isFalse();
    }

    @Test
    void getAdapter_nullProvider_throwsInvalidOrderStateException() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of());
        assertThatThrownBy(() -> registry.getAdapter(null))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Payment provider must not be null");
    }

    @Test
    void getAdapter_matchingSupportsProvider_returnsAdapterAndCaches() {
        PaymentGatewayPort mockAdapter = mock(PaymentGatewayPort.class);
        when(mockAdapter.supportsProvider(PaymentProvider.PAYOS)).thenReturn(true);

        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(mockAdapter));
        PaymentGatewayPort first = registry.getAdapter(PaymentProvider.PAYOS);
        PaymentGatewayPort second = registry.getAdapter(PaymentProvider.PAYOS);

        assertThat(first).isSameAs(mockAdapter);
        assertThat(second).isSameAs(mockAdapter);
        verify(mockAdapter, times(1)).supportsProvider(PaymentProvider.PAYOS);
    }

    @Test
    void getAdapter_supportsProviderThrowsException_isCaughtAndIgnored() {
        PaymentGatewayPort brokenAdapter = mock(PaymentGatewayPort.class);
        when(brokenAdapter.supportsProvider(any())).thenThrow(new RuntimeException("Probe failure"));

        PaymentGatewayPort workingAdapter = mock(PaymentGatewayPort.class);
        when(workingAdapter.supportsProvider(PaymentProvider.PAYOS)).thenReturn(true);

        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(brokenAdapter, workingAdapter));
        PaymentGatewayPort result = registry.getAdapter(PaymentProvider.PAYOS);

        assertThat(result).isSameAs(workingAdapter);
    }

    @Test
    void getAdapter_fallbackToClassName_matchesAllFourSupportedGateways() {
        DummyPayPalAdapter paypal = new DummyPayPalAdapter();
        DummyVNPayAdapter vnpay = new DummyVNPayAdapter();
        DummyPayOSAdapter payos = new DummyPayOSAdapter();
        DummyZaloPayAdapter zalopay = new DummyZaloPayAdapter();

        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(paypal, vnpay, payos, zalopay));

        assertThat(registry.getAdapter(PaymentProvider.PAYPAL)).isSameAs(paypal);
        assertThat(registry.getAdapter(PaymentProvider.VNPAY)).isSameAs(vnpay);
        assertThat(registry.getAdapter(PaymentProvider.PAYOS)).isSameAs(payos);
        assertThat(registry.getAdapter(PaymentProvider.ZALOPAY)).isSameAs(zalopay);
    }

    @Test
    void getAdapter_unsupportedProvider_throwsInvalidOrderStateException() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(new DummyUnknownAdapter()));
        assertThatThrownBy(() -> registry.getAdapter(PaymentProvider.MOMO))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Unsupported payment provider: MOMO");
    }

    @Test
    void supports_nullProvider_returnsFalse() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of());
        assertThat(registry.supports(null)).isFalse();
    }

    @Test
    void supports_validProvider_returnsTrue() {
        DummyPayOSAdapter payos = new DummyPayOSAdapter();
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(payos));
        assertThat(registry.supports(PaymentProvider.PAYOS)).isTrue();
    }

    @Test
    void supports_unsupportedProvider_returnsFalse() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of());
        assertThat(registry.supports(PaymentProvider.MOMO)).isFalse();
    }

    @Test
    void getSupportedProviders_returnsAllConfiguredGateways() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of());
        assertThat(registry.getSupportedProviders())
                .containsExactly(PaymentProvider.VNPAY, PaymentProvider.PAYPAL, PaymentProvider.PAYOS, PaymentProvider.ZALOPAY);
    }
}

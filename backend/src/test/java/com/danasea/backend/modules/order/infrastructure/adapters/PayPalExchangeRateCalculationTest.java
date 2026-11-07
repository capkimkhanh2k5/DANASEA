package com.danasea.backend.modules.order.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.danasea.backend.configs.properties.PayPalProperties;
import com.danasea.backend.modules.order.application.services.ExchangeRateService;
import com.danasea.backend.modules.order.application.services.ExchangeRateService.ExchangeRateSnapshot;
import com.danasea.backend.modules.order.domain.exceptions.ExchangeRateUnavailableException;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.ports.PaymentIntentResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayPal Dynamic Exchange Rate Conversion and Boundary Tests")
class PayPalExchangeRateCalculationTest {

    private static final String BASE = "https://api-m.sandbox.paypal.com";
    private static final BigDecimal FRANKFURTER_RATE = new BigDecimal("25709");
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 11, 5);
    private static final String SOURCE = "FRANKFURTER";

    @Mock
    private VNPayPaymentAdapter vnPayPaymentAdapter;

    @Mock
    private ExchangeRateService exchangeRateService;

    private MockRestServiceServer server;
    private PayPalPaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        PayPalProperties properties = new PayPalProperties("sandbox", "test-id", "test-secret", "WH-CONFIGURED", BASE, null, null);
        adapter = new PayPalPaymentAdapter(builder, properties, new ObjectMapper(), vnPayPaymentAdapter, exchangeRateService);
    }

    private void mockAuthAndOrder(UUID paymentId, String expectedUsdAmount) {
        server.expect(requestTo(BASE + "/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"test-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE + "/v2/checkout/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"purchase_units\":[{\"custom_id\":\"" + paymentId
                        + "\",\"reference_id\":\"" + paymentId
                        + "\",\"amount\":{\"currency_code\":\"USD\",\"value\":\"" + expectedUsdAmount + "\"}}]}"))
                .andRespond(withSuccess("{\"id\":\"PAYPAL-ORDER-123\",\"links\":[{\"rel\":\"approve\",\"href\":\"https://www.sandbox.paypal.com/checkoutnow?token=PAYPAL-ORDER-123\"}]}", MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest(name = "{0} VND / 25709 rate = {1} USD (previously {2} USD at 25900)")
    @CsvSource({
            "100000,   3.89,   3.86",
            "500000,  19.45,  19.31",
            "1000000, 38.90,  38.61",
            "5000000, 194.48, 193.05",
            "10000000, 388.97, 386.10"
    })
    @DisplayName("Calculates exact USD amounts for 5 DANASEA service price points without markup")
    void testFivePricePointsConversion(long vndAmount, String expectedUsd, String previousUsd) {
        UUID paymentId = UUID.randomUUID();
        when(exchangeRateService.getCurrentUsdToVndRate())
                .thenReturn(new ExchangeRateSnapshot(FRANKFURTER_RATE, EFFECTIVE_DATE, SOURCE));

        mockAuthAndOrder(paymentId, expectedUsd);

        PaymentIntentResult result = adapter.createPaymentIntent(
                paymentId, UUID.randomUUID(), BigDecimal.valueOf(vndAmount), PaymentProvider.PAYPAL);

        server.verify();

        assertThat(result.providerAmount()).isEqualByComparingTo(new BigDecimal(expectedUsd));
        assertThat(result.providerCurrency()).isEqualTo("USD");
        assertThat(result.exchangeRate()).isEqualByComparingTo(FRANKFURTER_RATE);
        assertThat(result.exchangeRateDate()).isEqualTo(EFFECTIVE_DATE);
        assertThat(result.exchangeRateSource()).isEqualTo(SOURCE);
    }

    @Test
    @DisplayName("Rejects order when converted USD is below $1.00 USD without silently bumping to $1.00")
    void rejectsBelowMinimumThreshold() {
        // 20,000 VND / 25,709 = 0.78 USD, which is < 1.00 USD
        BigDecimal smallAmount = new BigDecimal("20000");
        when(exchangeRateService.getCurrentUsdToVndRate())
                .thenReturn(new ExchangeRateSnapshot(FRANKFURTER_RATE, EFFECTIVE_DATE, SOURCE));

        assertThatThrownBy(() -> adapter.createPaymentIntent(
                UUID.randomUUID(), UUID.randomUUID(), smallAmount, PaymentProvider.PAYPAL))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("below the minimum threshold ($1.00 USD)");

        // Verifies PayPal API was NOT called and no upward clamping took place
        server.verify();
    }

    @Test
    @DisplayName("Fails checkout when exchange rate is unavailable or out of date")
    void failsWhenExchangeRateIsUnavailable() {
        when(exchangeRateService.getCurrentUsdToVndRate())
                .thenThrow(new ExchangeRateUnavailableException("Exchange rate USD/VND is stale. PayPal checkout is temporarily suspended."));

        assertThatThrownBy(() -> adapter.createPaymentIntent(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("500000"), PaymentProvider.PAYPAL))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("temporarily suspended");

        server.verify();
    }
}

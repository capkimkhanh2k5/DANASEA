package com.danasea.backend.modules.order.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentUrlHelperTest {

    @Test
    void preservesConfiguredRouteAndAppendsEncodedParameter() {
        assertThat(PaymentUrlHelper.appendQueryParams("https://checkout.example.com/payment/success?ref=a%20b",
                Map.of("paymentId", "x&y"))).isEqualTo(
                        "https://checkout.example.com/payment/success?ref=a%20b&paymentId=x%26y");
    }

    @Test
    void supportsLocalConfiguredRouteInDevelopment() {
        assertThat(PaymentUrlHelper.appendQueryParams("http://localhost:3000/payment/cancel",
                Map.of("status", "cancelled"))).isEqualTo("http://localhost:3000/payment/cancel?status=cancelled");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/payment/success", "ftp://checkout.example.com/pay",
            "https://checkout.example.com/pay#fragment", "https://secret@checkout.example.com/pay"})
    void rejectsAmbiguousRedirectConfiguration(String url) {
        assertThatThrownBy(() -> PaymentUrlHelper.appendQueryParams(url, Map.of("paymentId", "id")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

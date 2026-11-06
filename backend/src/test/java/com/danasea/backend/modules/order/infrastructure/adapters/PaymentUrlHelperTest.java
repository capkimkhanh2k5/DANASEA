package com.danasea.backend.modules.order.infrastructure.adapters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentUrlHelper Unit Tests")
class PaymentUrlHelperTest {

    private PaymentUrlHelper helper;

    @BeforeEach
    void setUp() {
        helper = new PaymentUrlHelper(null);
        PaymentUrlHelper.setGlobalInstance(helper);
    }

    @AfterEach
    void tearDown() {
        helper.setProductionOverride(null);
        PaymentUrlHelper.setGlobalInstance(null);
    }

    // --- 1. Development & Localhost ---

    @Test
    @DisplayName("Dev: sử dụng localhost khi ở môi trường development")
    void shouldUseLocalhostInDev() {
        helper.setProductionOverride(false);
        String result = helper.resolveUrl("http://localhost:3000/checkout/result", "/checkout/result");
        assertThat(result).isEqualTo("http://localhost:3000/checkout/result");
    }

    @Test
    @DisplayName("Dev: tự động chuẩn hóa path /payment/success cũ sang /checkout/result")
    void shouldNormalizeLegacyPathInDev() {
        helper.setProductionOverride(false);
        String result = helper.resolveUrl("http://localhost:3000/payment/success", "/checkout/result");
        assertThat(result).isEqualTo("http://localhost:3000/checkout/result");

        String cancelResult = helper.resolveUrl("http://localhost:3000/payment/cancel", "/checkout/result");
        assertThat(cancelResult).isEqualTo("http://localhost:3000/checkout/result");
    }

    @Test
    @DisplayName("Dev: fallback về http://localhost:3000/checkout/result khi URL rỗng")
    void shouldFallbackToDefaultDevUrlWhenBlank() {
        helper.setProductionOverride(false);
        assertThat(helper.resolveUrl(null, "/checkout/result")).isEqualTo("http://localhost:3000/checkout/result");
        assertThat(helper.resolveUrl("   ", "/checkout/result")).isEqualTo("http://localhost:3000/checkout/result");
    }

    // --- 2. Production Cấu Hình Riêng Hợp Lệ ---

    @Test

    @DisplayName("Production: chấp nhận cấu hình HTTPS công khai hợp lệ từ .env")
    void shouldAcceptValidHttpsUrlInProduction() {
        helper.setProductionOverride(true);

        String stagingUrl = "https://danasea.tailfc3563.ts.net/checkout/result";
        String resolved = helper.resolveUrl(stagingUrl, "/checkout/result");
        assertThat(resolved).isEqualTo("https://danasea.tailfc3563.ts.net/checkout/result");
    }

    @Test

    @DisplayName("Production: tự động chuẩn hóa path cũ nếu biến .env vẫn trỏ về /payment/success")
    void shouldNormalizeLegacyPathInProduction() {
        helper.setProductionOverride(true);

        String legacyUrl = "https://danasea.tailfc3563.ts.net/payment/success";
        String resolved = helper.resolveUrl(legacyUrl, "/checkout/result");
        assertThat(resolved).isEqualTo("https://danasea.tailfc3563.ts.net/checkout/result");
    }

    // --- 3. Production Fail-Closed ---

    @Test

    @DisplayName("Production: fail-closed nếu URL cấu hình bị thiếu hoặc rỗng")
    void shouldFailClosedWhenUrlIsMissingInProduction() {
        helper.setProductionOverride(true);

        assertThatThrownBy(() -> helper.resolveUrl(null, "/checkout/result"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment redirect URL is not configured");

        assertThatThrownBy(() -> helper.resolveUrl("   ", "/checkout/result"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment redirect URL is not configured");
    }

    @Test

    @DisplayName("Production: fail-closed nếu URL cấu hình trỏ về localhost hoặc 127.0.0.1")
    void shouldFailClosedWhenUrlIsLocalhostInProduction() {
        helper.setProductionOverride(true);

        assertThatThrownBy(() -> helper.resolveUrl("http://localhost:3000/checkout/result", "/checkout/result"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production payment redirect URL cannot point to localhost");

        assertThatThrownBy(() -> helper.resolveUrl("https://127.0.0.1:3000/checkout/result", "/checkout/result"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production payment redirect URL cannot point to localhost");
    }

    @Test

    @DisplayName("Production: fail-closed nếu URL cấu hình không sử dụng HTTPS")
    void shouldFailClosedWhenUrlIsNotHttpsInProduction() {
        helper.setProductionOverride(true);

        assertThatThrownBy(() -> helper.resolveUrl("http://danasea.vn/checkout/result", "/checkout/result"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production payment redirect URL must use HTTPS");
    }

    // --- 4. Bổ sung Query Parameters an toàn ---

    @Test

    @DisplayName("appendQueryParams: bổ sung tham số an toàn không xung đột query có sẵn")
    void shouldAppendQueryParamsSafely() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderId", "12345");
        params.put("paymentId", "67890");

        String urlWithoutQuery = "https://danasea.tailfc3563.ts.net/checkout/result";
        assertThat(PaymentUrlHelper.appendQueryParams(urlWithoutQuery, params))
                .isEqualTo("https://danasea.tailfc3563.ts.net/checkout/result?orderId=12345&paymentId=67890");

        String urlWithQuery = "https://danasea.tailfc3563.ts.net/checkout/result?ref=promo";
        assertThat(PaymentUrlHelper.appendQueryParams(urlWithQuery, params))
                .isEqualTo("https://danasea.tailfc3563.ts.net/checkout/result?ref=promo&orderId=12345&paymentId=67890");
    }

    // --- 5. Static Delegate Verification ---

    @Test
    @DisplayName("Static delegate resolveRedirectUrl hoạt động đúng")
    void shouldDelegateStatically() {
        helper.setProductionOverride(false);
        String result = PaymentUrlHelper.resolveRedirectUrl("http://localhost:3000/checkout/result",
                "/checkout/result");
        assertThat(result).isEqualTo("http://localhost:3000/checkout/result");
    }
}

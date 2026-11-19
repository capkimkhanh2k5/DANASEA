package com.danasea.backend.modules.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.danasea.backend.configs.properties.PayOSProperties;
import com.danasea.backend.configs.properties.ZaloPayProperties;
import com.danasea.backend.modules.ai.application.tools.GetPaymentProvidersTool;
import com.danasea.backend.modules.ai.application.tools.ToolExecutionContext;
import com.danasea.backend.modules.order.application.services.PaymentProviderAvailability;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.shared.i18n.SupportedLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GetPaymentProvidersToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultsEnableVnPayAndPayPalWhileKeepingMomoSepayDisabled() throws Exception {
        var availability = new PaymentProviderAvailability(
                payOs(false),
                zaloPay(false)
        );
        var tool = new GetPaymentProvidersTool(availability, mapper);

        assertThat(tool.getName()).isEqualTo("get_payment_providers");

        JsonNode result = mapper.readTree(tool.execute("{}"));
        assertThat(result.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(result.path("source").asText()).isEqualTo("PaymentProviderAvailability");
        assertThat(result.path("endpoint").asText()).isEqualTo("/api/payments/providers");
        assertThat(stringList(result.path("enabledProviders"))).containsExactly("VNPAY", "PAYPAL");
        assertThat(stringList(result.path("disabledProviders"))).containsExactly("MOMO", "SEPAY", "PAYOS", "ZALOPAY");

        JsonNode providers = result.path("providers");
        assertThat(providers.size()).isEqualTo(PaymentProvider.values().length);
        assertProviderState(providers, 0, "VNPAY", true);
        assertProviderState(providers, 1, "MOMO", false);
        assertProviderState(providers, 2, "SEPAY", false);
        assertProviderState(providers, 3, "PAYPAL", true);
        assertProviderState(providers, 4, "PAYOS", false);
        assertProviderState(providers, 5, "ZALOPAY", false);
    }

    @Test
    void togglesPayOsAndZaloPayAccordingToProperties() throws Exception {
        var payOsOnly = new GetPaymentProvidersTool(
                new PaymentProviderAvailability(payOs(true), zaloPay(false)), mapper);
        JsonNode payOsResult = mapper.readTree(payOsOnly.execute(null,
                new ToolExecutionContext(SupportedLanguage.VI, UUID.randomUUID(), UUID.randomUUID())));
        assertThat(stringList(payOsResult.path("enabledProviders"))).containsExactly("VNPAY", "PAYPAL", "PAYOS");
        assertThat(stringList(payOsResult.path("disabledProviders"))).containsExactly("MOMO", "SEPAY", "ZALOPAY");

        var zaloOnly = new GetPaymentProvidersTool(
                new PaymentProviderAvailability(payOs(false), zaloPay(true)), mapper);
        JsonNode zaloResult = mapper.readTree(zaloOnly.execute("{}"));
        assertThat(stringList(zaloResult.path("enabledProviders"))).containsExactly("VNPAY", "PAYPAL", "ZALOPAY");
        assertThat(stringList(zaloResult.path("disabledProviders"))).containsExactly("MOMO", "SEPAY", "PAYOS");

        var bothEnabled = new GetPaymentProvidersTool(
                new PaymentProviderAvailability(payOs(true), zaloPay(true)), mapper);
        JsonNode bothResult = mapper.readTree(bothEnabled.execute("{}"));
        assertThat(stringList(bothResult.path("enabledProviders")))
                .containsExactly("VNPAY", "PAYPAL", "PAYOS", "ZALOPAY");
        assertThat(stringList(bothResult.path("disabledProviders"))).containsExactly("MOMO", "SEPAY");
    }

    @Test
    void returnsStructuredErrorWhenAvailabilityServiceIsMissing() throws Exception {
        var tool = new GetPaymentProvidersTool(null, mapper);
        JsonNode result = mapper.readTree(tool.execute("{}"));
        assertThat(result.path("error").asText()).isEqualTo("FEATURE_UNAVAILABLE");
    }

    private PayOSProperties payOs(boolean enabled) {
        return new PayOSProperties("cid", "key", "chk", null, null, null, enabled);
    }

    private ZaloPayProperties zaloPay(boolean enabled) {
        return new ZaloPayProperties(123, "k1", "k2", null, null, null, null, enabled);
    }

    private List<String> stringList(JsonNode array) {
        return mapper.convertValue(array, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private void assertProviderState(JsonNode providers, int index, String expectedName, boolean expectedEnabled) {
        assertThat(providers.get(index).path("provider").asText()).isEqualTo(expectedName);
        assertThat(providers.get(index).path("enabled").asBoolean()).isEqualTo(expectedEnabled);
    }
}

package com.danasea.backend.configs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import com.danasea.backend.configs.properties.CheckinProperties;
import com.danasea.backend.configs.properties.CloudinaryProperties;
import com.danasea.backend.configs.properties.CorsProperties;
import com.danasea.backend.configs.properties.PayOSProperties;
import com.danasea.backend.configs.properties.PaymentProperties;
import com.danasea.backend.configs.properties.ZaloPayProperties;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.configs.CliProxyProperties;
import com.danasea.backend.modules.order.application.services.PaymentProviderAvailability;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.presentation.controllers.PaymentProviderController;
import com.danasea.backend.security.authentication.infrastructure.security.JwtProperties;

class ProductionConfigurationValidatorTest {
    private static final String KEY = "d9f317e42d8c1647ef793728e9be7118ca9d6ecb7c21914be7804e96b3587a6d";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "cloudinary.cloud-name=danasea",
                    "cloudinary.api-key=valid-cloud-key",
                    "cloudinary.api-secret=valid-cloud-secret",
                    "spring.mail.username=sender@danasea.test",
                    "spring.mail.password=valid-mail-password",
                    "ai.cliproxy.enabled=true",
                    "ai.cliproxy.base-url=http://cli-proxy-api:8317/v1",
                    "ai.cliproxy.api-key=proxy-" + KEY,
                    "ai.cliproxy.model=model", "ai.cliproxy.fallback-model=fallback",
                    "app.jwt.secret=" + KEY,
                    "app.jwt.access-token-minutes=15", "app.jwt.refresh-token-days=7",
                    "app.checkin.qr-secret=qr-" + KEY,
                    "app.payment.webhook-secret=webhook-" + KEY,
                    "app.cors.allowed-origins=https://danasea.tailfc3563.ts.net");

    @Test
    void acceptsConfiguredProductionSecrets() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void listsOnlyEstablishedGatewaysByDefault() {
        runner.run(context -> assertThat(context.getBean(PaymentProviderController.class).getAvailableProviders())
                .containsExactly(PaymentProvider.VNPAY, PaymentProvider.PAYPAL));
    }

    @Test
    void refusesSharedSigningSecrets() {
        runner.withPropertyValues("app.checkin.qr-secret=" + KEY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesWeakProxyClientSecret() {
        runner.withPropertyValues("ai.cliproxy.api-key=short")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @CsvSource({
            "app.jwt.secret,your_jwt_secret_key_minimum_256_bits",
            "app.checkin.qr-secret,danasea-secure-qr-checkin-hmac-sha256-default-key-32bytes-min!",
            "app.payment.webhook-secret,local-development-payment-webhook-secret-change-me",
            "ai.cliproxy.api-key,sk-cliproxy-pbl6-local-key"
    })
    void refusesKnownDevelopmentSecretsWithoutLeakingThem(String property, String value) {
        runner.withPropertyValues(property + "=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "Production configuration must not use a development secret: " + property);
            assertThat(context.getStartupFailure().getMessage()).doesNotContain(value);
        });
    }

    @ParameterizedTest
    @CsvSource({"http://localhost:5173", "https://*.example.com", "https://example.com/path", "http://example.com"})
    void refusesNonExactOrInsecureProductionOrigins(String origin) {
        runner.withPropertyValues("app.cors.allowed-origins=" + origin)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesAnEnabledPayOSGatewayWithoutCredentials() {
        runner.withPropertyValues("danasea.payment.payos.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @CsvSource({
            "danasea.payment.payos.return-url,http://payments.danasea.test/payment/success",
            "danasea.payment.payos.cancel-url,https://localhost/payment/cancel",
            "danasea.payment.zalopay.return-url,https://127.0.0.1/payment/success",
            "danasea.payment.zalopay.callback-url,http://payments.danasea.test/api/payments/webhook/zalopay",
            "danasea.payment.zalopay.callback-url,https://backend.internal./api/payments/webhook/zalopay",
            "danasea.payment.zalopay.callback-url,https://[::ffff:127.0.0.1]/api/payments/webhook/zalopay"
    })
    void refusesNonPublicOrInsecureGatewayUrls(String property, String value) {
        configuredPaymentGateways().withPropertyValues(property + "=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "Production payment URL must use a public HTTPS hostname: " + property);
        });
    }

    @Test
    void acceptsEnabledPaymentGatewaysWithCredentialsAndPublicUrls() {
        configuredPaymentGateways().run(context -> assertThat(context.getBean(PaymentProviderController.class)
                .getAvailableProviders()).containsExactly(
                        PaymentProvider.VNPAY, PaymentProvider.PAYPAL, PaymentProvider.PAYOS, PaymentProvider.ZALOPAY));
    }

    @Test
    void bindsMerchantSettingsAndBuildsProductionUrlsFromEnvironment() {
        String sourceName = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
        var environment = new SystemEnvironmentPropertySource(sourceName, Map.of(
                "APP_PAYMENT_PUBLIC_BASE_URL", "https://checkout.danasea.test",
                "PAYOS_ENABLED", "true",
                "PAYOS_CLIENT_ID", "merchant-client-id",
                "PAYOS_API_KEY", "merchant-api-key",
                "PAYOS_CHECKSUM_KEY", "merchant-checksum-key",
                "ZALOPAY_ENABLED", "true",
                "ZALOPAY_APP_ID", "50000",
                "ZALOPAY_KEY1", "merchant-key1",
                "ZALOPAY_KEY2", "merchant-key2"));
        runner.withInitializer(context -> context.getEnvironment().getPropertySources()
                        .replace(sourceName, environment))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var payOS = context.getBean(PayOSProperties.class);
                    assertThat(payOS.enabled()).isTrue();
                    assertThat(payOS.clientId()).isEqualTo("merchant-client-id");
                    assertThat(payOS.returnUrl()).isEqualTo("https://checkout.danasea.test/payment/success");
                    assertThat(payOS.cancelUrl()).isEqualTo("https://checkout.danasea.test/payment/cancel");
                    var zaloPay = context.getBean(ZaloPayProperties.class);
                    assertThat(zaloPay.enabled()).isTrue();
                    assertThat(zaloPay.appId()).isEqualTo(50000);
                    assertThat(zaloPay.endpoint()).isEqualTo("https://openapi.zalopay.vn/v2");
                    assertThat(zaloPay.callbackUrl()).isEqualTo("https://checkout.danasea.test/api/payments/webhook/zalopay");
                });
    }

    private ApplicationContextRunner configuredPaymentGateways() {
        return runner.withPropertyValues(
                "danasea.payment.payos.enabled=true",
                "danasea.payment.payos.client-id=payos-merchant",
                "danasea.payment.payos.api-key=payos-api-key",
                "danasea.payment.payos.checksum-key=payos-checksum-key",
                "danasea.payment.payos.return-url=https://payments.danasea.test/payment/success",
                "danasea.payment.payos.cancel-url=https://payments.danasea.test/payment/cancel",
                "danasea.payment.zalopay.enabled=true",
                "danasea.payment.zalopay.app-id=50000",
                "danasea.payment.zalopay.key1=zalopay-key1",
                "danasea.payment.zalopay.key2=zalopay-key2",
                "danasea.payment.zalopay.return-url=https://payments.danasea.test/payment/success",
                "danasea.payment.zalopay.callback-url=https://payments.danasea.test/api/payments/webhook/zalopay");
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ProductionConfigurationValidator.class, PaymentProviderAvailability.class, PaymentProviderController.class})
    @EnableConfigurationProperties({CloudinaryProperties.class, CorsProperties.class,
            JwtProperties.class, CheckinProperties.class, CliProxyProperties.class, MailProperties.class,
            PaymentProperties.class, PayOSProperties.class, ZaloPayProperties.class})
    static class PropertiesConfiguration {
    }
}

package com.danasea.backend.modules.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.danasea.backend.configs.properties.PaymentProperties;

class PaymentWebhookSignerTest {

    private static final String SECRET = "test-payment-webhook-secret-minimum-256-bits-long";
    private static final String PAYLOAD = "{\"paymentId\":\"123\",\"amount\":500000}";
    // Fixed HMAC-SHA256 vectors calculated independently with Python's hmac/hashlib.
    private static final String SIGNATURE = "883bb31d1623bbd44eafd46ad6e2c51ae3d896d97b435f1947844f4ec56c3d15";
    private final PaymentWebhookSigner signer = new PaymentWebhookSigner(new PaymentProperties(SECRET));

    @Test
    void signsAndVerifiesAnIndependentKnownAnswer() {
        assertThat(signer.sign(PAYLOAD)).isEqualTo(SIGNATURE);
        assertThat(signer.isValid(PAYLOAD, SIGNATURE)).isTrue();
    }

    @Test
    void signsUtf8PayloadWithoutLosingVietnameseTextOrEmoji() {
        String payload = "Hoàn tiền Đà Nẵng 🌊";
        String expected = "490183717bd254caef34fafd37d39f1269346351f023aae844e25427a222a30f";
        assertThat(signer.sign(payload)).isEqualTo(expected);
        assertThat(signer.isValid(payload, expected)).isTrue();
    }

    @Test
    void changingTheAmountInvalidatesTheOriginalSignature() {
        assertThat(signer.isValid("{\"paymentId\":\"123\",\"amount\":1}", SIGNATURE)).isFalse();
    }

    @Test
    void changingOnlyPayloadWhitespaceInvalidatesTheSignature() {
        assertThat(signer.isValid(PAYLOAD + " ", SIGNATURE)).isFalse();
    }

    @Test
    void aDifferentSecretCannotVerifyTheKnownSignature() {
        var differentSigner = new PaymentWebhookSigner(new PaymentProperties(
                "different-payment-webhook-secret-minimum-256-bits-long"));
        assertThat(differentSigner.isValid(PAYLOAD, SIGNATURE)).isFalse();
    }

    @Test
    void acceptsUppercaseHexAndSurroundingSignatureWhitespace() {
        assertThat(signer.isValid(PAYLOAD, " \t" + SIGNATURE.toUpperCase(Locale.ROOT) + "\n")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-hex-signature", "883bb31d", "g83bb31d1623bbd44eafd46ad6e2c51ae3d896d97b435f1947844f4ec56c3d15"})
    void rejectsMissingMalformedOrTruncatedSignatures(String signature) {
        assertThat(signer.isValid(PAYLOAD, signature)).isFalse();
    }

    @Test
    void rejectsANullPayloadEvenWithAnOtherwiseValidSignature() {
        assertThat(signer.isValid(null, SIGNATURE)).isFalse();
    }
}

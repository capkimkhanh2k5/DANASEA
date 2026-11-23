package com.danasea.backend.modules.checkin.domain.services;

import com.danasea.backend.modules.checkin.domain.exceptions.InvalidQrSignatureException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidQrTokenException;
import com.danasea.backend.modules.checkin.domain.models.QrTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("QrTokenSignerTest — Cryptographic Unit Tests for HMAC-SHA256 & SHA-256 Hashing")
class QrTokenSignerTest {

    private static final String TEST_SECRET = "danasea-secure-qr-checkin-hmac-sha256-default-key-32bytes-min!";
    private QrTokenSigner signer;

    @BeforeEach
    void setUp() {
        signer = new QrTokenSigner(TEST_SECRET);
    }

    @Test
    @DisplayName("1. Determinism: Same inputs produce identical HMAC-SHA256 signature and raw token")
    void testDeterministicTokenGeneration() {
        UUID subOrderId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        OffsetDateTime expiresAt = OffsetDateTime.of(2026, 9, 25, 18, 0, 0, 0, ZoneOffset.ofHours(7));

        String token1 = signer.generateToken(subOrderId, expiresAt);
        String token2 = signer.generateToken(subOrderId, expiresAt);

        assertThat(token1).isEqualTo(token2);
        assertThat(token1).contains(subOrderId.toString());
        assertThat(token1).contains(expiresAt.toString());
        assertThat(token1).contains(".");
    }

    @Test
    @DisplayName("2. Verification: Valid token signature verifies to true")
    void testVerifySignature_ValidToken_ReturnsTrue() {
        UUID subOrderId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);

        String token = signer.generateToken(subOrderId, expiresAt);

        assertThat(signer.verifySignature(token)).isTrue();
        assertThat(signer.verifyToken(token)).isTrue();
    }

    @Test
    @DisplayName("3. Tamper Detection: Changing single character in payload or signature fails verification")
    void testVerifySignature_TamperedToken_ReturnsFalse() {
        UUID subOrderId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        String token = signer.generateToken(subOrderId, expiresAt);

        // Case A: Sửa đổi payload
        char changedPayloadChar = token.charAt(0) == 'a' ? 'b' : 'a';
        String tamperedPayload = changedPayloadChar + token.substring(1);
        assertThat(signer.verifySignature(tamperedPayload)).isFalse();

        // Case B: Sửa đổi signature byte
        int signatureStart = token.lastIndexOf('.') + 1;
        char firstSignatureChar = token.charAt(signatureStart);
        char replacedChar = firstSignatureChar == 'A' ? 'B' : 'A';
        String tamperedSig = token.substring(0, signatureStart)
                + replacedChar
                + token.substring(signatureStart + 1);
        assertThat(signer.verifySignature(tamperedSig)).isFalse();
    }

    @Test
    @DisplayName("4. Malformed Input: Null, blank or invalid token returns false in verification")
    void testVerifySignature_MalformedOrBlank_ReturnsFalse() {
        assertThat(signer.verifySignature(null)).isFalse();
        assertThat(signer.verifySignature("")).isFalse();
        assertThat(signer.verifySignature("   ")).isFalse();
        assertThat(signer.verifySignature("no-dots-in-this-token")).isFalse();
        assertThat(signer.verifySignature(".starts-with-dot")).isFalse();
        assertThat(signer.verifySignature("ends-with-dot.")).isFalse();
    }

    @Test
    @DisplayName("5. Parse Payload: Valid token parses into exact subOrderId and expiresAt")
    void testParsePayload_Success() {
        UUID subOrderId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(3);
        String token = signer.generateToken(subOrderId, expiresAt);

        QrTokenPayload payload = signer.parsePayload(token);

        assertThat(payload).isNotNull();
        assertThat(payload.getSubOrderId()).isEqualTo(subOrderId);
        assertThat(payload.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("6. Parse Payload: Tampered token throws InvalidQrSignatureException")
    void testParsePayload_Tampered_ThrowsInvalidQrSignatureException() {
        UUID subOrderId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(3);
        String token = signer.generateToken(subOrderId, expiresAt);
        char lastChar = token.charAt(token.length() - 1);
        char tamperedChar = lastChar == 'f' ? '0' : 'f';
        String tampered = token.substring(0, token.length() - 1) + tamperedChar;

        assertThatThrownBy(() -> signer.parsePayload(tampered))
                .isInstanceOf(InvalidQrSignatureException.class);
    }

    @Test
    @DisplayName("7. Parse Payload: Malformed token structure throws InvalidQrTokenException")
    void testParsePayload_Malformed_ThrowsInvalidQrTokenException() {
        assertThatThrownBy(() -> signer.parsePayload("malformed-token-without-separator"))
                .isInstanceOf(InvalidQrTokenException.class);

        assertThatThrownBy(() -> signer.parsePayload(null))
                .isInstanceOf(InvalidQrTokenException.class);
    }

    @Test
    @DisplayName("8. SHA-256 Hashing: Produces exactly 64 lowercase hex characters")
    void testHashToken_Produces64HexChars() {
        String rawToken = "550e8400-e29b-41d4-a716-446655440000:2026-09-25T18:00:00Z.abcd1234ef";
        String hash = QrTokenSigner.hashToken(rawToken);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[0-9a-f]{64}$");
        assertThat(hash).isNotEqualTo(rawToken);

        // Different tokens must have different hashes (Collision Resistance)
        String otherToken = "550e8400-e29b-41d4-a716-446655440001:2026-09-25T18:00:00Z.abcd1234ef";
        String otherHash = QrTokenSigner.hashToken(otherToken);
        assertThat(hash).isNotEqualTo(otherHash);
    }
}

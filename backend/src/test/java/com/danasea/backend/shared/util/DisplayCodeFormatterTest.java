package com.danasea.backend.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DisplayCodeFormatterTest {

    @Test
    void formatsDeterministicSeedUuidsUsingHeadAndTail() {
        assertThat(DisplayCodeFormatter.format("00010000-0000-0000-0000-000000000042", "ORD"))
                .isEqualTo("ORD-00010042");
        assertThat(DisplayCodeFormatter.format("00020000-0000-0000-0000-000000000015", "SUB"))
                .isEqualTo("SUB-00020015");
        assertThat(DisplayCodeFormatter.format("b0010000-0000-0000-0000-000000000001", "ORD"))
                .isEqualTo("ORD-B0010001");
        assertThat(DisplayCodeFormatter.format("b0020000-0000-0000-0000-000000000009", "SUB"))
                .isEqualTo("SUB-B0020009");
        assertThat(DisplayCodeFormatter.format("77777777-7777-7777-7777-777777777704", "DISP"))
                .isEqualTo("DISP-77777704");
        assertThat(DisplayCodeFormatter.format("99999999-9999-9999-9999-999999999992", "SUB"))
                .isEqualTo("SUB-99999992");
    }

    @Test
    void formatsRandomUuidV4UsingFirstEightHexCharsAndHandlesEdgeInputs() {
        UUID random = UUID.fromString("7a2f1c4d-9876-4abc-8def-0123456789ab");
        assertThat(DisplayCodeFormatter.format(random, "ORD")).isEqualTo("ORD-7A2F1C4D");
        assertThat(DisplayCodeFormatter.format("ord-7a2f1c4d", "ORD")).isEqualTo("ORD-7A2F1C4D");
        assertThat(DisplayCodeFormatter.format((UUID) null, "ORD")).isEmpty();
        assertThat(DisplayCodeFormatter.format((String) null, "ORD")).isEmpty();
        assertThat(DisplayCodeFormatter.format("   ", "ORD")).isEmpty();
        assertThat(DisplayCodeFormatter.format("abc12", null)).isEqualTo("ORD-ABC12");
        assertThat(DisplayCodeFormatter.format("abc12", "  ")).isEqualTo("ORD-ABC12");
        assertThat(DisplayCodeFormatter.formatWithHash(null, "ORD")).isEmpty();
        assertThat(DisplayCodeFormatter.formatWithHash(random, "ORD")).isEqualTo("#ORD-7A2F1C4D");
    }

    @Test
    void normalizesDetectsSeedHexAndReconstructsOnlyKnownSeedHeads() {
        assertThat(DisplayCodeFormatter.normalizeCode(null)).isEmpty();
        assertThat(DisplayCodeFormatter.normalizeCode("  #ord-7a2f1c4d  ")).isEqualTo("ORD-7A2F1C4D");
        assertThat(DisplayCodeFormatter.normalizeCode("sub-ec938b9f")).isEqualTo("SUB-EC938B9F");

        assertThat(DisplayCodeFormatter.isSeedHex(null)).isFalse();
        assertThat(DisplayCodeFormatter.isSeedHex("1234")).isFalse();
        assertThat(DisplayCodeFormatter.isSeedHex("7a2f1c4d9876")).isFalse();
        assertThat(DisplayCodeFormatter.isSeedHex("666666666666")).isTrue();

        assertThat(DisplayCodeFormatter.tryReconstructSeedUuid(null)).isEmpty();
        assertThat(DisplayCodeFormatter.tryReconstructSeedUuid("INVALID")).isEmpty();
        assertThat(DisplayCodeFormatter.tryReconstructSeedUuid("#ORD-00010042"))
                .contains(UUID.fromString("00010000-0000-0000-0000-000000000042"));
        assertThat(DisplayCodeFormatter.tryReconstructSeedUuid("#DISP-77777704"))
                .contains(UUID.fromString("77777777-7777-7777-7777-777777777704"));
        assertThat(DisplayCodeFormatter.tryReconstructSeedUuid("#ORD-7A2F1C4D")).isEmpty();
        assertThat(DisplayCodeFormatter.tryReconstructSeedUuid("#SUB-EC938B9F")).isEmpty();
    }
}

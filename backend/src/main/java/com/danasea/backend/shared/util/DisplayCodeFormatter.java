package com.danasea.backend.shared.util;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single source of truth for DANASEA standardized entity display codes
 * (e.g. {@code ORD-00010042}, {@code SUB-7A2F1C4D}, {@code DISP-77777704}).
 * Mirrors Web {@code displayCode.ts} and Mobile {@code display_code.dart}.
 */
public final class DisplayCodeFormatter {

    private static final Pattern ALREADY_PREFIXED = Pattern.compile("^[A-Z]{3,6}-");
    private static final Pattern SEED_CODE = Pattern.compile(
            "^(?:ORD|SUB|DNS|DISP|PAY|STL|VND|DOC|LOG|CUST|REF|SRV|SLOT|REV)-([0-9A-F]{4})([0-9A-F]{4})(?:-\\d+)?$");

    private static final List<String> ZERO_PAD_SEED_PREFIXES = List.of(
            "00010000", "00020000", "00040000", "00050000", "00060000",
            "00080000", "00090000", "b0010000", "b0020000", "00000000"
    );

    private static final Set<String> ZERO_PAD_SEED_HEADS = Set.of(
            "0001", "0002", "0004", "0005", "0006", "0008", "0009", "b001", "b002", "0000"
    );

    private static final Set<String> REPEAT_SEED_HEADS = Set.of(
            "6666", "7777", "9999"
    );

    private DisplayCodeFormatter() {
    }

    public static String format(UUID id, String prefix) {
        return id == null ? "" : format(id.toString(), prefix);
    }

    public static String format(String rawId, String prefix) {
        if (rawId == null || rawId.isBlank()) {
            return "";
        }
        String trimmed = rawId.trim();
        if (ALREADY_PREFIXED.matcher(trimmed.toUpperCase(Locale.ROOT)).find()) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        String effectivePrefix = (prefix == null || prefix.isBlank()) ? "ORD" : prefix.trim().toUpperCase(Locale.ROOT);
        String clean = trimmed.replace("-", "").toLowerCase(Locale.ROOT);
        if (clean.length() < 8) {
            return effectivePrefix + "-" + clean.toUpperCase(Locale.ROOT);
        }
        String code = isSeedHex(clean)
                ? clean.substring(0, 4) + clean.substring(clean.length() - 4)
                : clean.substring(0, 8);
        return effectivePrefix + "-" + code.toUpperCase(Locale.ROOT);
    }

    public static String formatWithHash(UUID id, String prefix) {
        String code = format(id, prefix);
        return code.isEmpty() ? "" : "#" + code;
    }

    public static String normalizeCode(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned.toUpperCase(Locale.ROOT);
    }

    public static boolean isSeedHex(String cleanHex) {
        if (cleanHex == null || cleanHex.length() < 8) {
            return false;
        }
        String lower = cleanHex.replace("-", "").toLowerCase(Locale.ROOT);
        for (String prefix : ZERO_PAD_SEED_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        return lower.substring(0, 8).chars().distinct().count() <= 2;
    }

    /**
     * Reconstructs a full 36-char seed UUID ONLY when the 4-char head matches known
     * deterministic seed prefixes ({@code 0001}, {@code b001}, {@code 7777}, ...).
     * Never fabricates fake UUIDs for random UUIDv4 display codes.
     */
    public static Optional<UUID> tryReconstructSeedUuid(String rawCode) {
        String normalized = normalizeCode(rawCode);
        Matcher matcher = SEED_CODE.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String head = matcher.group(1).toLowerCase(Locale.ROOT);
        String tail = matcher.group(2).toLowerCase(Locale.ROOT);
        if (ZERO_PAD_SEED_HEADS.contains(head)) {
            return Optional.of(UUID.fromString(head + "0000-0000-0000-0000-00000000" + tail));
        }
        if (REPEAT_SEED_HEADS.contains(head)) {
            char d = head.charAt(0);
            String block8 = String.valueOf(d).repeat(8);
            String block4 = String.valueOf(d).repeat(4);
            return Optional.of(UUID.fromString(block8 + "-" + block4 + "-" + block4 + "-" + block4 + "-" + block8 + tail));
        }
        return Optional.empty();
    }
}

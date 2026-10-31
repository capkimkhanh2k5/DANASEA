package com.danasea.backend.shared.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class SlugUtils {

    private static final Pattern DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEADING_TRAILING_HYPHENS = Pattern.compile("^-+|-+$");

    private SlugUtils() {
    }

    /**
     * Converts a Vietnamese or international text string to an SEO-friendly slug.
     * Example: "Tour Lặn Biển Ngắm San Hô - Cù Lao Chàm" -> "tour-lan-bien-ngam-san-ho-cu-lao-cham"
     */
    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "service";
        }

        // Replace Vietnamese 'đ' / 'Đ' which are not decomposed by Normalizer
        String normalized = input.trim()
                .replace("đ", "d")
                .replace("Đ", "d");

        // Decompose unicode diacritics
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = DIACRITICAL_MARKS.matcher(normalized).replaceAll("");

        // Lowercase and replace non-alphanumeric with hyphen
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll("-");

        // Remove leading and trailing hyphens
        normalized = LEADING_TRAILING_HYPHENS.matcher(normalized).replaceAll("");

        return normalized.isBlank() ? "service" : normalized;
    }

    /**
     * Resolves a unique slug using a predicate to test existing slugs.
     */
    public static String resolveUniqueSlug(String baseName, Predicate<String> existsPredicate) {
        String baseSlug = toSlug(baseName);
        if (!existsPredicate.test(baseSlug)) {
            return baseSlug;
        }

        int suffix = 2;
        while (true) {
            String candidate = baseSlug + "-" + suffix;
            if (!existsPredicate.test(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }
}

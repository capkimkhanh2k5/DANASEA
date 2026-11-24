package com.danasea.backend.security.authentication.infrastructure.security;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.HttpServletResponse;

public final class CookieUtils {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String REFRESH_TOKEN_PATH = "/api/auth";

    private CookieUtils() {
    }

    public static void addRefreshTokenCookie(
            HttpServletResponse response,
            String refreshToken,
            long maxAgeSeconds) {
        addRefreshTokenCookie(response, refreshToken, maxAgeSeconds, true);
    }

    public static void addRefreshTokenCookie(
            HttpServletResponse response,
            String refreshToken,
            long maxAgeSeconds,
            boolean secure) {
        addCookie(response, refreshToken, Duration.ofSeconds(maxAgeSeconds), secure);
    }

    public static void clearRefreshTokenCookie(HttpServletResponse response) {
        clearRefreshTokenCookie(response, true);
    }

    public static void clearRefreshTokenCookie(HttpServletResponse response, boolean secure) {
        addCookie(response, "", Duration.ZERO, secure);
    }

    private static void addCookie(HttpServletResponse response, String value, Duration maxAge, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_PATH)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

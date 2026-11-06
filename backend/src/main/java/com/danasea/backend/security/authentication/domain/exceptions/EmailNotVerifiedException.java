package com.danasea.backend.security.authentication.domain.exceptions;

public class EmailNotVerifiedException extends InvalidCredentialsException {
    private final String email;
    private final String accessToken;

    public EmailNotVerifiedException(String email, String accessToken) {
        super("Email is not verified");
        this.email = email;
        this.accessToken = accessToken;
    }

    public EmailNotVerifiedException(String email) {
        this(email, null);
    }

    public String getEmail() {
        return email;
    }

    public String getAccessToken() {
        return accessToken;
    }
}

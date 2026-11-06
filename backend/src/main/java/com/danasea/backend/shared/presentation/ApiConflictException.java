package com.danasea.backend.shared.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ApiConflictException extends ResponseStatusException {
    private final String code;

    public ApiConflictException(String code, String reason) {
        super(HttpStatus.CONFLICT, reason);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

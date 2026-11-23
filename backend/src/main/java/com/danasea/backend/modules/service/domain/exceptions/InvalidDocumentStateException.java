package com.danasea.backend.modules.service.domain.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidDocumentStateException extends RuntimeException {
    public InvalidDocumentStateException(String message) {
        super(message);
    }
}

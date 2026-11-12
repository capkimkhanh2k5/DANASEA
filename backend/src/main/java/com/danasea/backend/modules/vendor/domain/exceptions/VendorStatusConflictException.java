package com.danasea.backend.modules.vendor.domain.exceptions;

public class VendorStatusConflictException extends RuntimeException {

    private final String errorCode;

    public VendorStatusConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

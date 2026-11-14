package com.danasea.backend.modules.vendor.presentation.handlers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.danasea.backend.modules.vendor.domain.exceptions.InvalidDocTypeException;
import com.danasea.backend.modules.vendor.domain.exceptions.UserLockedException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorAlreadyExistsException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorDocumentsIncompleteException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorProfileIncompleteException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorStatusConflictException;
import com.danasea.backend.modules.service.domain.exceptions.InvalidFileTypeException;
import com.danasea.backend.shared.presentation.ErrorResponse;
import com.danasea.backend.shared.presentation.LocalizedExceptionHandlerSupport;

@RestControllerAdvice(basePackages = "com.danasea.backend.modules.vendor")
public class VendorExceptionHandler extends LocalizedExceptionHandlerSupport {

    @ExceptionHandler(VendorAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleVendorAlreadyExists(VendorAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error("VENDOR_ALREADY_EXISTS"));
    }

    @ExceptionHandler(VendorStatusConflictException.class)
    public ResponseEntity<ErrorResponse> handleVendorStatusConflict(VendorStatusConflictException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error(ex.getErrorCode() != null ? ex.getErrorCode() : "VENDOR_STATUS_CONFLICT"));
    }

    @ExceptionHandler(VendorNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVendorNotFound(VendorNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error("VENDOR_NOT_FOUND"));
    }

    @ExceptionHandler(VendorProfileIncompleteException.class)
    public ResponseEntity<ErrorResponse> handleVendorProfileIncomplete(VendorProfileIncompleteException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error("PROFILE_INCOMPLETE"));
    }

    @ExceptionHandler(VendorDocumentsIncompleteException.class)
    public ResponseEntity<ErrorResponse> handleVendorDocumentsIncomplete(VendorDocumentsIncompleteException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error("DOCUMENTS_INCOMPLETE"));
    }

    @ExceptionHandler(UserLockedException.class)
    public ResponseEntity<ErrorResponse> handleUserLocked(UserLockedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(error("USER_LOCKED"));
    }

    @ExceptionHandler(InvalidDocTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDocType(InvalidDocTypeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_DOC_TYPE"));
    }

    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileType(InvalidFileTypeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_FILE_TYPE"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(validationError(ex));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_INPUT"));
    }
}

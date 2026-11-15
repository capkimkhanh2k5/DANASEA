package com.danasea.backend.modules.partner.presentation.handlers;

import com.danasea.backend.modules.partner.domain.exceptions.ApplicationNotFoundException;
import com.danasea.backend.modules.partner.domain.exceptions.ApplicationStatusConflictException;
import com.danasea.backend.modules.partner.domain.exceptions.CustomerHasActiveObligationsException;
import com.danasea.backend.shared.presentation.ErrorResponse;
import com.danasea.backend.shared.presentation.LocalizedExceptionHandlerSupport;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.danasea.backend.modules.partner")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PartnerExceptionHandler extends LocalizedExceptionHandlerSupport {

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ApplicationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("APPLICATION_NOT_FOUND"));
    }

    @ExceptionHandler(ApplicationStatusConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ApplicationStatusConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("APPLICATION_STATUS_CONFLICT"));
    }

    @ExceptionHandler(CustomerHasActiveObligationsException.class)
    public ResponseEntity<ErrorResponse> handleActiveObligations(CustomerHasActiveObligationsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("CUSTOMER_HAS_ACTIVE_OBLIGATIONS"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("APPLICATION_STATUS_CONFLICT"));
    }
}

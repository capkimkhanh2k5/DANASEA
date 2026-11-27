package com.danasea.backend.modules.dispute.presentation.handlers;

import com.danasea.backend.modules.dispute.domain.exceptions.DisputeAlreadyResolvedException;
import com.danasea.backend.modules.dispute.domain.exceptions.DisputeNotFoundException;
import com.danasea.backend.modules.dispute.domain.exceptions.DisputePeriodExpiredException;
import com.danasea.backend.modules.dispute.domain.exceptions.DuplicateDisputeException;
import com.danasea.backend.modules.dispute.domain.exceptions.InvalidDisputeResolutionException;
import com.danasea.backend.modules.dispute.domain.exceptions.InvalidSubOrderStateException;
import com.danasea.backend.modules.dispute.domain.exceptions.UnauthorizedDisputeAccessException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.InvalidFileTypeException;
import com.danasea.backend.shared.presentation.ErrorResponse;
import com.danasea.backend.shared.presentation.LocalizedExceptionHandlerSupport;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.danasea.backend.modules.dispute")
public class DisputeExceptionHandler extends LocalizedExceptionHandlerSupport {

    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileType(InvalidFileTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_FILE_TYPE"));
    }

    @ExceptionHandler(DisputeAlreadyResolvedException.class)
    public ResponseEntity<ErrorResponse> handleDisputeAlreadyResolved(DisputeAlreadyResolvedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("DISPUTE_ALREADY_RESOLVED"));
    }

    @ExceptionHandler(DuplicateDisputeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateDispute(DuplicateDisputeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("ACTIVE_DISPUTE_ALREADY_EXISTS"));
    }

    @ExceptionHandler(DisputeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDisputeNotFound(DisputeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("DISPUTE_NOT_FOUND"));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("ORDER_NOT_FOUND"));
    }

    @ExceptionHandler(UnauthorizedDisputeAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedDisputeAccess(UnauthorizedDisputeAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("ACCESS_DENIED"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("ACCESS_DENIED"));
    }

    @ExceptionHandler(InvalidSubOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSubOrderState(InvalidSubOrderStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_SUB_ORDER_STATE"));
    }

    @ExceptionHandler(DisputePeriodExpiredException.class)
    public ResponseEntity<ErrorResponse> handleDisputePeriodExpired(DisputePeriodExpiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("DISPUTE_PERIOD_EXPIRED"));
    }

    @ExceptionHandler(InvalidDisputeResolutionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDisputeResolution(InvalidDisputeResolutionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_DISPUTE_RESOLUTION"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_ARGUMENT"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(validationError(ex));
    }
}

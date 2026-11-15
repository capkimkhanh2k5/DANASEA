package com.danasea.backend.modules.checkin.presentation.handlers;

import com.danasea.backend.modules.checkin.domain.exceptions.AmbiguousCheckinTargetException;
import com.danasea.backend.modules.checkin.domain.exceptions.CheckinAlreadyUsedException;
import com.danasea.backend.modules.checkin.domain.exceptions.CheckinTokenNotFoundException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidQrSignatureException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidQrTokenException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidSubOrderStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.QrTokenExpiredException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedCheckinAccessException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedVendorCheckinException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.danasea.backend.modules.checkin")
public class CheckinExceptionHandler extends LocalizedExceptionHandlerSupport {

    public record CheckinAlreadyUsedErrorResponse(
            String code,
            String message,
            OffsetDateTime usedAt,
            UUID usedByVendorStaffId
    ) {}

    public record AmbiguousCheckinResponse(
            String code,
            String message,
            UUID masterOrderId,
            java.util.List<com.danasea.backend.modules.checkin.presentation.dtos.EligibleSubOrderCandidate> eligibleSubOrders
    ) {}

    @ExceptionHandler(InvalidQrSignatureException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQrSignature(InvalidQrSignatureException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_QR_SIGNATURE"));
    }

    @ExceptionHandler(InvalidQrTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQrToken(InvalidQrTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_QR_TOKEN"));
    }

    @ExceptionHandler(InvalidSubOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSubOrderState(InvalidSubOrderStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_SUB_ORDER_STATE"));
    }

    @ExceptionHandler(InvalidCheckinStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCheckinState(InvalidCheckinStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("SLOT_ALREADY_CONCLUDED"));
    }

    @ExceptionHandler(AmbiguousCheckinTargetException.class)
    public ResponseEntity<AmbiguousCheckinResponse> handleAmbiguousCheckinTarget(AmbiguousCheckinTargetException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AmbiguousCheckinResponse(
                        "AMBIGUOUS_MASTER_QR_CHECKIN",
                        ex.getMessage() != null && !ex.getMessage().isBlank()
                                ? ex.getMessage()
                                : message("error.ambiguous_master_qr_checkin"),
                        ex.getMasterOrderId(),
                        ex.getCandidates()
                ));
    }

    @ExceptionHandler(UnauthorizedCheckinAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedCheckinAccess(UnauthorizedCheckinAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("ACCESS_DENIED"));
    }

    @ExceptionHandler(UnauthorizedVendorCheckinException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedVendorCheckin(UnauthorizedVendorCheckinException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("VENDOR_MISMATCH"));
    }

    @ExceptionHandler(CheckinAlreadyUsedException.class)
    public ResponseEntity<CheckinAlreadyUsedErrorResponse> handleCheckinAlreadyUsed(CheckinAlreadyUsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new CheckinAlreadyUsedErrorResponse(
                        "QR_TOKEN_ALREADY_USED",
                        message("error.qr_token_already_used"),
                        ex.getUsedAt(),
                        ex.getUsedByVendorStaffId()
                ));
    }

    @ExceptionHandler(QrTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleQrTokenExpired(QrTokenExpiredException ex) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(error("QR_TOKEN_EXPIRED"));
    }

    @ExceptionHandler(CheckinTokenNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCheckinTokenNotFound(CheckinTokenNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("CHECKIN_TOKEN_NOT_FOUND"));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("ORDER_NOT_FOUND"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("ACCESS_DENIED"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(validationError(ex));
    }
}

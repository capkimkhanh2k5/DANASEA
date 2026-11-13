package com.danasea.backend.modules.settlement.presentation.handlers;

import com.danasea.backend.modules.settlement.domain.exceptions.InvalidCommissionRateException;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidOrderAmountException;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidSettlementPeriodException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyFinalizedException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyPaidException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFoundException;
import com.danasea.backend.modules.settlement.domain.exceptions.UnauthorizedSettlementAccessException;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.shared.presentation.ErrorResponse;
import com.danasea.backend.shared.presentation.LocalizedExceptionHandlerSupport;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.danasea.backend.modules.settlement")
public class SettlementExceptionHandler extends LocalizedExceptionHandlerSupport {

    @ExceptionHandler(SettlementNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSettlementNotFound(SettlementNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("SETTLEMENT_NOT_FOUND"));
    }

    @ExceptionHandler(SettlementAlreadyFinalizedException.class)
    public ResponseEntity<ErrorResponse> handleSettlementAlreadyFinalized(SettlementAlreadyFinalizedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("SETTLEMENT_ALREADY_FINALIZED"));
    }

    @ExceptionHandler(SettlementAlreadyPaidException.class)
    public ResponseEntity<ErrorResponse> handleSettlementAlreadyPaid(SettlementAlreadyPaidException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("SETTLEMENT_ALREADY_PAID"));
    }

    @ExceptionHandler(VendorNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVendorNotFound(VendorNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("VENDOR_NOT_FOUND"));
    }

    @ExceptionHandler(InvalidSettlementPeriodException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSettlementPeriod(InvalidSettlementPeriodException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_SETTLEMENT_PERIOD"));
    }

    @ExceptionHandler(InvalidCommissionRateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCommissionRate(InvalidCommissionRateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_COMMISSION_RATE"));
    }

    @ExceptionHandler(InvalidOrderAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderAmount(InvalidOrderAmountException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_ORDER_AMOUNT"));
    }

    @ExceptionHandler(UnauthorizedSettlementAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedSettlementAccess(UnauthorizedSettlementAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("FORBIDDEN_SETTLEMENT_ACCESS"));
    }

    @ExceptionHandler(com.danasea.backend.modules.settlement.domain.exceptions.PayoutRequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePayoutRequestNotFound(com.danasea.backend.modules.settlement.domain.exceptions.PayoutRequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("PAYOUT_REQUEST_NOT_FOUND"));
    }

    @ExceptionHandler(com.danasea.backend.modules.settlement.domain.exceptions.PayoutAmountExceedsBalanceException.class)
    public ResponseEntity<ErrorResponse> handlePayoutAmountExceedsBalance(com.danasea.backend.modules.settlement.domain.exceptions.PayoutAmountExceedsBalanceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("PAYOUT_AMOUNT_EXCEEDS_BALANCE"));
    }

    @ExceptionHandler(com.danasea.backend.modules.settlement.domain.exceptions.InvalidPayoutRequestStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPayoutRequestState(com.danasea.backend.modules.settlement.domain.exceptions.InvalidPayoutRequestStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_PAYOUT_REQUEST_STATE"));
    }

    @ExceptionHandler(com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFinalizedException.class)
    public ResponseEntity<ErrorResponse> handleSettlementNotFinalized(com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFinalizedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("SETTLEMENT_NOT_FINALIZED"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(validationError(ex));
    }
}

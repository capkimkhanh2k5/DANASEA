package com.danasea.backend.modules.order.presentation.handlers;

import com.danasea.backend.modules.order.domain.exceptions.BookingNotEligibleForOrderException;
import com.danasea.backend.modules.order.domain.exceptions.InvalidDiscountException;
import com.danasea.backend.modules.order.domain.exceptions.InvalidOrderStateException;
import com.danasea.backend.modules.order.domain.exceptions.InvalidWebhookException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentAttemptConflictException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentGatewayException;
import com.danasea.backend.modules.order.domain.exceptions.PaymentVerificationException;
import com.danasea.backend.modules.order.domain.exceptions.RefundNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.order.domain.exceptions.UnpaidOrderReceiptException;
import com.danasea.backend.modules.order.domain.exceptions.WaiverAcceptanceRequiredException;
import com.danasea.backend.modules.order.domain.exceptions.WaiverVersionMismatchException;
import com.danasea.backend.modules.order.presentation.dtos.MissingWaiverSubOrderResponse;
import com.danasea.backend.modules.order.presentation.dtos.WaiverAcceptanceRequiredResponse;
import com.danasea.backend.shared.presentation.ErrorResponse;
import com.danasea.backend.shared.presentation.LocalizedExceptionHandlerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderExceptionHandler extends LocalizedExceptionHandlerSupport {

    @ExceptionHandler(UnpaidOrderReceiptException.class)
    public ResponseEntity<ErrorResponse> handleUnpaidOrderReceipt(UnpaidOrderReceiptException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("ORDER_NOT_PAID"));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("ORDER_NOT_FOUND"));
    }

    @ExceptionHandler(RefundNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRefundNotFound(RefundNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("REFUND_NOT_FOUND"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("ACCESS_DENIED"));
    }

    @ExceptionHandler(UnauthorizedOrderAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedOrderAccess(UnauthorizedOrderAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("UNAUTHORIZED_ORDER_ACCESS"));
    }

    @ExceptionHandler(BookingNotEligibleForOrderException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotEligible(BookingNotEligibleForOrderException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("BOOKING_NOT_ELIGIBLE_FOR_ORDER"));
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderState(InvalidOrderStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("INVALID_ORDER_STATE"));
    }

    @ExceptionHandler(PaymentAttemptConflictException.class)
    public ResponseEntity<ErrorResponse> handlePaymentAttemptConflict(PaymentAttemptConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(ex.getCode()));
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ErrorResponse> handlePaymentGateway(PaymentGatewayException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error("PAYMENT_GATEWAY_UNAVAILABLE"));
    }

    @ExceptionHandler(PaymentVerificationException.class)
    public ResponseEntity<ErrorResponse> handlePaymentVerification(PaymentVerificationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("PAYMENT_SIGNATURE_INVALID"));
    }

    @ExceptionHandler(InvalidWebhookException.class)
    public ResponseEntity<ErrorResponse> handleInvalidWebhook(InvalidWebhookException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_WEBHOOK"));
    }

    @ExceptionHandler(InvalidDiscountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDiscount(InvalidDiscountException ex) {
        String code = ex.getErrorCode() != null ? ex.getErrorCode() : "INVALID_DISCOUNT";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(code));
    }

    @ExceptionHandler(WaiverVersionMismatchException.class)
    public ResponseEntity<ErrorResponse> handleWaiverVersionMismatch(WaiverVersionMismatchException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("WAIVER_VERSION_MISMATCH"));
    }

    @ExceptionHandler(WaiverAcceptanceRequiredException.class)
    public ResponseEntity<WaiverAcceptanceRequiredResponse> handleWaiverAcceptanceRequired(
            WaiverAcceptanceRequiredException ex) {
        String msg = message("error.waiver_acceptance_required");
        var items = ex.getMissingSubOrders().stream()
                .map(m -> new MissingWaiverSubOrderResponse(
                        m.subOrderId(),
                        m.serviceId(),
                        m.serviceName(),
                        m.waiverVersion(),
                        m.required(),
                        m.waiverContent(),
                        m.contentLanguage(),
                        m.fallbackUsed()
                ))
                .toList();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new WaiverAcceptanceRequiredResponse(
                        WaiverAcceptanceRequiredResponse.ERROR_CODE,
                        msg,
                        ex.getOrderId(),
                        items
                ));
    }
}

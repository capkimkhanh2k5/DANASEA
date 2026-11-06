package com.danasea.backend.security.authentication.presentation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.danasea.backend.security.authentication.domain.exceptions.EmailAlreadyUsedException;
import com.danasea.backend.security.authentication.domain.exceptions.EmailNotVerifiedException;
import com.danasea.backend.security.authentication.presentation.dtos.EmailNotVerifiedResponse;
import com.danasea.backend.security.authentication.domain.exceptions.OtpExpiredException;
import com.danasea.backend.security.authentication.domain.exceptions.OtpInvalidException;
import com.danasea.backend.security.authentication.domain.exceptions.OtpMaxAttemptsExceededException;
import com.danasea.backend.security.authentication.domain.exceptions.OtpRequestTooFrequentException;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.danasea.backend.shared.presentation.ErrorResponse;

@RestControllerAdvice(basePackageClasses = AuthenticationController.class)
public class AuthenticationExceptionHandler {

        private LocalizedMessageService messages = LocalizedMessageService.standalone();

        @Autowired(required = false)
        void setLocalizedMessageService(LocalizedMessageService messages) {
                this.messages = messages;
        }

        @ExceptionHandler(EmailAlreadyUsedException.class)
        public ResponseEntity<ErrorResponse> handleEmailAlreadyUsed(
                        EmailAlreadyUsedException exception) {
                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(new ErrorResponse(
                                                "EMAIL_ALREADY_USED",
                                                messages.get("error.email_already_used")));
        }

        @ExceptionHandler(EmailNotVerifiedException.class)
        public ResponseEntity<EmailNotVerifiedResponse> handleEmailNotVerified(
                        EmailNotVerifiedException exception) {
                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(new EmailNotVerifiedResponse(
                                                "EMAIL_NOT_VERIFIED",
                                                messages.get("error.email_not_verified"),
                                                exception.getEmail(),
                                                exception.getAccessToken()));
        }

        @ExceptionHandler({OtpInvalidException.class, OtpExpiredException.class, OtpMaxAttemptsExceededException.class})
        public ResponseEntity<ErrorResponse> handleOtpVerificationExceptions(Exception exception) {
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(new ErrorResponse(
                                                "INVALID_OTP",
                                                messages.get("error.invalid_otp")));
        }

        @ExceptionHandler(OtpRequestTooFrequentException.class)
        public ResponseEntity<ErrorResponse> handleOtpTooFrequent(OtpRequestTooFrequentException exception) {
                return ResponseEntity
                                .status(HttpStatus.TOO_MANY_REQUESTS)
                                .body(new ErrorResponse(
                                                "OTP_REQUEST_TOO_FREQUENT",
                                                messages.get("error.otp_request_too_frequent")));
        }
}

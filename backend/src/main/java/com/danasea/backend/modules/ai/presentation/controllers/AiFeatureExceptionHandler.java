package com.danasea.backend.modules.ai.presentation.controllers;

import org.springframework.core.Ordered;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.danasea.backend.modules.ai.domain.exceptions.AiResourceNotFoundException;
import com.danasea.backend.modules.ai.domain.exceptions.AiStateConflictException;
import com.danasea.backend.modules.ai.infrastructure.cliproxy.CliProxyException;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.exceptions.UnauthorizedOrderAccessException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.shared.presentation.ErrorResponse;
import com.danasea.backend.shared.presentation.LocalizedExceptionHandlerSupport;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = CustomerAiController.class)
public class AiFeatureExceptionHandler extends LocalizedExceptionHandlerSupport {
    @ExceptionHandler(CliProxyException.class)
    public ResponseEntity<ErrorResponse> unavailable(CliProxyException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("AI_SERVICE_UNAVAILABLE", message("ai.unavailable")));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(error("AI_INVALID_INPUT"));
    }

    @ExceptionHandler({AccessDeniedException.class, UnauthorizedOrderAccessException.class})
    public ResponseEntity<ErrorResponse> denied(Exception exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("AI_ACCESS_DENIED"));
    }

    @ExceptionHandler({OrderNotFoundException.class, ServiceNotFoundException.class, AiResourceNotFoundException.class})
    public ResponseEntity<ErrorResponse> missing(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("AI_SOURCE_NOT_FOUND"));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, AiStateConflictException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("AI_STATE_CONFLICT"));
    }
}

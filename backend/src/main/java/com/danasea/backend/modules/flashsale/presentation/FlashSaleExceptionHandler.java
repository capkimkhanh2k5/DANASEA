package com.danasea.backend.modules.flashsale.presentation;

import com.danasea.backend.modules.flashsale.domain.FlashSaleException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class FlashSaleExceptionHandler {
    @ExceptionHandler(FlashSaleException.class)
    public ResponseEntity<?> business(FlashSaleException e) {
        int status=e.code().endsWith("NOT_FOUND")?404:e.code().equals("IDEMPOTENCY_KEY_REUSED")?409:422;
        return ResponseEntity.status(status).body(Map.of("code",e.code(),"message",e.getMessage()));
    }
}

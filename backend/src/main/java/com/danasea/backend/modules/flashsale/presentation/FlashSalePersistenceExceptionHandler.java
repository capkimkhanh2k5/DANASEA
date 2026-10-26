package com.danasea.backend.modules.flashsale.presentation;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(0)
@RestControllerAdvice(basePackageClasses=AdminFlashSaleController.class)
public class FlashSalePersistenceExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(Map.of("code","FLASH_SALE_CONFLICT","message","Conflicting or immutable Flash Sale data"));
    }
}

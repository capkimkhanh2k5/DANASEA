package com.danasea.backend.modules.service.presentation.handlers;

import com.danasea.backend.modules.service.domain.exceptions.CategoryHasActiveServicesException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasChildrenException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasDependentRulesException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasLinkedServicesException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHierarchyLoopException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.SlugAlreadyExistsException;
import com.danasea.backend.shared.presentation.ErrorResponse;
import com.danasea.backend.shared.presentation.LocalizedExceptionHandlerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CategoryExceptionHandler extends LocalizedExceptionHandlerSupport {

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCategoryNotFound(CategoryNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error("CATEGORY_NOT_FOUND"));
    }

    @ExceptionHandler(SlugAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleSlugAlreadyExists(SlugAlreadyExistsException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error("SLUG_ALREADY_EXISTS"));
    }

    @ExceptionHandler(CategoryHasActiveServicesException.class)
    public ResponseEntity<ErrorResponse> handleCategoryHasActiveServices(CategoryHasActiveServicesException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error("CATEGORY_HAS_ACTIVE_SERVICES"));
    }

    @ExceptionHandler(CategoryHasLinkedServicesException.class)
    public ResponseEntity<ErrorResponse> handleCategoryHasLinkedServices(CategoryHasLinkedServicesException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error("CATEGORY_HAS_LINKED_SERVICES"));
    }

    @ExceptionHandler(CategoryHasChildrenException.class)
    public ResponseEntity<ErrorResponse> handleCategoryHasChildren(CategoryHasChildrenException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error("CATEGORY_HAS_CHILDREN"));
    }

    @ExceptionHandler(CategoryHasDependentRulesException.class)
    public ResponseEntity<ErrorResponse> handleCategoryHasDependentRules(CategoryHasDependentRulesException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error("CATEGORY_HAS_DEPENDENT_RULES"));
    }

    @ExceptionHandler(CategoryHierarchyLoopException.class)
    public ResponseEntity<ErrorResponse> handleCategoryHierarchyLoop(CategoryHierarchyLoopException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error("CATEGORY_HIERARCHY_LOOP"));
    }
}

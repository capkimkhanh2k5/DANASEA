package com.danasea.backend.modules.service.presentation.handlers;

import com.danasea.backend.modules.service.domain.exceptions.CategoryInactiveException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.ImageNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.InvalidFileTypeException;
import com.danasea.backend.modules.service.domain.exceptions.InvalidServiceStateException;
import com.danasea.backend.modules.service.domain.exceptions.MaxImagesExceededException;
import com.danasea.backend.modules.service.domain.exceptions.SafetyDocumentRequiredException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceImagesRequiredException;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.UnauthorizedServiceAccessException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.exceptions.WaiverContentRequiredException;
import com.danasea.backend.modules.service.domain.exceptions.WeatherRequirementsMissingException;
import com.danasea.backend.shared.presentation.ErrorResponse;
import com.danasea.backend.shared.presentation.LocalizedExceptionHandlerSupport;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.danasea.backend.modules.service")
public class ServiceExceptionHandler extends LocalizedExceptionHandlerSupport {

    @ExceptionHandler(ServiceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleServiceNotFound(ServiceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("SERVICE_NOT_FOUND"));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCategoryNotFound(CategoryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("CATEGORY_NOT_FOUND"));
    }

    @ExceptionHandler(CategoryInactiveException.class)
    public ResponseEntity<ErrorResponse> handleCategoryInactive(CategoryInactiveException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("CATEGORY_INACTIVE"));
    }

    @ExceptionHandler(VendorNotApprovedException.class)
    public ResponseEntity<ErrorResponse> handleVendorNotApproved(VendorNotApprovedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("VENDOR_NOT_APPROVED"));
    }

    @ExceptionHandler(UnauthorizedServiceAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(UnauthorizedServiceAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("UNAUTHORIZED_SERVICE_ACCESS"));
    }

    @ExceptionHandler(InvalidServiceStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidServiceStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_SERVICE_STATE"));
    }

    @ExceptionHandler(ServiceImagesRequiredException.class)
    public ResponseEntity<ErrorResponse> handleImagesRequired(ServiceImagesRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("SERVICE_IMAGES_REQUIRED"));
    }

    @ExceptionHandler(WeatherRequirementsMissingException.class)
    public ResponseEntity<ErrorResponse> handleWeatherMissing(WeatherRequirementsMissingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("WEATHER_REQUIREMENTS_MISSING"));
    }

    @ExceptionHandler(WaiverContentRequiredException.class)
    public ResponseEntity<ErrorResponse> handleWaiverContentRequired(WaiverContentRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("WAIVER_CONTENT_REQUIRED"));
    }

    @ExceptionHandler(ImageNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleImageNotFound(ImageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("IMAGE_NOT_FOUND"));
    }

    @ExceptionHandler(MaxImagesExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxImagesExceeded(MaxImagesExceededException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(error("MAX_IMAGES_EXCEEDED"));
    }

    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileType(InvalidFileTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_FILE_TYPE"));
    }

    @ExceptionHandler(SafetyDocumentRequiredException.class)
    public ResponseEntity<ErrorResponse> handleSafetyDocumentRequired(SafetyDocumentRequiredException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(error("SAFETY_DOCUMENT_REQUIRED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : message("error.invalid_input");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INPUT", message));
    }
}

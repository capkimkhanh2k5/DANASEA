package com.danasea.backend.modules.service.presentation.handlers;

import com.danasea.backend.modules.service.domain.exceptions.*;
import com.danasea.backend.shared.presentation.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServiceExceptionHandlerTest {

    private final ServiceExceptionHandler handler = new ServiceExceptionHandler();

    @Test
    void illegalArgumentResponseHidesInternalExceptionMessage() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("internal detail"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().code());
        assertEquals("Invalid request.", response.getBody().message());
    }

    @Test
    void testSafetyDocumentRequiredException() {
        ResponseEntity<ErrorResponse> response = handler.handleSafetyDocumentRequired(new SafetyDocumentRequiredException("Test"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SAFETY_DOCUMENT_REQUIRED", response.getBody().code());
    }

    @Test
    void testMaxImagesExceededException() {
        ResponseEntity<ErrorResponse> response = handler.handleMaxImagesExceeded(new MaxImagesExceededException("5"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MAX_IMAGES_EXCEEDED", response.getBody().code());
    }

    @Test
    void testVendorNotApprovedException() {
        ResponseEntity<ErrorResponse> response = handler.handleVendorNotApproved(new VendorNotApprovedException("Test"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VENDOR_NOT_APPROVED", response.getBody().code());
    }

    @Test
    void testServiceNotFoundException() {
        ResponseEntity<ErrorResponse> response = handler.handleServiceNotFound(new ServiceNotFoundException(UUID.randomUUID()));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SERVICE_NOT_FOUND", response.getBody().code());
    }
}

package com.danasea.backend.modules.service.presentation.controllers;

import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.application.usecases.GetSafetyDocumentsUseCase;
import com.danasea.backend.modules.service.application.usecases.UploadSafetyDocumentUseCase;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.presentation.dtos.SafetyDocumentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import com.danasea.backend.modules.service.domain.ports.VendorPort;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendor/services/{serviceId}/safety-documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
public class VendorSafetyDocumentController {

    private final UploadSafetyDocumentUseCase uploadSafetyDocumentUseCase;
    private final GetSafetyDocumentsUseCase getSafetyDocumentsUseCase;
    private final JpaServiceRepository serviceRepository;
    private final VendorPort vendorPort;
    private final FileStoragePort fileStoragePort;

    /**
     * GET /api/vendor/services/{serviceId}/safety-documents
     * Owning vendor xem danh sách chứng chỉ an toàn của service mình sở hữu
     */
    @GetMapping
    public ResponseEntity<List<SafetyDocumentResponse>> listSafetyDocuments(
            @PathVariable UUID serviceId) {

        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        UUID vendorId = vendorPort.findByUserId(userId)
                .map(vendor -> vendor.getId())
                .orElseThrow(() -> new AccessDeniedException("Vendor profile not found"));

        var serviceEntity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found: " + serviceId));

        if (!vendorId.equals(serviceEntity.getVendorId())) {
            throw new AccessDeniedException("Vendor does not own this service");
        }

        var docs = getSafetyDocumentsUseCase.execute(serviceId);
        return ResponseEntity.ok(docs.stream()
                .map(doc -> SafetyDocumentResponse.from(doc, fileStoragePort.generateDeliveryUrl(doc.getFileUrl())))
                .toList());
    }

    /**
     * POST /api/vendor/services/{serviceId}/safety-documents
     * Upload chứng chỉ an toàn (PDF hoặc ảnh)
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<SafetyDocumentResponse> uploadSafetyDocument(
            @PathVariable UUID serviceId,
            @RequestParam("file") MultipartFile file) {

        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        UUID vendorId = vendorPort.findByUserId(userId)
                .map(vendor -> vendor.getId())
                .orElseThrow(() -> new AccessDeniedException("Vendor profile not found"));
        var saved = uploadSafetyDocumentUseCase.execute(serviceId, vendorId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SafetyDocumentResponse.from(saved, fileStoragePort.generateDeliveryUrl(saved.getFileUrl())));
    }
}

package com.danasea.backend.modules.service.presentation.controllers;

import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.application.usecases.GetSafetyDocumentsUseCase;
import com.danasea.backend.modules.service.application.usecases.ReplaceSafetyDocumentUseCase;
import com.danasea.backend.modules.service.application.usecases.UploadSafetyDocumentUseCase;
import com.danasea.backend.modules.service.application.usecases.WithdrawSafetyDocumentUseCase;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.models.DocStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.presentation.dtos.SafetyDocumentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import com.danasea.backend.modules.service.domain.ports.VendorPort;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/vendor/services/{serviceId}/safety-documents")
@PreAuthorize("hasRole('VENDOR')")
public class VendorSafetyDocumentController {

    private final UploadSafetyDocumentUseCase uploadSafetyDocumentUseCase;
    private final WithdrawSafetyDocumentUseCase withdrawSafetyDocumentUseCase;
    private final ReplaceSafetyDocumentUseCase replaceSafetyDocumentUseCase;
    private final GetSafetyDocumentsUseCase getSafetyDocumentsUseCase;
    private final JpaServiceRepository serviceRepository;
    private final VendorPort vendorPort;
    private final FileStoragePort fileStoragePort;

    public VendorSafetyDocumentController(
            UploadSafetyDocumentUseCase uploadSafetyDocumentUseCase,
            GetSafetyDocumentsUseCase getSafetyDocumentsUseCase,
            JpaServiceRepository serviceRepository,
            VendorPort vendorPort,
            FileStoragePort fileStoragePort) {
        this(uploadSafetyDocumentUseCase, null, null, getSafetyDocumentsUseCase, serviceRepository, vendorPort, fileStoragePort);
    }

    @Autowired
    public VendorSafetyDocumentController(
            UploadSafetyDocumentUseCase uploadSafetyDocumentUseCase,
            WithdrawSafetyDocumentUseCase withdrawSafetyDocumentUseCase,
            ReplaceSafetyDocumentUseCase replaceSafetyDocumentUseCase,
            GetSafetyDocumentsUseCase getSafetyDocumentsUseCase,
            JpaServiceRepository serviceRepository,
            VendorPort vendorPort,
            FileStoragePort fileStoragePort) {
        this.uploadSafetyDocumentUseCase = uploadSafetyDocumentUseCase;
        this.withdrawSafetyDocumentUseCase = withdrawSafetyDocumentUseCase;
        this.replaceSafetyDocumentUseCase = replaceSafetyDocumentUseCase;
        this.getSafetyDocumentsUseCase = getSafetyDocumentsUseCase;
        this.serviceRepository = serviceRepository;
        this.vendorPort = vendorPort;
        this.fileStoragePort = fileStoragePort;
    }

    /**
     * GET /api/vendor/services/{serviceId}/safety-documents
     * Owning vendor xem danh sách chứng chỉ an toàn của service mình sở hữu
     */
    public ResponseEntity<List<SafetyDocumentResponse>> listSafetyDocuments(UUID serviceId) {
        return listSafetyDocuments(serviceId, false);
    }

    @GetMapping
    public ResponseEntity<List<SafetyDocumentResponse>> listSafetyDocuments(
            @PathVariable UUID serviceId,
            @RequestParam(name = "includeInactive", required = false, defaultValue = "false") boolean includeInactive) {

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
        var filteredDocs = docs.stream()
                .filter(doc -> includeInactive || (doc.getStatus() != DocStatus.WITHDRAWN && doc.getStatus() != DocStatus.SUPERSEDED))
                .map(doc -> SafetyDocumentResponse.from(doc, fileStoragePort.generateDeliveryUrl(doc.getFileUrl())))
                .toList();
        return ResponseEntity.ok(filteredDocs);
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

    /**
     * POST /api/vendor/services/{serviceId}/safety-documents/{documentId}/withdraw
     * Thu hồi chứng chỉ (PENDING hoặc REJECTED)
     */
    @PostMapping("/{documentId}/withdraw")
    public ResponseEntity<SafetyDocumentResponse> withdrawSafetyDocument(
            @PathVariable UUID serviceId,
            @PathVariable UUID documentId) {

        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        UUID vendorId = vendorPort.findByUserId(userId)
                .map(vendor -> vendor.getId())
                .orElseThrow(() -> new AccessDeniedException("Vendor profile not found"));
        var withdrawn = withdrawSafetyDocumentUseCase.execute(serviceId, documentId, vendorId);
        return ResponseEntity.ok(SafetyDocumentResponse.from(withdrawn, fileStoragePort.generateDeliveryUrl(withdrawn.getFileUrl())));
    }

    /**
     * DELETE /api/vendor/services/{serviceId}/safety-documents/{documentId}
     * Xóa/thu hồi chứng chỉ an toàn
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<SafetyDocumentResponse> deleteSafetyDocument(
            @PathVariable UUID serviceId,
            @PathVariable UUID documentId) {

        return withdrawSafetyDocument(serviceId, documentId);
    }

    /**
     * POST /api/vendor/services/{serviceId}/safety-documents/{documentId}/replace
     * Thay thế chứng chỉ (PENDING hoặc REJECTED)
     */
    @PostMapping(value = "/{documentId}/replace", consumes = "multipart/form-data")
    public ResponseEntity<SafetyDocumentResponse> replaceSafetyDocument(
            @PathVariable UUID serviceId,
            @PathVariable UUID documentId,
            @RequestParam("file") MultipartFile file) {

        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        UUID vendorId = vendorPort.findByUserId(userId)
                .map(vendor -> vendor.getId())
                .orElseThrow(() -> new AccessDeniedException("Vendor profile not found"));
        var replaced = replaceSafetyDocumentUseCase.execute(serviceId, documentId, vendorId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SafetyDocumentResponse.from(replaced, fileStoragePort.generateDeliveryUrl(replaced.getFileUrl())));
    }
}

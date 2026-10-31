package com.danasea.backend.modules.service.presentation.controllers;

import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.modules.service.application.ports.FileStoragePort;
import com.danasea.backend.modules.service.application.usecases.ApproveSafetyDocumentUseCase;
import com.danasea.backend.modules.service.application.usecases.GetSafetyDocumentsUseCase;
import com.danasea.backend.modules.service.presentation.dtos.RejectDocumentRequest;
import com.danasea.backend.modules.service.presentation.dtos.SafetyDocumentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/services/{serviceId}/safety-documents")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSafetyDocumentController {

    private final GetSafetyDocumentsUseCase getSafetyDocumentsUseCase;
    private final ApproveSafetyDocumentUseCase approveSafetyDocumentUseCase;
    private final FileStoragePort fileStoragePort;

    /**
     * GET /api/admin/services/{serviceId}/safety-documents
     * Admin xem danh sách chứng chỉ an toàn của service
     */
    @GetMapping
    public ResponseEntity<List<SafetyDocumentResponse>> listSafetyDocuments(
            @PathVariable UUID serviceId) {

        var docs = getSafetyDocumentsUseCase.execute(serviceId);
        return ResponseEntity.ok(docs.stream()
                .map(doc -> SafetyDocumentResponse.from(doc, fileStoragePort.generateDeliveryUrl(doc.getFileUrl())))
                .toList());
    }

    /**
     * PATCH /api/admin/services/{serviceId}/safety-documents/{docId}/approve
     * Admin phê duyệt chứng chỉ an toàn
     */
    @PatchMapping("/{docId}/approve")
    public ResponseEntity<SafetyDocumentResponse> approveDocument(
            @PathVariable UUID serviceId,
            @PathVariable UUID docId,
            Principal principal) {

        UUID adminId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        var updated = approveSafetyDocumentUseCase.approve(serviceId, docId, adminId);
        return ResponseEntity.ok(SafetyDocumentResponse.from(updated, fileStoragePort.generateDeliveryUrl(updated.getFileUrl())));
    }

    /**
     * PATCH /api/admin/services/{serviceId}/safety-documents/{docId}/reject
     * Admin từ chối chứng chỉ an toàn
     */
    @PatchMapping("/{docId}/reject")
    public ResponseEntity<SafetyDocumentResponse> rejectDocument(
            @PathVariable UUID serviceId,
            @PathVariable UUID docId,
            @Valid @RequestBody RejectDocumentRequest request,
            Principal principal) {

        UUID adminId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        var updated = approveSafetyDocumentUseCase.reject(
                serviceId, docId, adminId, request.rejectionReason());
        return ResponseEntity.ok(SafetyDocumentResponse.from(updated, fileStoragePort.generateDeliveryUrl(updated.getFileUrl())));
    }
}

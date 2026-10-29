package com.danasea.backend.modules.admin.presentation;

import java.util.UUID;

import com.danasea.backend.modules.admin.application.usecases.ReviewVendorDocumentUseCase;
import com.danasea.backend.modules.admin.presentation.dtos.RejectVendorDocumentRequest;
import com.danasea.backend.modules.vendor.application.ports.DocumentStoragePort;
import com.danasea.backend.modules.vendor.presentation.dtos.VendorDocumentResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/vendors/{vendorId}/documents")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminVendorDocumentController {
    private final ReviewVendorDocumentUseCase review;
    private final DocumentStoragePort documentStoragePort;

    @PatchMapping("/{documentId}/approve")
    public ResponseEntity<VendorDocumentResponse> approve(@PathVariable UUID vendorId, @PathVariable UUID documentId) {
        var updated = review.approve(vendorId, documentId, actorId());
        return ResponseEntity.ok(VendorDocumentResponse.fromDomain(updated, documentStoragePort.generateDeliveryUrl(updated.getFileUrl())));
    }

    @PatchMapping("/{documentId}/reject")
    public ResponseEntity<VendorDocumentResponse> reject(@PathVariable UUID vendorId, @PathVariable UUID documentId,
            @Valid @RequestBody RejectVendorDocumentRequest request) {
        var updated = review.reject(vendorId, documentId, actorId(), request.reason());
        return ResponseEntity.ok(VendorDocumentResponse.fromDomain(updated, documentStoragePort.generateDeliveryUrl(updated.getFileUrl())));
    }

    private UUID actorId() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Admin identity required"));
    }
}

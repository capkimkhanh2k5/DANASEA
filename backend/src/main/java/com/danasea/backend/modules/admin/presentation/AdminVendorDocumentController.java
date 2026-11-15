package com.danasea.backend.modules.admin.presentation;

import java.util.UUID;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.application.usecases.ReviewVendorDocumentUseCase;
import com.danasea.backend.modules.admin.presentation.dtos.RejectVendorDocumentRequest;
import com.danasea.backend.modules.vendor.application.ports.DocumentStoragePort;
import com.danasea.backend.modules.vendor.presentation.dtos.VendorDocumentResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/vendors/{vendorId}/documents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVendorDocumentController {
    private final ReviewVendorDocumentUseCase review;
    private final DocumentStoragePort documentStoragePort;
    private final AccountInternalApi accountInternalApi;

    @Autowired
    public AdminVendorDocumentController(ReviewVendorDocumentUseCase review,
                                        DocumentStoragePort documentStoragePort,
                                        @Autowired(required = false) AccountInternalApi accountInternalApi) {
        this.review = review;
        this.documentStoragePort = documentStoragePort;
        this.accountInternalApi = accountInternalApi;
    }

    public AdminVendorDocumentController(ReviewVendorDocumentUseCase review,
                                        DocumentStoragePort documentStoragePort) {
        this(review, documentStoragePort, null);
    }

    @PatchMapping("/{documentId}/approve")
    public ResponseEntity<VendorDocumentResponse> approve(@PathVariable UUID vendorId, @PathVariable UUID documentId) {
        var updated = review.approve(vendorId, documentId, actorId());
        String reviewerName = resolveReviewerName(updated.getReviewedBy());
        return ResponseEntity.ok(VendorDocumentResponse.fromDomain(
                updated,
                documentStoragePort.generateDeliveryUrl(updated.getFileUrl()),
                reviewerName
        ));
    }

    @PatchMapping("/{documentId}/reject")
    public ResponseEntity<VendorDocumentResponse> reject(@PathVariable UUID vendorId, @PathVariable UUID documentId,
            @Valid @RequestBody RejectVendorDocumentRequest request) {
        var updated = review.reject(vendorId, documentId, actorId(), request.reason());
        String reviewerName = resolveReviewerName(updated.getReviewedBy());
        return ResponseEntity.ok(VendorDocumentResponse.fromDomain(
                updated,
                documentStoragePort.generateDeliveryUrl(updated.getFileUrl()),
                reviewerName
        ));
    }

    private String resolveReviewerName(UUID reviewedBy) {
        if (reviewedBy != null && accountInternalApi != null) {
            return accountInternalApi.findUserById(reviewedBy).map(User::getFullName).orElse(null);
        }
        return null;
    }

    private UUID actorId() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Admin identity required"));
    }
}

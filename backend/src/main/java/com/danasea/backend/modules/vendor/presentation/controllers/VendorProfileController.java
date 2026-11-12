package com.danasea.backend.modules.vendor.presentation.controllers;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.vendor.application.usecases.GetVendorDocumentsUseCase;
import com.danasea.backend.modules.vendor.application.usecases.GetVendorProfileUseCase;
import com.danasea.backend.modules.vendor.application.usecases.RegisterVendorProfileUseCase;
import com.danasea.backend.modules.vendor.application.usecases.ResubmitVendorProfileUseCase;
import com.danasea.backend.modules.vendor.application.usecases.UpdateVendorProfileUseCase;
import com.danasea.backend.modules.vendor.application.usecases.UploadVendorDocumentUseCase;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import com.danasea.backend.modules.vendor.domain.models.VendorDocument;
import com.danasea.backend.modules.vendor.presentation.dtos.RegisterVendorProfileRequest;
import com.danasea.backend.modules.vendor.presentation.dtos.UpdateVendorProfileRequest;
import com.danasea.backend.modules.vendor.presentation.dtos.VendorDocumentResponse;
import com.danasea.backend.modules.vendor.presentation.dtos.VendorProfileResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/vendor")
public class VendorProfileController {

    private final RegisterVendorProfileUseCase registerVendorProfileUseCase;
    private final GetVendorProfileUseCase getVendorProfileUseCase;
    private final UpdateVendorProfileUseCase updateVendorProfileUseCase;
    private final ResubmitVendorProfileUseCase resubmitVendorProfileUseCase;
    private final UploadVendorDocumentUseCase uploadVendorDocumentUseCase;
    private final GetVendorDocumentsUseCase getVendorDocumentsUseCase;
    private final AccountInternalApi accountInternalApi;

    @Autowired
    public VendorProfileController(
            RegisterVendorProfileUseCase registerVendorProfileUseCase,
            GetVendorProfileUseCase getVendorProfileUseCase,
            UpdateVendorProfileUseCase updateVendorProfileUseCase,
            ResubmitVendorProfileUseCase resubmitVendorProfileUseCase,
            UploadVendorDocumentUseCase uploadVendorDocumentUseCase,
            GetVendorDocumentsUseCase getVendorDocumentsUseCase,
            AccountInternalApi accountInternalApi) {
        this.registerVendorProfileUseCase = registerVendorProfileUseCase;
        this.getVendorProfileUseCase = getVendorProfileUseCase;
        this.updateVendorProfileUseCase = updateVendorProfileUseCase;
        this.resubmitVendorProfileUseCase = resubmitVendorProfileUseCase;
        this.uploadVendorDocumentUseCase = uploadVendorDocumentUseCase;
        this.getVendorDocumentsUseCase = getVendorDocumentsUseCase;
        this.accountInternalApi = accountInternalApi;
    }

    public VendorProfileController(
            RegisterVendorProfileUseCase registerVendorProfileUseCase,
            GetVendorProfileUseCase getVendorProfileUseCase,
            UpdateVendorProfileUseCase updateVendorProfileUseCase,
            UploadVendorDocumentUseCase uploadVendorDocumentUseCase,
            GetVendorDocumentsUseCase getVendorDocumentsUseCase,
            AccountInternalApi accountInternalApi) {
        this(registerVendorProfileUseCase, getVendorProfileUseCase, updateVendorProfileUseCase, null,
                uploadVendorDocumentUseCase, getVendorDocumentsUseCase, accountInternalApi);
    }

    @Deprecated(since = "2.0", forRemoval = true)
    @PostMapping("/profile")
    @PreAuthorize("denyAll()")
    public ResponseEntity<VendorProfileResponse> registerProfile(
            @Valid @RequestBody RegisterVendorProfileRequest request,
            Principal principal) {
        throw new UnsupportedOperationException("Direct vendor registration is deprecated. Please apply via /api/partner-applications.");
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('VENDOR', 'CUSTOMER')")
    public ResponseEntity<VendorProfileResponse> getProfile(Principal principal) {
        UUID userId = resolveUserId(principal);
        Vendor vendor = getVendorProfileUseCase.execute(userId);
        return ResponseEntity.ok(VendorProfileResponse.fromDomain(vendor));
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<VendorProfileResponse> updateProfile(
            @Valid @RequestBody UpdateVendorProfileRequest request,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        Vendor vendor = updateVendorProfileUseCase.execute(userId, request.toCommand());
        return ResponseEntity.ok(VendorProfileResponse.fromDomain(vendor));
    }

    @PostMapping("/profile/resubmit")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<VendorProfileResponse> resubmitProfile(Principal principal) {
        UUID userId = resolveUserId(principal);
        Vendor vendor = resubmitVendorProfileUseCase.execute(userId);
        return ResponseEntity.ok(VendorProfileResponse.fromDomain(vendor));
    }

    @PostMapping("/documents")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<VendorDocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "doc_type", required = false) String docTypeParam,
            @RequestParam(value = "docType", required = false) String docTypeCamel,
            Principal principal) {
        UUID userId = resolveUserId(principal);
        String docType = (docTypeParam != null && !docTypeParam.isBlank()) ? docTypeParam : docTypeCamel;
        VendorDocument document = uploadVendorDocumentUseCase.execute(userId, file, docType);
        return ResponseEntity.status(HttpStatus.CREATED).body(VendorDocumentResponse.fromDomain(document));
    }

    @GetMapping("/documents")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<List<VendorDocumentResponse>> getDocuments(Principal principal) {
        UUID userId = resolveUserId(principal);
        List<VendorDocument> documents = getVendorDocumentsUseCase.execute(userId);
        List<VendorDocumentResponse> responses = documents.stream()
                .map(VendorDocumentResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(responses);
    }

    private UUID resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        String identifier = principal.getName();
        var userOpt = accountInternalApi.findUserByEmail(identifier);
        if (userOpt.isPresent()) {
            return userOpt.get().getId();
        }
        try {
            UUID uuid = UUID.fromString(identifier);
            userOpt = accountInternalApi.findUserById(uuid);
            if (userOpt.isPresent()) {
                return userOpt.get().getId();
            }
        } catch (IllegalArgumentException ignored) {
        }
        throw new VendorNotFoundException("User not found: " + identifier);
    }
}

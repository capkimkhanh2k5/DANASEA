package com.danasea.backend.modules.admin.presentation;

import java.security.Principal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.admin.application.usecases.ApproveVendorUseCase;
import com.danasea.backend.modules.admin.application.usecases.GetVendorDetailUseCase;
import com.danasea.backend.modules.admin.application.usecases.GetVendorsUseCase;
import com.danasea.backend.modules.admin.application.usecases.RejectVendorUseCase;
import com.danasea.backend.modules.admin.presentation.dtos.AdminVendorResponse;
import com.danasea.backend.modules.admin.presentation.dtos.RejectVendorRequest;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/vendors")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVendorController {

    private final GetVendorsUseCase getVendorsUseCase;
    private final GetVendorDetailUseCase getVendorDetailUseCase;
    private final ApproveVendorUseCase approveVendorUseCase;
    private final RejectVendorUseCase rejectVendorUseCase;
    private final AccountInternalApi accountInternalApi;

    @GetMapping
    public ResponseEntity<Page<AdminVendorResponse>> getVendors(
            @RequestParam(required = false) VerificationStatus status,
            Pageable pageable) {
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!Set.of("createdAt", "updatedAt", "businessName", "verificationStatus")
                    .contains(order.getProperty())) {
                throw new IllegalArgumentException("Unsupported sort field: " + order.getProperty());
            }
        }
        Pageable validatedPageable = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        return ResponseEntity.ok(getVendorsUseCase.execute(status, validatedPageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminVendorResponse> getVendor(@PathVariable UUID id) {
        return ResponseEntity.ok(getVendorDetailUseCase.execute(id));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<AdminVendorResponse> approveVendor(
            @PathVariable UUID id,
            Principal principal) {
        UUID actorId = resolveActorId(principal);
        return ResponseEntity.ok(approveVendorUseCase.execute(id, actorId));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<AdminVendorResponse> rejectVendor(
            @PathVariable UUID id,
            @RequestBody @Valid RejectVendorRequest request,
            Principal principal) {
        UUID actorId = resolveActorId(principal);
        return ResponseEntity.ok(rejectVendorUseCase.execute(id, actorId, request.getReason()));
    }

    private UUID resolveActorId(Principal principal) {
        if (principal != null && principal.getName() != null) {
            String name = principal.getName();
            try {
                return UUID.fromString(name);
            } catch (IllegalArgumentException ignored) {
                return accountInternalApi.findUserByEmail(name)
                        .map(User::getId)
                        .orElse(null);
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            try {
                return UUID.fromString(auth.getName());
            } catch (IllegalArgumentException ignored) {
                return accountInternalApi.findUserByEmail(auth.getName())
                        .map(User::getId)
                        .orElse(null);
            }
        }

        return null;
    }
}

package com.danasea.backend.modules.partner.presentation.controllers;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.partner.application.usecases.ApprovePartnerApplicationUseCase;
import com.danasea.backend.modules.partner.application.usecases.GetAdminPartnerApplicationDetailUseCase;
import com.danasea.backend.modules.partner.application.usecases.GetAdminPartnerApplicationsUseCase;
import com.danasea.backend.modules.partner.application.usecases.RejectPartnerApplicationUseCase;
import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.presentation.dtos.AdminRejectApplicationRequest;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/partner-applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPartnerApplicationController {

    private final GetAdminPartnerApplicationsUseCase getApplicationsUseCase;
    private final GetAdminPartnerApplicationDetailUseCase getApplicationDetailUseCase;
    private final ApprovePartnerApplicationUseCase approveApplicationUseCase;
    private final RejectPartnerApplicationUseCase rejectApplicationUseCase;
    private final AccountInternalApi accountInternalApi;

    @GetMapping
    public ResponseEntity<Page<PartnerApplicationResponse>> getApplications(
            @RequestParam(required = false) PartnerApplicationStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (pageable.getPageNumber() < 0 || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        return ResponseEntity.ok(getApplicationsUseCase.execute(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartnerApplicationResponse> getApplication(@PathVariable UUID id) {
        return ResponseEntity.ok(getApplicationDetailUseCase.execute(id));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<PartnerApplicationResponse> approveApplication(
            @PathVariable UUID id,
            Principal principal) {
        UUID adminId = resolveAdminId(principal);
        return ResponseEntity.ok(approveApplicationUseCase.execute(id, adminId));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<PartnerApplicationResponse> rejectApplication(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRejectApplicationRequest request,
            Principal principal) {
        UUID adminId = resolveAdminId(principal);
        return ResponseEntity.ok(rejectApplicationUseCase.execute(id, adminId, request.getRejectionReason()));
    }

    private UUID resolveAdminId(Principal principal) {
        if (principal != null && principal.getName() != null) {
            String name = principal.getName();
            try {
                return UUID.fromString(name);
            } catch (IllegalArgumentException ignored) {
                if (accountInternalApi != null) {
                    return accountInternalApi.findUserByEmail(name)
                            .map(User::getId)
                            .orElse(null);
                }
            }
        }
        return SecurityUtils.getCurrentUserId().orElse(null);
    }
}

package com.danasea.backend.modules.partner.presentation.controllers;

import com.danasea.backend.modules.partner.application.usecases.GetMyPartnerApplicationUseCase;
import com.danasea.backend.modules.partner.application.usecases.ResubmitPartnerApplicationUseCase;
import com.danasea.backend.modules.partner.application.usecases.SubmitPartnerApplicationUseCase;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import com.danasea.backend.modules.partner.presentation.dtos.ResubmitPartnerApplicationRequest;
import com.danasea.backend.modules.partner.presentation.dtos.SubmitPartnerApplicationRequest;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/partner-applications")
@RequiredArgsConstructor
public class PartnerApplicationController {

    private final SubmitPartnerApplicationUseCase submitUseCase;
    private final GetMyPartnerApplicationUseCase getMyUseCase;
    private final ResubmitPartnerApplicationUseCase resubmitUseCase;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PartnerApplicationResponse> submitApplication(
            @Valid @RequestBody SubmitPartnerApplicationRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        return ResponseEntity.status(HttpStatus.CREATED).body(submitUseCase.execute(userId, request));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'VENDOR')")
    public ResponseEntity<PartnerApplicationResponse> getMyApplication() {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        return ResponseEntity.ok(getMyUseCase.execute(userId));
    }

    @PostMapping("/resubmit")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PartnerApplicationResponse> resubmitApplication(
            @Valid @RequestBody ResubmitPartnerApplicationRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        return ResponseEntity.ok(resubmitUseCase.execute(userId, request));
    }
}

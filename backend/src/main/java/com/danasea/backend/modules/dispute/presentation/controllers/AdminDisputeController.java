package com.danasea.backend.modules.dispute.presentation.controllers;

import com.danasea.backend.modules.dispute.application.usecases.GetAdminDisputeDetailUseCase;
import com.danasea.backend.modules.dispute.application.usecases.GetDisputesUseCase;
import com.danasea.backend.modules.dispute.application.usecases.ResolveDisputeUseCase;
import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import com.danasea.backend.modules.dispute.presentation.dtos.ResolveDisputeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/disputes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final GetDisputesUseCase getDisputesUseCase;
    private final ResolveDisputeUseCase resolveDisputeUseCase;
    private final GetAdminDisputeDetailUseCase getAdminDisputeDetailUseCase;

    @GetMapping
    public ResponseEntity<Page<DisputeResponse>> getDisputes(
            @RequestParam(name = "status", required = false) DisputeStatus status,
            @RequestParam(name = "reason", required = false) DisputeReason reason,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.info("Admin query disputes with status: {}, reason: {}", status, reason);
        Page<DisputeResponse> response = getDisputesUseCase.execute(status, reason, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisputeResponse> getDisputeDetail(
            @PathVariable("id") UUID id
    ) {
        log.info("Admin query dispute detail for id: {}", id);
        DisputeResponse response = getAdminDisputeDetailUseCase.execute(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<DisputeResponse> resolveDispute(
            @PathVariable("id") UUID disputeId,
            @Valid @RequestBody ResolveDisputeRequest request
    ) {
        log.info("Admin resolve dispute id: {}, resolution: {}", disputeId, request.resolution());
        DisputeResponse response = resolveDisputeUseCase.execute(disputeId, request);
        return ResponseEntity.ok(response);
    }
}

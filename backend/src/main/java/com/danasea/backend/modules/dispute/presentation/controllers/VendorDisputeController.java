package com.danasea.backend.modules.dispute.presentation.controllers;

import com.danasea.backend.modules.dispute.application.usecases.GetVendorDisputeDetailUseCase;
import com.danasea.backend.modules.dispute.application.usecases.GetVendorDisputesUseCase;
import com.danasea.backend.modules.dispute.application.usecases.SubmitVendorDisputeResponseUseCase;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import com.danasea.backend.modules.dispute.presentation.dtos.SubmitVendorResponseRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/vendor/disputes")
@PreAuthorize("hasRole('VENDOR')")
@RequiredArgsConstructor
public class VendorDisputeController {

    private final GetVendorDisputesUseCase getVendorDisputesUseCase;
    private final GetVendorDisputeDetailUseCase getVendorDisputeDetailUseCase;
    private final SubmitVendorDisputeResponseUseCase submitVendorDisputeResponseUseCase;

    @GetMapping
    public ResponseEntity<Page<DisputeResponse>> getVendorDisputes(
            @RequestParam(name = "status", required = false) DisputeStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.info("Vendor request to get disputes with status: {}, pageable: {}", status, pageable);
        Page<DisputeResponse> response = getVendorDisputesUseCase.execute(status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisputeResponse> getVendorDisputeDetail(
            @PathVariable("id") UUID id
    ) {
        log.info("Vendor request to get dispute detail for id: {}", id);
        DisputeResponse response = getVendorDisputeDetailUseCase.execute(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/responses")
    public ResponseEntity<DisputeResponse> submitVendorResponse(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SubmitVendorResponseRequest request
    ) {
        log.info("Vendor request to submit response for dispute id: {}", id);
        DisputeResponse response = submitVendorDisputeResponseUseCase.execute(id, request);
        return ResponseEntity.ok(response);
    }
}

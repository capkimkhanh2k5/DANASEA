package com.danasea.backend.modules.settlement.presentation.controllers;

import com.danasea.backend.modules.settlement.application.dtos.GenerateSettlementRequest;
import com.danasea.backend.modules.settlement.application.dtos.SettlementDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.SettlementResponse;
import com.danasea.backend.modules.settlement.application.usecases.FinalizeSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GenerateSettlementUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetSettlementsUseCase;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/settlements")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettlementController {

    private final GenerateSettlementUseCase generateSettlementUseCase;
    private final GetSettlementsUseCase getSettlementsUseCase;
    private final FinalizeSettlementUseCase finalizeSettlementUseCase;

    @Autowired
    public AdminSettlementController(
            GenerateSettlementUseCase generateSettlementUseCase,
            GetSettlementsUseCase getSettlementsUseCase,
            FinalizeSettlementUseCase finalizeSettlementUseCase
    ) {
        this.generateSettlementUseCase = generateSettlementUseCase;
        this.getSettlementsUseCase = getSettlementsUseCase;
        this.finalizeSettlementUseCase = finalizeSettlementUseCase;
    }

    @PostMapping("/generate")
    public ResponseEntity<SettlementResponse> generateSettlement(
            @Valid @RequestBody GenerateSettlementRequest request
    ) {
        log.info("Admin requested a settlement for vendor {} from {} to {}",
                request.vendorId(), request.periodStart(), request.periodEnd());
        SettlementResponse response = generateSettlementUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<SettlementResponse>> getSettlements(
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SettlementResponse> result = getSettlementsUseCase.executeForAdmin(vendorId, status, from, to, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SettlementDetailResponse> getSettlementDetail(@PathVariable UUID id) {
        SettlementDetailResponse result = getSettlementsUseCase.getAdminSettlementById(id);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/finalize")
    public ResponseEntity<SettlementResponse> finalizeSettlement(@PathVariable UUID id) {
        log.info("Admin requested finalization of settlement {}", id);
        SettlementResponse response = finalizeSettlementUseCase.execute(id);
        return ResponseEntity.ok(response);
    }
}

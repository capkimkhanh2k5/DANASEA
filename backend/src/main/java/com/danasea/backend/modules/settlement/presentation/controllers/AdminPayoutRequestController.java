package com.danasea.backend.modules.settlement.presentation.controllers;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.settlement.application.dtos.ApprovePayoutRequest;
import com.danasea.backend.modules.settlement.application.dtos.MarkPaidPayoutRequest;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.application.dtos.RejectPayoutRequest;
import com.danasea.backend.modules.settlement.application.usecases.ApprovePayoutRequestUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetAdminPayoutRequestsUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetPayoutRequestDetailUseCase;
import com.danasea.backend.modules.settlement.application.usecases.MarkPayoutPaidUseCase;
import com.danasea.backend.modules.settlement.application.usecases.RejectPayoutRequestUseCase;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/payout-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutRequestController {

    private final GetAdminPayoutRequestsUseCase getAdminPayoutRequestsUseCase;
    private final GetPayoutRequestDetailUseCase getPayoutRequestDetailUseCase;
    private final ApprovePayoutRequestUseCase approvePayoutRequestUseCase;
    private final RejectPayoutRequestUseCase rejectPayoutRequestUseCase;
    private final MarkPayoutPaidUseCase markPayoutPaidUseCase;

    @Autowired
    public AdminPayoutRequestController(
            GetAdminPayoutRequestsUseCase getAdminPayoutRequestsUseCase,
            GetPayoutRequestDetailUseCase getPayoutRequestDetailUseCase,
            ApprovePayoutRequestUseCase approvePayoutRequestUseCase,
            RejectPayoutRequestUseCase rejectPayoutRequestUseCase,
            MarkPayoutPaidUseCase markPayoutPaidUseCase
    ) {
        this.getAdminPayoutRequestsUseCase = getAdminPayoutRequestsUseCase;
        this.getPayoutRequestDetailUseCase = getPayoutRequestDetailUseCase;
        this.approvePayoutRequestUseCase = approvePayoutRequestUseCase;
        this.rejectPayoutRequestUseCase = rejectPayoutRequestUseCase;
        this.markPayoutPaidUseCase = markPayoutPaidUseCase;
    }

    @GetMapping
    public ResponseEntity<Page<PayoutRequestResponse>> getAdminPayoutRequests(
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) UUID settlementId,
            @RequestParam(required = false) PayoutRequestStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<PayoutRequestResponse> result = getAdminPayoutRequestsUseCase.execute(
                vendorId, settlementId, status, from, to, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayoutRequestDetailResponse> getAdminPayoutRequestDetail(@PathVariable UUID id) {
        PayoutRequestDetailResponse result = getPayoutRequestDetailUseCase.execute(id, null);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<PayoutRequestResponse> approvePayoutRequest(
            @PathVariable UUID id,
            @RequestBody(required = false) ApprovePayoutRequest request
    ) {
        UUID adminUserId = SecurityUtils.getCurrentUserId().orElse(null);
        log.info("Admin {} duyệt PayoutRequest #{}", adminUserId, id);
        PayoutRequestResponse response = approvePayoutRequestUseCase.execute(id, adminUserId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<PayoutRequestResponse> rejectPayoutRequest(
            @PathVariable UUID id,
            @Valid @RequestBody RejectPayoutRequest request
    ) {
        UUID adminUserId = SecurityUtils.getCurrentUserId().orElse(null);
        log.info("Admin {} từ chối PayoutRequest #{} với lý do: {}", adminUserId, id, request.rejectionReason());
        PayoutRequestResponse response = rejectPayoutRequestUseCase.execute(id, adminUserId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/mark-paid")
    public ResponseEntity<PayoutRequestResponse> markPayoutPaid(
            @PathVariable UUID id,
            @RequestBody(required = false) MarkPaidPayoutRequest request
    ) {
        UUID adminUserId = SecurityUtils.getCurrentUserId().orElse(null);
        log.info("Admin {} ghi nhận giải ngân PAID cho PayoutRequest #{}", adminUserId, id);
        PayoutRequestResponse response = markPayoutPaidUseCase.execute(id, adminUserId, request);
        return ResponseEntity.ok(response);
    }
}

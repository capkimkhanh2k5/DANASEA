package com.danasea.backend.modules.settlement.presentation.controllers;

import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.settlement.application.dtos.CreatePayoutRequestRequest;
import com.danasea.backend.modules.settlement.application.dtos.EligibleSettlementPayoutResponse;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.application.usecases.CreatePayoutRequestUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetPayoutRequestDetailUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetVendorEligibleSettlementsUseCase;
import com.danasea.backend.modules.settlement.application.usecases.GetVendorPayoutRequestsUseCase;
import com.danasea.backend.modules.settlement.domain.exceptions.UnauthorizedSettlementAccessException;
import com.danasea.backend.security.authorization.domain.models.AuthorizationSubject;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/vendor/payout-requests")
@PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
public class VendorPayoutRequestController {

    private final CreatePayoutRequestUseCase createPayoutRequestUseCase;
    private final GetVendorPayoutRequestsUseCase getVendorPayoutRequestsUseCase;
    private final GetPayoutRequestDetailUseCase getPayoutRequestDetailUseCase;
    private final GetVendorEligibleSettlementsUseCase getVendorEligibleSettlementsUseCase;
    private final VendorLookupPort vendorLookupPort;

    @Autowired
    public VendorPayoutRequestController(
            CreatePayoutRequestUseCase createPayoutRequestUseCase,
            GetVendorPayoutRequestsUseCase getVendorPayoutRequestsUseCase,
            GetPayoutRequestDetailUseCase getPayoutRequestDetailUseCase,
            GetVendorEligibleSettlementsUseCase getVendorEligibleSettlementsUseCase,
            VendorLookupPort vendorLookupPort
    ) {
        this.createPayoutRequestUseCase = createPayoutRequestUseCase;
        this.getVendorPayoutRequestsUseCase = getVendorPayoutRequestsUseCase;
        this.getPayoutRequestDetailUseCase = getPayoutRequestDetailUseCase;
        this.getVendorEligibleSettlementsUseCase = getVendorEligibleSettlementsUseCase;
        this.vendorLookupPort = vendorLookupPort;
    }

    @PostMapping
    public ResponseEntity<PayoutRequestResponse> createPayoutRequest(
            @Valid @RequestBody CreatePayoutRequestRequest request
    ) {
        UUID currentVendorId = resolveCurrentVendorId();
        log.info("Vendor {} tạo yêu cầu rút tiền: {} đ cho kỳ đối soát {}",
                currentVendorId, request.amount(), request.settlementId());
        PayoutRequestResponse response = createPayoutRequestUseCase.execute(currentVendorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<PayoutRequestResponse>> getVendorPayoutRequests(
            @RequestParam(required = false) PayoutRequestStatus status,
            @RequestParam(required = false) UUID settlementId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID currentVendorId = resolveCurrentVendorId();
        Page<PayoutRequestResponse> result = getVendorPayoutRequestsUseCase.execute(
                currentVendorId, status, settlementId, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/eligible-settlements")
    public ResponseEntity<List<EligibleSettlementPayoutResponse>> getEligibleSettlements() {
        UUID currentVendorId = resolveCurrentVendorId();
        List<EligibleSettlementPayoutResponse> result = getVendorEligibleSettlementsUseCase.execute(currentVendorId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayoutRequestDetailResponse> getVendorPayoutRequestDetail(@PathVariable UUID id) {
        UUID currentVendorId = resolveCurrentVendorId();
        PayoutRequestDetailResponse result = getPayoutRequestDetailUseCase.execute(id, currentVendorId);
        return ResponseEntity.ok(result);
    }

    private UUID resolveCurrentVendorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            throw new UnauthorizedSettlementAccessException("User is not authenticated");
        }

        UUID currentUserId = null;
        if (auth.getDetails() instanceof AuthorizationSubject subject) {
            currentUserId = subject.userId();
        }
        if (currentUserId == null) {
            try {
                currentUserId = UUID.fromString(auth.getName());
            } catch (IllegalArgumentException e) {
                currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
            }
        }
        if (currentUserId == null) {
            throw new UnauthorizedSettlementAccessException("User is not authenticated");
        }

        final UUID finalUserId = currentUserId;
        return vendorLookupPort.findVendorIdByUserId(finalUserId)
                .orElseThrow(() -> new UnauthorizedSettlementAccessException("Vendor profile not found for current user: " + finalUserId));
    }
}

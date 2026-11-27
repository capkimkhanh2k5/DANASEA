package com.danasea.backend.modules.dispute.presentation.controllers;

import com.danasea.backend.modules.dispute.application.usecases.CreateDisputeUseCase;
import com.danasea.backend.modules.dispute.application.usecases.GetCustomerDisputeDetailUseCase;
import com.danasea.backend.modules.dispute.application.usecases.GetCustomerDisputesUseCase;
import com.danasea.backend.modules.dispute.presentation.dtos.CreateDisputeRequest;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CustomerDisputeController {

    private final CreateDisputeUseCase createDisputeUseCase;
    private final GetCustomerDisputesUseCase getCustomerDisputesUseCase;
    private final GetCustomerDisputeDetailUseCase getCustomerDisputeDetailUseCase;

    @PostMapping("/api/orders/{id}/disputes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DisputeResponse> createDispute(
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody CreateDisputeRequest request
    ) {
        log.info("Received customer request to create dispute for orderId: {}, subOrderId: {}", orderId, request.subOrderId());
        DisputeResponse response = createDisputeUseCase.execute(orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/disputes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DisputeResponse>> getCustomerDisputes(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.info("Customer request to get disputes with pageable: {}", pageable);
        Page<DisputeResponse> response = getCustomerDisputesUseCase.execute(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/disputes/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DisputeResponse> getCustomerDisputeDetail(
            @PathVariable("id") UUID id
    ) {
        log.info("Customer request to get dispute detail for id: {}", id);
        DisputeResponse response = getCustomerDisputeDetailUseCase.execute(id);
        return ResponseEntity.ok(response);
    }
}

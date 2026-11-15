package com.danasea.backend.modules.service.presentation.controllers;

import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.service.application.usecases.BatchCreateServiceSlotsUseCase;
import com.danasea.backend.modules.service.application.usecases.CreateServiceSlotUseCase;
import com.danasea.backend.modules.service.application.usecases.GetVendorServiceSlotsUseCase;
import com.danasea.backend.modules.service.application.usecases.UpdateServiceSlotUseCase;
import com.danasea.backend.modules.service.presentation.dtos.BatchCreateServiceSlotsRequest;
import com.danasea.backend.modules.service.presentation.dtos.BatchCreateServiceSlotsResponse;
import com.danasea.backend.modules.service.presentation.dtos.CreateServiceSlotRequest;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotResponse;
import com.danasea.backend.modules.service.presentation.dtos.UpdateServiceSlotRequest;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendor/services/{id}/slots")
@RequiredArgsConstructor
public class VendorServiceSlotController {

    private final GetVendorServiceSlotsUseCase getVendorServiceSlotsUseCase;
    private final CreateServiceSlotUseCase createServiceSlotUseCase;
    private final BatchCreateServiceSlotsUseCase batchCreateServiceSlotsUseCase;
    private final UpdateServiceSlotUseCase updateServiceSlotUseCase;

    @GetMapping
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<List<ServiceSlotResponse>> getSlots(@PathVariable("id") UUID serviceId) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        List<ServiceSlotResponse> response = getVendorServiceSlotsUseCase.execute(userId, serviceId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ServiceSlotResponse> createSlot(
            @PathVariable("id") UUID serviceId,
            @Valid @RequestBody CreateServiceSlotRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        ServiceSlotResponse response = createServiceSlotUseCase.execute(userId, serviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<BatchCreateServiceSlotsResponse> batchCreateSlots(
            @PathVariable("id") UUID serviceId,
            @Valid @RequestBody BatchCreateServiceSlotsRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        BatchCreateServiceSlotsResponse response = batchCreateServiceSlotsUseCase.execute(userId, serviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{slotId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ServiceSlotResponse> updateSlot(
            @PathVariable("id") UUID serviceId,
            @PathVariable("slotId") UUID slotId,
            @Valid @RequestBody UpdateServiceSlotRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        ServiceSlotResponse response = updateServiceSlotUseCase.execute(userId, serviceId, slotId, request);
        return ResponseEntity.ok(response);
    }
}

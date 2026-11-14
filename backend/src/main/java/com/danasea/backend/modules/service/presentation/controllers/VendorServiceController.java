package com.danasea.backend.modules.service.presentation.controllers;


import com.danasea.backend.modules.service.application.dtos.CreateServiceCommand;
import com.danasea.backend.modules.service.application.dtos.ServiceResult;
import com.danasea.backend.modules.service.application.dtos.UpdateServiceCommand;
import com.danasea.backend.modules.service.application.usecases.CreateServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.DeleteServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.GetServiceDetailUseCase;
import com.danasea.backend.modules.service.application.usecases.GetVendorServicesUseCase;
import com.danasea.backend.modules.service.application.usecases.PauseServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.ResumeServiceUseCase;
import com.danasea.backend.modules.service.application.usecases.SubmitServiceForReviewUseCase;
import com.danasea.backend.modules.service.application.usecases.UpdateServiceUseCase;
import com.danasea.backend.modules.service.presentation.dtos.CreateServiceRequest;
import com.danasea.backend.modules.service.presentation.dtos.UpdateServiceRequest;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendor/services")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
public class VendorServiceController {

    private final CreateServiceUseCase createServiceUseCase;
    private final GetVendorServicesUseCase getVendorServicesUseCase;
    private final GetServiceDetailUseCase getServiceDetailUseCase;
    private final UpdateServiceUseCase updateServiceUseCase;
    private final SubmitServiceForReviewUseCase submitServiceForReviewUseCase;
    private final PauseServiceUseCase pauseServiceUseCase;
    private final ResumeServiceUseCase resumeServiceUseCase;
    private final DeleteServiceUseCase deleteServiceUseCase;

    @PostMapping
    public ResponseEntity<ServiceResult> createService(
            @Valid @RequestBody CreateServiceRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        CreateServiceCommand cmd = new CreateServiceCommand(
                userId,
                request.categoryId(),
                request.name(),
                request.nameEn(),
                request.description(),
                request.descriptionEn(),
                request.price(),
                request.durationMinutes(),
                request.capacityPerSlot(),
                request.operationType(),
                request.locationName(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.waiverContent(),
                request.waiverContentEn(),
                request.waiverRequired(),
                request.weatherSensitive(),
                request.minWindKmh(),
                request.maxWaveM());
        return ResponseEntity.status(HttpStatus.CREATED).body(createServiceUseCase.execute(cmd));
    }

    @GetMapping
    public ResponseEntity<List<ServiceResult>> getMyServices() {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        return ResponseEntity.ok(getVendorServicesUseCase.execute(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResult> getServiceDetail(
            @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        return ResponseEntity.ok(getServiceDetailUseCase.execute(userId, id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ServiceResult> updateService(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        UpdateServiceCommand cmd = new UpdateServiceCommand(
                userId, id,
                request.categoryId(), request.name(), request.nameEn(),
                request.description(), request.descriptionEn(),
                request.price(), request.durationMinutes(), request.capacityPerSlot(),
                request.operationType(),
                request.locationName(), request.address(), request.latitude(), request.longitude(),
                request.waiverContent(), request.waiverContentEn(), request.waiverRequired(),
                request.weatherSensitive(), request.minWindKmh(), request.maxWaveM());
        return ResponseEntity.ok(updateServiceUseCase.execute(cmd));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ServiceResult> submitForReview(
            @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        return ResponseEntity.ok(submitServiceForReviewUseCase.execute(userId, id));
    }

    @PatchMapping("/{id}/pause")
    public ResponseEntity<ServiceResult> pause(
            @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        return ResponseEntity.ok(pauseServiceUseCase.execute(userId, id));
    }

    @PatchMapping("/{id}/resume")
    public ResponseEntity<ServiceResult> resume(
            @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        return ResponseEntity.ok(resumeServiceUseCase.execute(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(
            @PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        deleteServiceUseCase.execute(userId, id);
        return ResponseEntity.noContent().build();
    }
}

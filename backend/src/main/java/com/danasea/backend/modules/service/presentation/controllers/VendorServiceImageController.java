package com.danasea.backend.modules.service.presentation.controllers;

import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.modules.service.application.usecases.DeleteServiceImageUseCase;
import com.danasea.backend.modules.service.application.usecases.ReorderServiceImagesUseCase;
import com.danasea.backend.modules.service.application.usecases.UploadServiceImageUseCase;
import com.danasea.backend.modules.service.presentation.dtos.ReorderImagesRequest;
import com.danasea.backend.modules.service.presentation.dtos.ServiceImageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import com.danasea.backend.modules.service.domain.ports.VendorPort;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendor/services/{serviceId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
public class VendorServiceImageController {

    private final UploadServiceImageUseCase uploadServiceImageUseCase;
    private final DeleteServiceImageUseCase deleteServiceImageUseCase;
    private final ReorderServiceImagesUseCase reorderServiceImagesUseCase;
    private final VendorPort vendorPort;

    /**
     * POST /api/vendor/services/{serviceId}/images
     * Upload ảnh cho service — multipart/form-data
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ServiceImageResponse> uploadImage(
            @PathVariable UUID serviceId,
            @RequestParam("file") MultipartFile file) {

        UUID vendorId = currentVendorId();
        var saved = uploadServiceImageUseCase.execute(serviceId, vendorId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceImageResponse.from(saved));
    }

    /**
     * DELETE /api/vendor/services/{serviceId}/images/{imageId}
     */
    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID serviceId,
            @PathVariable UUID imageId) {

        UUID vendorId = currentVendorId();
        deleteServiceImageUseCase.execute(serviceId, imageId, vendorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/vendor/services/{serviceId}/images/reorder
     * Đổi sort_order hàng loạt — body: { "imageIds": ["uuid1", "uuid2", ...] }
     */
    @PatchMapping("/reorder")
    public ResponseEntity<List<ServiceImageResponse>> reorderImages(
            @PathVariable UUID serviceId,
            @Valid @RequestBody ReorderImagesRequest request) {

        UUID vendorId = currentVendorId();
        var reordered = reorderServiceImagesUseCase.execute(serviceId, vendorId, request.imageIds());
        return ResponseEntity.ok(reordered.stream().map(ServiceImageResponse::from).toList());
    }

    private UUID currentVendorId() {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User ID not found"));
        return vendorPort.findByUserId(userId)
                .map(vendor -> vendor.getId())
                .orElseThrow(() -> new AccessDeniedException("Vendor profile not found"));
    }
}

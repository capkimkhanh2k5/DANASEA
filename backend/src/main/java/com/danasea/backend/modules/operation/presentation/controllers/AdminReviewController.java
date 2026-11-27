package com.danasea.backend.modules.operation.presentation.controllers;

import java.util.UUID;

import com.danasea.backend.modules.operation.application.usecases.GetAdminReviewsUseCase;
import com.danasea.backend.modules.operation.application.usecases.UpdateReviewVisibilityUseCase;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import com.danasea.backend.modules.operation.presentation.dtos.UpdateReviewVisibilityRequest;
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

@Slf4j
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final GetAdminReviewsUseCase getAdminReviewsUseCase;
    private final UpdateReviewVisibilityUseCase updateReviewVisibilityUseCase;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReviewResponse>> getAdminReviews(
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) Boolean isFlagged,
            @RequestParam(required = false) Boolean isVisible,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ReviewResponse> response = getAdminReviewsUseCase.execute(serviceId, vendorId, isFlagged, isVisible, pageable);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewResponse> updateVisibility(
            @PathVariable("id") UUID reviewId,
            @Valid @RequestBody UpdateReviewVisibilityRequest request
    ) {
        ReviewResponse response = updateReviewVisibilityUseCase.execute(reviewId, request);
        return ResponseEntity.ok(response);
    }
}

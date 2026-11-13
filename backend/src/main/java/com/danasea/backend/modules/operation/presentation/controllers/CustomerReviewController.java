package com.danasea.backend.modules.operation.presentation.controllers;

import java.util.UUID;

import com.danasea.backend.modules.operation.application.usecases.CreateReviewUseCase;
import com.danasea.backend.modules.operation.application.usecases.FlagReviewUseCase;
import com.danasea.backend.modules.operation.application.usecases.UpdateReviewUseCase;
import com.danasea.backend.modules.operation.presentation.dtos.CreateReviewRequest;
import com.danasea.backend.modules.operation.presentation.dtos.FlagReviewRequest;
import com.danasea.backend.modules.operation.presentation.dtos.FlagReviewResponse;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import com.danasea.backend.modules.operation.presentation.dtos.UpdateReviewRequest;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CustomerReviewController {

    private final CreateReviewUseCase createReviewUseCase;
    private final UpdateReviewUseCase updateReviewUseCase;
    private final FlagReviewUseCase flagReviewUseCase;

    @PostMapping("/sub-orders/{id}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable("id") UUID subOrderId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        UUID customerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        ReviewResponse response = createReviewUseCase.execute(subOrderId, request, customerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/sub-orders/{id}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewResponse> updateReviewBySubOrder(
            @PathVariable("id") UUID subOrderId,
            @Valid @RequestBody UpdateReviewRequest request
    ) {
        UUID customerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        ReviewResponse response = updateReviewUseCase.executeBySubOrder(subOrderId, request, customerId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/reviews/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable("id") UUID reviewId,
            @Valid @RequestBody UpdateReviewRequest request
    ) {
        UUID customerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        ReviewResponse response = updateReviewUseCase.execute(reviewId, request, customerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reviews/{id}/flag")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FlagReviewResponse> flagReview(
            @PathVariable("id") UUID reviewId,
            @Valid @RequestBody FlagReviewRequest request
    ) {
        FlagReviewResponse response = flagReviewUseCase.execute(reviewId, request);
        return ResponseEntity.ok(response);
    }
}

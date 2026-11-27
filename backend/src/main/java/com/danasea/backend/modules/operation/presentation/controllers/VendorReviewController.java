package com.danasea.backend.modules.operation.presentation.controllers;

import java.util.UUID;

import com.danasea.backend.modules.operation.application.usecases.GetVendorReviewsUseCase;
import com.danasea.backend.modules.operation.application.usecases.ReplyVendorReviewUseCase;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import com.danasea.backend.modules.operation.presentation.dtos.VendorReplyRequest;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/vendor/reviews")
@RequiredArgsConstructor
public class VendorReviewController {

    private final GetVendorReviewsUseCase getVendorReviewsUseCase;
    private final ReplyVendorReviewUseCase replyVendorReviewUseCase;

    @GetMapping
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Page<ReviewResponse>> getVendorReviews(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        Page<ReviewResponse> response = getVendorReviewsUseCase.execute(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ReviewResponse> replyToReview(
            @PathVariable("id") UUID reviewId,
            @Valid @RequestBody VendorReplyRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
        ReviewResponse response = replyVendorReviewUseCase.execute(reviewId, request, userId);
        return ResponseEntity.ok(response);
    }
}

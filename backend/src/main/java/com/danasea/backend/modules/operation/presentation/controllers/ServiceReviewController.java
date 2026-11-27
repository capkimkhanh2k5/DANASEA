package com.danasea.backend.modules.operation.presentation.controllers;

import java.util.UUID;

import com.danasea.backend.modules.operation.application.usecases.GetServiceReviewsUseCase;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceReviewController {

    private final GetServiceReviewsUseCase getServiceReviewsUseCase;

    @GetMapping("/{id}/reviews")
    public ResponseEntity<Page<ReviewResponse>> getServiceReviews(
            @PathVariable("id") UUID serviceId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ReviewResponse> response = getServiceReviewsUseCase.execute(serviceId, pageable);
        return ResponseEntity.ok(response);
    }
}

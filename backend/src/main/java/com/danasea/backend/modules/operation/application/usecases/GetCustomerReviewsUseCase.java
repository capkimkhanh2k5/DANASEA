package com.danasea.backend.modules.operation.application.usecases;

import java.util.UUID;

import com.danasea.backend.modules.operation.infrastructure.persistence.mappers.ReviewMapper;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetCustomerReviewsUseCase {

    private final JpaReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;

    @Transactional(readOnly = true)
    public Page<ReviewResponse> execute(UUID customerId, Pageable pageable) {
        return reviewRepository.findByCustomerId(customerId, pageable)
                .map(reviewMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ReviewResponse executeBySubOrder(UUID subOrderId, UUID customerId) {
        return reviewRepository.findBySubOrderId(subOrderId)
                .filter(r -> r.getCustomerId().equals(customerId))
                .map(reviewMapper::toResponse)
                .orElse(null);
    }
}

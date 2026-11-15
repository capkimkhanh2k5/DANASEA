package com.danasea.backend.modules.operation.application.usecases;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.mappers.ReviewMapper;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAdminReviewsUseCase {

    private final JpaReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;

    @Transactional(readOnly = true)
    public Page<ReviewResponse> execute(UUID serviceId, UUID vendorId, Boolean isFlagged, Boolean isVisible, Pageable pageable) {
        Specification<ReviewJpaEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (serviceId != null) {
                predicates.add(cb.equal(root.get("serviceId"), serviceId));
            }
            if (vendorId != null) {
                predicates.add(cb.equal(root.get("vendorId"), vendorId));
            }
            if (isFlagged != null) {
                predicates.add(cb.equal(root.get("isFlagged"), isFlagged));
            }
            if (isVisible != null) {
                predicates.add(cb.equal(root.get("isVisible"), isVisible));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ReviewJpaEntity> page = reviewRepository.findAll(spec, pageable);
        return reviewMapper.toResponsePage(page);
    }
}

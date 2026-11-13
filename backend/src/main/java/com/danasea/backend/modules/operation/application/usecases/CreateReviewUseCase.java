package com.danasea.backend.modules.operation.application.usecases;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;

import com.danasea.backend.modules.operation.domain.exceptions.DuplicateReviewException;
import com.danasea.backend.modules.operation.domain.exceptions.InvalidReviewSubOrderStateException;
import com.danasea.backend.modules.operation.domain.exceptions.UnauthorizedReviewAccessException;
import com.danasea.backend.modules.operation.domain.services.ReviewRatingService;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.mappers.ReviewMapper;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.dtos.CreateReviewRequest;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReviewUseCase {

    private final JpaReviewRepository reviewRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaMasterOrderRepository masterOrderRepository;
    private final ReviewMapper reviewMapper;
    private final ReviewRatingService reviewRatingService;

    @Transactional
    public ReviewResponse execute(UUID subOrderId, CreateReviewRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedReviewAccessException("User is not authenticated"));
        return execute(subOrderId, request, currentUserId);
    }

    @Transactional
    public ReviewResponse execute(UUID subOrderId, CreateReviewRequest request, UUID customerId) {
        log.info("Creating review for subOrderId: {} by customerId: {}", subOrderId, customerId);

        // 1. Kiểm tra tồn tại SubOrder
        SubOrderJpaEntity subOrder = subOrderRepository.findById(subOrderId)
                .orElseThrow(() -> new OrderNotFoundException("Sub-order not found with id: " + subOrderId));

        // 2. Kiểm tra trạng thái đơn: chỉ đơn hoàn thành (COMPLETED) mới được đánh giá
        if (subOrder.getStatus() != SubOrderStatus.COMPLETED) {
            throw new InvalidReviewSubOrderStateException(
                    "Only completed sub-orders can be reviewed. Current status: " + subOrder.getStatus());
        }

        // 3. Kiểm tra quyền sở hữu (chỉ chính chủ khách hàng sở hữu đơn hàng)
        MasterOrderJpaEntity masterOrder = masterOrderRepository.findById(subOrder.getMasterOrderId())
                .orElseThrow(() -> new OrderNotFoundException("Master order not found with id: " + subOrder.getMasterOrderId()));

        if (masterOrder.getCustomerId() == null || !masterOrder.getCustomerId().equals(customerId)) {
            throw new UnauthorizedReviewAccessException("User is not the owner of this sub-order");
        }

        reviewRatingService.lockVendor(subOrder.getVendorId());

        // 4. Kiểm tra tính duy nhất: 1 đơn hàng con = 1 đánh giá
        if (reviewRepository.existsBySubOrderId(subOrderId)) {
            throw new DuplicateReviewException("A review has already been submitted for this sub-order: " + subOrderId);
        }

        // 5. Lưu ReviewJpaEntity
        ReviewJpaEntity entity = ReviewJpaEntity.builder()
                .subOrderId(subOrderId)
                .customerId(customerId)
                .vendorId(subOrder.getVendorId())
                .serviceId(subOrder.getServiceId())
                .rating(request.rating())
                .comment(request.comment())
                .images(reviewMapper.serializeImages(request.images()))
                .isFlagged(false)
                .isVisible(true)
                .build();

        ReviewJpaEntity saved;
        try {
            saved = reviewRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uq_reviews_sub_order".equals(violation.getConstraintName())) {
                    throw new DuplicateReviewException("A review already exists for this sub-order");
                }
            }
            throw exception;
        }

        // 6. Cập nhật điểm service và vendor
        reviewRatingService.recalculateRatings(saved.getServiceId(), saved.getVendorId());

        return reviewMapper.toResponse(saved);
    }
}

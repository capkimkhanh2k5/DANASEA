package com.danasea.backend.modules.operation.application.usecases;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.operation.domain.exceptions.ReviewNotFoundException;
import com.danasea.backend.modules.operation.domain.exceptions.UnauthorizedReviewAccessException;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.mappers.ReviewMapper;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import com.danasea.backend.modules.operation.presentation.dtos.VendorReplyRequest;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.LocalizedMessageRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplyVendorReviewUseCase {

    private final JpaReviewRepository reviewRepository;
    private final JpaVendorRepository vendorRepository;
    private final ReviewMapper reviewMapper;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final JpaServiceRepository serviceRepository;

    @Transactional
    public ReviewResponse execute(UUID reviewId, VendorReplyRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedReviewAccessException("User is not authenticated"));
        return execute(reviewId, request, currentUserId);
    }

    @Transactional
    public ReviewResponse execute(UUID reviewId, VendorReplyRequest request, UUID userId) {
        log.info("Vendor userId: {} replying to reviewId: {}", userId, reviewId);

        VendorJpaEntity vendor = vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new VendorNotFoundException("Vendor profile not found for user: " + userId));

        ReviewJpaEntity review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException("Review not found with id: " + reviewId));

        if (!review.getVendorId().equals(vendor.getId())) {
            throw new UnauthorizedReviewAccessException("Vendor does not own the service associated with this review");
        }

        review.setVendorReply(request.reply());
        review.setVendorRepliedAt(OffsetDateTime.now());

        ReviewJpaEntity saved = reviewRepository.save(review);

        // Send in-app notification to the customer who submitted the review
        if (review.getCustomerId() != null && sendNotificationUseCase != null) {
            String vendorName = vendor.getBusinessName() != null ? vendor.getBusinessName() : "Nhà cung cấp";
            String serviceName = "dịch vụ";
            if (serviceRepository != null && review.getServiceId() != null) {
                serviceName = serviceRepository.findById(review.getServiceId())
                        .map(s -> s.getName() != null ? s.getName() : "dịch vụ")
                        .orElse("dịch vụ");
            }

            String idempotencyKey = "vendor_review_reply:" + review.getId();
            NotificationCommand command = new NotificationCommand(
                    review.getCustomerId(),
                    "REVIEW_REPLY",
                    NotificationChannel.IN_APP,
                    LocalizedMessageRef.of("notification.review.vendor_reply.title"),
                    LocalizedMessageRef.of("notification.review.vendor_reply.body", vendorName, serviceName),
                    "SUB_ORDER",
                    review.getSubOrderId(),
                    null
            );

            sendNotificationUseCase.executeOnce(command, idempotencyKey);
        }

        return reviewMapper.toResponse(saved);
    }
}

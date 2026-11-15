package com.danasea.backend.modules.operation.application.usecases;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.mappers.ReviewMapper;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCustomerReviewsUseCase Unit Tests")
class GetCustomerReviewsUseCaseTest {

    @Mock
    private JpaReviewRepository reviewRepository;

    private ReviewMapper reviewMapper;
    private GetCustomerReviewsUseCase getCustomerReviewsUseCase;

    private UUID customerId;
    private UUID subOrderId;
    private UUID reviewId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();
        reviewId = UUID.randomUUID();

        reviewMapper = new ReviewMapper(new ObjectMapper(), mock(com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository.class));
        getCustomerReviewsUseCase = new GetCustomerReviewsUseCase(reviewRepository, reviewMapper);
    }

    @Test
    @DisplayName("Should return page of reviews for customer with vendor reply")
    void shouldReturnCustomerReviews() {
        ReviewJpaEntity entity = ReviewJpaEntity.builder()
                .customerId(customerId)
                .subOrderId(subOrderId)
                .vendorId(UUID.randomUUID())
                .serviceId(UUID.randomUUID())
                .rating((short) 5)
                .comment("Tour cano rất vui!")
                .vendorReply("Cảm ơn bạn nhiều nhé!")
                .vendorRepliedAt(OffsetDateTime.now())
                .isVisible(true)
                .isFlagged(false)
                .build();
        entity.setId(reviewId);

        Pageable pageable = PageRequest.of(0, 10);
        when(reviewRepository.findByCustomerId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));

        Page<ReviewResponse> result = getCustomerReviewsUseCase.execute(customerId, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        ReviewResponse res = result.getContent().get(0);
        assertThat(res.id()).isEqualTo(reviewId);
        assertThat(res.vendorReply()).isEqualTo("Cảm ơn bạn nhiều nhé!");
        assertThat(res.vendorRepliedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return review by subOrderId if owned by customer")
    void shouldReturnReviewBySubOrderId() {
        ReviewJpaEntity entity = ReviewJpaEntity.builder()
                .customerId(customerId)
                .subOrderId(subOrderId)
                .vendorId(UUID.randomUUID())
                .serviceId(UUID.randomUUID())
                .rating((short) 5)
                .comment("Tuyệt vời")
                .vendorReply("Hẹn gặp lại bạn!")
                .vendorRepliedAt(OffsetDateTime.now())
                .build();
        entity.setId(reviewId);

        when(reviewRepository.findBySubOrderId(subOrderId)).thenReturn(Optional.of(entity));

        ReviewResponse res = getCustomerReviewsUseCase.executeBySubOrder(subOrderId, customerId);

        assertThat(res).isNotNull();
        assertThat(res.id()).isEqualTo(reviewId);
        assertThat(res.vendorReply()).isEqualTo("Hẹn gặp lại bạn!");
    }

    @Test
    @DisplayName("Should return null if review belongs to different customer")
    void shouldReturnNullIfDifferentCustomer() {
        ReviewJpaEntity entity = ReviewJpaEntity.builder()
                .customerId(UUID.randomUUID()) // other customer
                .subOrderId(subOrderId)
                .vendorId(UUID.randomUUID())
                .serviceId(UUID.randomUUID())
                .rating((short) 5)
                .build();
        entity.setId(reviewId);

        when(reviewRepository.findBySubOrderId(subOrderId)).thenReturn(Optional.of(entity));

        ReviewResponse res = getCustomerReviewsUseCase.executeBySubOrder(subOrderId, customerId);

        assertThat(res).isNull();
    }
}

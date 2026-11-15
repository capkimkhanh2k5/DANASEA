package com.danasea.backend.modules.operation.application.usecases;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.mappers.ReviewMapper;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCustomerReviewsUseCase Unit Tests")
class GetCustomerReviewsUseCaseTest {

    @Mock
    private JpaReviewRepository reviewRepository;
    @Mock
    private JpaServiceRepository serviceRepository;
    @Mock
    private JpaUserRepository userRepository;

    private GetCustomerReviewsUseCase getCustomerReviewsUseCase;

    private UUID customerId;
    private UUID subOrderId;
    private UUID reviewId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();
        reviewId = UUID.randomUUID();

        ReviewMapper reviewMapper = new ReviewMapper(new ObjectMapper(), userRepository, serviceRepository);
        getCustomerReviewsUseCase = new GetCustomerReviewsUseCase(reviewRepository, reviewMapper);
    }

    @Test
    void loadsServiceNamesOnceForDistinctIdsOnTheCustomerPage() {
        UUID canoeId = UUID.randomUUID();
        UUID diveId = UUID.randomUUID();
        UUID missingServiceId = UUID.randomUUID();
        List<ReviewJpaEntity> reviews = List.of(
                ReviewJpaEntity.builder().customerId(customerId).serviceId(canoeId).build(),
                ReviewJpaEntity.builder().customerId(customerId).serviceId(diveId).build(),
                ReviewJpaEntity.builder().customerId(customerId).serviceId(canoeId).build(),
                ReviewJpaEntity.builder().customerId(customerId).serviceId(missingServiceId).build());
        ServiceJpaEntity canoe = new ServiceJpaEntity();
        canoe.setId(canoeId);
        canoe.setName("Tour cano");
        ServiceJpaEntity dive = new ServiceJpaEntity();
        dive.setId(diveId);
        dive.setName("Tour lặn biển");
        Pageable pageable = PageRequest.of(1, 4);
        when(reviewRepository.findByCustomerId(customerId, pageable)).thenReturn(new PageImpl<>(reviews, pageable, 9));
        Set<UUID> serviceIds = Set.of(canoeId, diveId, missingServiceId);
        when(serviceRepository.findAllById(serviceIds)).thenReturn(List.of(dive, canoe));

        Page<ReviewResponse> result = getCustomerReviewsUseCase.execute(customerId, pageable);

        verify(serviceRepository, never()).findById(any(UUID.class));
        verify(serviceRepository).findAllById(serviceIds);
        assertThat(result.getTotalElements()).isEqualTo(9);
        assertThat(result).extracting(ReviewResponse::serviceName)
                .containsExactly("Tour cano", "Tour lặn biển", "Tour cano", null);
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
        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setName("Tour cano");
        when(serviceRepository.findById(entity.getServiceId())).thenReturn(Optional.of(service));

        ReviewResponse res = getCustomerReviewsUseCase.executeBySubOrder(subOrderId, customerId);

        assertThat(res).isNotNull();
        assertThat(res.id()).isEqualTo(reviewId);
        assertThat(res.vendorReply()).isEqualTo("Hẹn gặp lại bạn!");
        assertThat(res.serviceName()).isEqualTo("Tour cano");
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
        verifyNoInteractions(serviceRepository);
    }
}

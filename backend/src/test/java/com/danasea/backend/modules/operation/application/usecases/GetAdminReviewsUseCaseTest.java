package com.danasea.backend.modules.operation.application.usecases;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.mappers.ReviewMapper;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAdminReviewsUseCaseTest {

    @Mock
    private JpaReviewRepository reviewRepository;
    @Mock
    private JpaUserRepository userRepository;
    @Mock
    private Root<ReviewJpaEntity> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder criteriaBuilder;
    @Mock
    private Path<Object> servicePath;
    @Mock
    private Path<Object> vendorPath;
    @Mock
    private Path<Object> flaggedPath;
    @Mock
    private Path<Object> visiblePath;
    @Captor
    private ArgumentCaptor<Specification<ReviewJpaEntity>> specificationCaptor;

    private GetAdminReviewsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAdminReviewsUseCase(reviewRepository, new ReviewMapper(new ObjectMapper(), userRepository));
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true"})
    void combinesAllSuppliedFiltersAndReturnsAdminPayloadWithPagination(boolean flagged, boolean visible) {
        UUID serviceId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(1, 2, Sort.by("createdAt").descending());
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-10-20T10:00:00+07:00");
        ReviewJpaEntity review = ReviewJpaEntity.builder()
                .subOrderId(UUID.randomUUID()).customerId(customerId).serviceId(serviceId).vendorId(vendorId)
                .rating((short) 2).comment("The trip started late")
                .images("[\"https://images.example/review.jpg\"]")
                .vendorReply("We are reviewing the departure time").vendorRepliedAt(createdAt.plusDays(1))
                .isFlagged(flagged).isVisible(visible).flagReason("Reported content")
                .moderationNote("Admin review required").build();
        review.setId(UUID.randomUUID());
        review.setCreatedAt(createdAt);
        review.setUpdatedAt(createdAt.plusDays(2));
        UserJpaEntity customer = new UserJpaEntity();
        customer.setFullName("Nguyen An");
        customer.setAvatarUrl("https://images.example/customer.jpg");
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(reviewRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(review), pageable, 7));
        Predicate servicePredicate = mock(Predicate.class);
        Predicate vendorPredicate = mock(Predicate.class);
        Predicate flaggedPredicate = mock(Predicate.class);
        Predicate visiblePredicate = mock(Predicate.class);
        Predicate combinedPredicate = mock(Predicate.class);
        when(root.get("serviceId")).thenReturn(servicePath);
        when(root.get("vendorId")).thenReturn(vendorPath);
        when(root.get("isFlagged")).thenReturn(flaggedPath);
        when(root.get("isVisible")).thenReturn(visiblePath);
        when(criteriaBuilder.equal(servicePath, serviceId)).thenReturn(servicePredicate);
        when(criteriaBuilder.equal(vendorPath, vendorId)).thenReturn(vendorPredicate);
        when(criteriaBuilder.equal(flaggedPath, flagged)).thenReturn(flaggedPredicate);
        when(criteriaBuilder.equal(visiblePath, visible)).thenReturn(visiblePredicate);
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(combinedPredicate);

        Page<ReviewResponse> response = useCase.execute(serviceId, vendorId, flagged, visible, pageable);

        ReviewResponse expected = ReviewResponse.builder()
                .id(review.getId()).subOrderId(review.getSubOrderId()).customerId(customerId)
                .customerName("Nguyen An").customerAvatar("https://images.example/customer.jpg")
                .vendorId(vendorId).serviceId(serviceId).rating((short) 2).comment("The trip started late")
                .images(List.of("https://images.example/review.jpg"))
                .vendorReply("We are reviewing the departure time").vendorRepliedAt(createdAt.plusDays(1))
                .isFlagged(flagged).isVisible(visible).flagReason("Reported content")
                .moderationNote("Admin review required").createdAt(createdAt).updatedAt(createdAt.plusDays(2)).build();
        assertThat(response.getContent()).containsExactly(expected);
        assertThat(response.getNumber()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(7);
        assertThat(response.getTotalPages()).isEqualTo(4);
        assertThat(response.getSort()).isEqualTo(pageable.getSort());
        verify(reviewRepository).findAll(specificationCaptor.capture(), eq(pageable));
        assertThat(specificationCaptor.getValue().toPredicate(root, query, criteriaBuilder))
                .isSameAs(combinedPredicate);
        verify(criteriaBuilder).and(new Predicate[]{servicePredicate, vendorPredicate, flaggedPredicate, visiblePredicate});
    }

    @Test
    void omitsAbsentFiltersAndReturnsAnEmptyPage() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(reviewRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));
        Predicate unrestrictedPredicate = mock(Predicate.class);
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(unrestrictedPredicate);

        Page<ReviewResponse> response = useCase.execute(null, null, null, null, pageable);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getPageable()).isEqualTo(pageable);
        verify(reviewRepository).findAll(specificationCaptor.capture(), eq(pageable));
        assertThat(specificationCaptor.getValue().toPredicate(root, query, criteriaBuilder))
                .isSameAs(unrestrictedPredicate);
        verify(criteriaBuilder).and(new Predicate[0]);
        verifyNoInteractions(root, userRepository);
    }
}

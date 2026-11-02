package com.danasea.backend.modules.operation.application.usecases;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.operation.domain.exceptions.UnauthorizedReviewAccessException;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.mappers.ReviewMapper;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.controllers.VendorReviewController;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import com.danasea.backend.modules.vendor.domain.exceptions.VendorNotFoundException;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.authorization.domain.models.AuthorizationSubject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetVendorReviewsUseCaseTest {

    @Mock
    private JpaReviewRepository reviewRepository;
    @Mock
    private JpaVendorRepository vendorRepository;
    @Mock
    private JpaUserRepository userRepository;
    private GetVendorReviewsUseCase useCase;
    private VendorReviewController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        useCase = new GetVendorReviewsUseCase(reviewRepository, vendorRepository,
                new ReviewMapper(new ObjectMapper(), userRepository));
        controller = new VendorReviewController(useCase, mock(ReplyVendorReviewUseCase.class));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void currentPrincipalSelectsItsOwnVendorAndMapsTheCompleteReviewPage(boolean throughController) {
        UUID userId = UUID.randomUUID();
        UUID misleadingName = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                misleadingName.toString(), "unused", List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
        authentication.setDetails(new AuthorizationSubject(userId, "vendor@example.test", Set.of("VENDOR"), Set.of()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setId(vendorId);
        vendor.setUserId(userId);
        PageRequest pageable = PageRequest.of(1, 2, Sort.by("createdAt").descending());
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-10-20T10:00:00+07:00");
        UUID customerId = UUID.randomUUID();
        ReviewJpaEntity review = ReviewJpaEntity.builder()
                .subOrderId(UUID.randomUUID()).customerId(customerId).vendorId(vendorId).serviceId(UUID.randomUUID())
                .rating((short) 2).comment("The trip started late")
                .images("[\"https://images.example/review.jpg\"]")
                .vendorReply("We are reviewing the departure time").vendorRepliedAt(createdAt.plusDays(1))
                .isVisible(false).isFlagged(true).flagReason("Reported content")
                .moderationNote("Admin review required").build();
        review.setId(UUID.randomUUID());
        review.setCreatedAt(createdAt);
        review.setUpdatedAt(createdAt.plusDays(2));
        UserJpaEntity customer = new UserJpaEntity();
        customer.setFullName("Nguyen An");
        customer.setAvatarUrl("https://images.example/customer.jpg");
        when(vendorRepository.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(reviewRepository.findByVendorId(vendorId, pageable)).thenReturn(new PageImpl<>(List.of(review), pageable, 7));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

        Page<ReviewResponse> result;
        if (throughController) {
            ResponseEntity<Page<ReviewResponse>> response = controller.getVendorReviews(pageable);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            result = response.getBody();
        } else {
            result = useCase.execute(pageable);
        }

        ReviewResponse expected = ReviewResponse.builder()
                .id(review.getId()).subOrderId(review.getSubOrderId()).customerId(customerId)
                .customerName("Nguyen An").customerAvatar("https://images.example/customer.jpg")
                .vendorId(vendorId).serviceId(review.getServiceId()).rating((short) 2).comment("The trip started late")
                .images(List.of("https://images.example/review.jpg"))
                .vendorReply("We are reviewing the departure time").vendorRepliedAt(createdAt.plusDays(1))
                .isVisible(false).isFlagged(true).flagReason("Reported content").moderationNote("Admin review required")
                .createdAt(createdAt).updatedAt(createdAt.plusDays(2)).build();
        assertThat(result).isNotNull();
        assertThat(result.getContent()).containsExactly(expected);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(7);
        assertThat(result.getTotalPages()).isEqualTo(4);
        assertThat(result.getSort()).isEqualTo(pageable.getSort());
        verify(vendorRepository).findByUserId(userId);
        verify(reviewRepository).findByVendorId(vendorId, pageable);
        verify(userRepository).findById(customerId);
        verifyNoMoreInteractions(vendorRepository, reviewRepository, userRepository);
    }

    @Test
    void explicitUserLookupReturnsAnEmptyTenantPageWithoutLoadingCustomerProfiles() {
        UUID userId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setId(vendorId);
        PageRequest pageable = PageRequest.of(0, 5);
        when(vendorRepository.findByUserId(userId)).thenReturn(Optional.of(vendor));
        when(reviewRepository.findByVendorId(vendorId, pageable)).thenReturn(Page.empty(pageable));

        Page<ReviewResponse> result = useCase.execute(userId, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getPageable()).isEqualTo(pageable);
        verify(vendorRepository).findByUserId(userId);
        verify(reviewRepository).findByVendorId(vendorId, pageable);
        verifyNoMoreInteractions(vendorRepository, reviewRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void missingVendorProfileIsRejectedBeforeAnyReviewOrCustomerLookup() {
        UUID userId = UUID.randomUUID();
        when(vendorRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(userId, PageRequest.of(0, 10)))
                .isInstanceOf(VendorNotFoundException.class)
                .hasMessage("Vendor profile not found for user: " + userId);

        verify(vendorRepository).findByUserId(userId);
        verifyNoMoreInteractions(vendorRepository);
        verifyNoInteractions(reviewRepository, userRepository);
    }

    @Test
    void absentAuthenticationDeniesBothUseCaseAndConsumerBeforeDataAccess() {
        PageRequest pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> useCase.execute(pageable))
                .isInstanceOf(UnauthorizedReviewAccessException.class).hasMessage("User is not authenticated");
        assertThatThrownBy(() -> controller.getVendorReviews(pageable))
                .isInstanceOf(AccessDeniedException.class).hasMessage("User is not authenticated");

        verifyNoInteractions(vendorRepository, reviewRepository, userRepository);
    }

    @Test
    void unauthenticatedTokenCannotSupplyAUserIdForReviewAccess() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(UUID.randomUUID().toString(), "unused"));

        assertThatThrownBy(() -> useCase.execute(PageRequest.of(0, 10)))
                .isInstanceOf(UnauthorizedReviewAccessException.class).hasMessage("User is not authenticated");

        verifyNoInteractions(vendorRepository, reviewRepository, userRepository);
    }

    @Test
    void authenticatedNameWithoutResolvableUserIdIsDeniedBeforeDataAccess() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "vendor@example.test", "unused", List.of(new SimpleGrantedAuthority("ROLE_VENDOR"))));

        assertThatThrownBy(() -> useCase.execute(PageRequest.of(0, 10)))
                .isInstanceOf(UnauthorizedReviewAccessException.class).hasMessage("User is not authenticated");

        verifyNoInteractions(vendorRepository, reviewRepository, userRepository);
    }
}

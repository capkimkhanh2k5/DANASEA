package com.danasea.backend.modules.operation.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.infrastructure.persistence.repositories.JpaNotificationRepository;
import com.danasea.backend.modules.operation.domain.exceptions.DuplicateReviewException;
import com.danasea.backend.modules.operation.domain.exceptions.InvalidReviewSubOrderStateException;
import com.danasea.backend.modules.operation.domain.exceptions.ReviewPeriodExpiredException;
import com.danasea.backend.modules.operation.domain.exceptions.UnauthorizedReviewAccessException;
import com.danasea.backend.modules.operation.domain.services.ReviewRatingService;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.mappers.ReviewMapper;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.dtos.*;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.shared.i18n.LocalizedMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Review & Rating UseCase Unit Tests")
class ReviewUseCaseTest {

    @Mock
    private JpaReviewRepository reviewRepository;

    @Mock
    private JpaSubOrderRepository subOrderRepository;

    @Mock
    private JpaMasterOrderRepository masterOrderRepository;

    @Mock
    private JpaServiceRepository serviceRepository;

    @Mock
    private JpaVendorRepository vendorRepository;

    @Mock
    private SendNotificationUseCase sendNotificationUseCase;

    private ReviewMapper reviewMapper;
    private ReviewRatingService reviewRatingService;

    private CreateReviewUseCase createReviewUseCase;
    private UpdateReviewUseCase updateReviewUseCase;
    private FlagReviewUseCase flagReviewUseCase;
    private GetServiceReviewsUseCase getServiceReviewsUseCase;
    private GetVendorReviewsUseCase getVendorReviewsUseCase;
    private ReplyVendorReviewUseCase replyVendorReviewUseCase;
    private GetAdminReviewsUseCase getAdminReviewsUseCase;
    private UpdateReviewVisibilityUseCase updateReviewVisibilityUseCase;

    private UUID customerId;
    private UUID vendorId;
    private UUID serviceId;
    private UUID masterOrderId;
    private UUID subOrderId;
    private UUID reviewId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        masterOrderId = UUID.randomUUID();
        subOrderId = UUID.randomUUID();
        reviewId = UUID.randomUUID();

        lenient().when(vendorRepository.findByIdForUpdate(any(UUID.class))).thenReturn(Optional.of(new VendorJpaEntity()));
        ObjectMapper objectMapper = new ObjectMapper();
        reviewMapper = new ReviewMapper(objectMapper, mock(JpaUserRepository.class), serviceRepository);
        reviewRatingService = new ReviewRatingService(reviewRepository, serviceRepository, vendorRepository);

        createReviewUseCase = new CreateReviewUseCase(reviewRepository, subOrderRepository, masterOrderRepository, reviewMapper, reviewRatingService);
        updateReviewUseCase = new UpdateReviewUseCase(reviewRepository, reviewMapper, reviewRatingService);
        flagReviewUseCase = new FlagReviewUseCase(reviewRepository);
        getServiceReviewsUseCase = new GetServiceReviewsUseCase(reviewRepository, reviewMapper);
        getVendorReviewsUseCase = new GetVendorReviewsUseCase(reviewRepository, vendorRepository, reviewMapper);
        replyVendorReviewUseCase = new ReplyVendorReviewUseCase(reviewRepository, vendorRepository, reviewMapper, sendNotificationUseCase, serviceRepository);
        getAdminReviewsUseCase = new GetAdminReviewsUseCase(reviewRepository, reviewMapper);
        updateReviewVisibilityUseCase = new UpdateReviewVisibilityUseCase(reviewRepository, reviewMapper, reviewRatingService);
    }

    @Test
    void publicPageLoadsServiceNameOnceAndRedactsPrivateReviewFields() {
        Pageable pageable = PageRequest.of(0, 10);
        ReviewJpaEntity review = ReviewJpaEntity.builder()
                .customerId(customerId)
                .subOrderId(subOrderId)
                .vendorId(vendorId)
                .serviceId(serviceId)
                .rating((short) 4)
                .isFlagged(true)
                .flagReason("Internal reason")
                .moderationNote("Internal note")
                .isVisible(true)
                .build();
        ReviewJpaEntity anotherReview = ReviewJpaEntity.builder().serviceId(serviceId).rating((short) 5).build();
        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setName("Tour cano");
        when(reviewRepository.findByServiceIdAndIsVisibleTrue(serviceId, pageable))
                .thenReturn(new PageImpl<>(List.of(review, anotherReview), pageable, 2));
        when(serviceRepository.findAllById(Set.of(serviceId))).thenReturn(List.of(service));

        Page<ReviewResponse> result = getServiceReviewsUseCase.execute(serviceId, pageable);

        verify(serviceRepository, never()).findById(any(UUID.class));
        verify(serviceRepository).findAllById(Set.of(serviceId));
        assertThat(result).extracting(ReviewResponse::serviceName).containsExactly("Tour cano", "Tour cano");
        ReviewResponse publicReview = result.getContent().get(0);
        assertThat(publicReview.customerId()).isNull();
        assertThat(publicReview.subOrderId()).isNull();
        assertThat(publicReview.isFlagged()).isNull();
        assertThat(publicReview.flagReason()).isNull();
        assertThat(publicReview.moderationNote()).isNull();
    }

    @Nested
    @DisplayName("CreateReviewUseCase Tests")
    class CreateReviewTests {

        @Test
        @DisplayName("Should successfully create review when sub-order is completed and owned by customer")
        void shouldCreateReviewSuccessfully() {
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(masterOrderId);
            subOrder.setServiceId(serviceId);
            subOrder.setVendorId(vendorId);
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customerId);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(reviewRepository.existsBySubOrderId(subOrderId)).thenReturn(false);

            ReviewJpaEntity savedReview = ReviewJpaEntity.builder()
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .vendorId(vendorId)
                    .serviceId(serviceId)
                    .rating((short) 5)
                    .comment("Trải nghiệm tuyệt vời!")
                    .isVisible(true)
                    .isFlagged(false)
                    .build();
            savedReview.setId(reviewId);
            savedReview.setCreatedAt(OffsetDateTime.now());

            when(reviewRepository.saveAndFlush(any(ReviewJpaEntity.class))).thenReturn(savedReview);

            // Mock rating recalculation queries
            when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(new ServiceJpaEntity()));
            when(vendorRepository.findByIdForUpdate(vendorId)).thenReturn(Optional.of(new VendorJpaEntity()));
            when(reviewRepository.countByServiceIdAndIsVisibleTrue(serviceId)).thenReturn(1L);
            when(reviewRepository.getAvgRatingByServiceId(serviceId)).thenReturn(5.0);
            when(reviewRepository.countByVendorIdAndIsVisibleTrue(vendorId)).thenReturn(1L);
            when(reviewRepository.getAvgRatingByVendorId(vendorId)).thenReturn(5.0);

            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Trải nghiệm tuyệt vời!", List.of("https://image1.jpg"));
            ReviewResponse response = createReviewUseCase.execute(subOrderId, request, customerId);

            assertThat(response).isNotNull();
            assertThat(response.rating()).isEqualTo((short) 5);
            assertThat(response.comment()).isEqualTo("Trải nghiệm tuyệt vời!");

            verify(reviewRepository).saveAndFlush(any(ReviewJpaEntity.class));
            verify(serviceRepository).save(any(ServiceJpaEntity.class));
            verify(vendorRepository).save(any(VendorJpaEntity.class));
        }

        @Test
        @DisplayName("Should reject review if sub-order is CHECKED_IN")
        void shouldRejectIfCheckedIn() {
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setStatus(SubOrderStatus.CHECKED_IN);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Comment", null);

            assertThatThrownBy(() -> createReviewUseCase.execute(subOrderId, request, customerId))
                    .isInstanceOf(InvalidReviewSubOrderStateException.class)
                    .hasMessageContaining("Only completed sub-orders can be reviewed. Current status: CHECKED_IN");
        }

        @Test
        @DisplayName("Should reject review if sub-order is CONFIRMED")
        void shouldRejectIfConfirmed() {
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setStatus(SubOrderStatus.CONFIRMED);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Comment", null);

            assertThatThrownBy(() -> createReviewUseCase.execute(subOrderId, request, customerId))
                    .isInstanceOf(InvalidReviewSubOrderStateException.class)
                    .hasMessageContaining("Only completed sub-orders can be reviewed. Current status: CONFIRMED");
        }

        @Test
        @DisplayName("Should reject review if sub-order is NO_SHOW")
        void shouldRejectIfNoShow() {
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setStatus(SubOrderStatus.NO_SHOW);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Comment", null);

            assertThatThrownBy(() -> createReviewUseCase.execute(subOrderId, request, customerId))
                    .isInstanceOf(InvalidReviewSubOrderStateException.class)
                    .hasMessageContaining("Only completed sub-orders can be reviewed. Current status: NO_SHOW");
        }

        @Test
        @DisplayName("Should reject review if sub-order is CANCELLED, REFUNDED, PARTIALLY_REFUNDED, PENDING, REJECTED, or IN_PROGRESS")
        void shouldRejectIfOtherNonCompletedStatuses() {
            List<SubOrderStatus> invalidStatuses = List.of(
                    SubOrderStatus.PENDING,
                    SubOrderStatus.REJECTED,
                    SubOrderStatus.IN_PROGRESS,
                    SubOrderStatus.CANCELLED,
                    SubOrderStatus.REFUNDED,
                    SubOrderStatus.PARTIALLY_REFUNDED
            );

            for (SubOrderStatus status : invalidStatuses) {
                SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
                subOrder.setId(subOrderId);
                subOrder.setStatus(status);

                when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

                CreateReviewRequest request = new CreateReviewRequest((short) 5, "Comment", null);

                assertThatThrownBy(() -> createReviewUseCase.execute(subOrderId, request, customerId))
                        .isInstanceOf(InvalidReviewSubOrderStateException.class)
                        .hasMessageContaining("Only completed sub-orders can be reviewed");
            }
        }

        @Test
        @DisplayName("FIFO Early Dispatch: SubOrder in CHECKED_IN cannot review even if dispatched early, until COMPLETED")
        void shouldRejectCheckedInEvenIfDispatchedEarlyViaFifo() {
            UUID actualEarlySlotId = UUID.randomUUID();
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(masterOrderId);
            subOrder.setVendorId(vendorId);
            subOrder.setServiceId(serviceId);
            subOrder.setSlotId(UUID.randomUUID()); // Original slot 08:00
            subOrder.setActualSlotId(actualEarlySlotId); // Dispatched early to 07:30 slot
            subOrder.setCheckedInAt(OffsetDateTime.now().minusMinutes(40));
            subOrder.setStatus(SubOrderStatus.CHECKED_IN);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));

            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Chuyến đi tuyệt vời!", List.of());

            // 1. Phải bị từ chối khi đang CHECKED_IN
            assertThatThrownBy(() -> createReviewUseCase.execute(subOrderId, request, customerId))
                    .isInstanceOf(InvalidReviewSubOrderStateException.class)
                    .hasMessageContaining("Only completed sub-orders can be reviewed. Current status: CHECKED_IN");

            // 2. Khi hệ thống Auto-Completion hoàn thành đơn hàng -> SubOrder chuyển sang COMPLETED -> Được phép đánh giá
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customerId);
            when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(reviewRepository.existsBySubOrderId(subOrderId)).thenReturn(false);

            ReviewJpaEntity savedReview = ReviewJpaEntity.builder()
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .vendorId(vendorId)
                    .serviceId(serviceId)
                    .rating((short) 5)
                    .comment("Chuyến đi tuyệt vời!")
                    .isVisible(true)
                    .isFlagged(false)
                    .build();
            savedReview.setId(reviewId);
            savedReview.setCreatedAt(OffsetDateTime.now());

            when(reviewRepository.saveAndFlush(any(ReviewJpaEntity.class))).thenReturn(savedReview);
            when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(new ServiceJpaEntity()));
            when(vendorRepository.findByIdForUpdate(vendorId)).thenReturn(Optional.of(new VendorJpaEntity()));
            when(reviewRepository.countByServiceIdAndIsVisibleTrue(serviceId)).thenReturn(1L);
            when(reviewRepository.getAvgRatingByServiceId(serviceId)).thenReturn(5.0);
            when(reviewRepository.countByVendorIdAndIsVisibleTrue(vendorId)).thenReturn(1L);
            when(reviewRepository.getAvgRatingByVendorId(vendorId)).thenReturn(5.0);

            ReviewResponse response = createReviewUseCase.execute(subOrderId, request, customerId);
            assertThat(response).isNotNull();
            assertThat(response.rating()).isEqualTo((short) 5);
        }

        @Test
        @DisplayName("Multi-SubOrder: Review eligibility is strictly evaluated per SubOrder status, not MasterOrder")
        void shouldEnforceReviewEligibilityPerSubOrderInMultiSubOrderBooking() {
            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customerId);
            when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));

            // SubOrder 1: COMPLETED -> Allowed
            UUID subOrderCompletedId = UUID.randomUUID();
            SubOrderJpaEntity subOrderCompleted = new SubOrderJpaEntity();
            subOrderCompleted.setId(subOrderCompletedId);
            subOrderCompleted.setMasterOrderId(masterOrderId);
            subOrderCompleted.setVendorId(vendorId);
            subOrderCompleted.setServiceId(serviceId);
            subOrderCompleted.setStatus(SubOrderStatus.COMPLETED);

            // SubOrder 2: CHECKED_IN -> Rejected
            UUID subOrderCheckedInId = UUID.randomUUID();
            SubOrderJpaEntity subOrderCheckedIn = new SubOrderJpaEntity();
            subOrderCheckedIn.setId(subOrderCheckedInId);
            subOrderCheckedIn.setMasterOrderId(masterOrderId);
            subOrderCheckedIn.setStatus(SubOrderStatus.CHECKED_IN);

            // SubOrder 3: CONFIRMED -> Rejected
            UUID subOrderConfirmedId = UUID.randomUUID();
            SubOrderJpaEntity subOrderConfirmed = new SubOrderJpaEntity();
            subOrderConfirmed.setId(subOrderConfirmedId);
            subOrderConfirmed.setMasterOrderId(masterOrderId);
            subOrderConfirmed.setStatus(SubOrderStatus.CONFIRMED);

            // SubOrder 4: NO_SHOW -> Rejected
            UUID subOrderNoShowId = UUID.randomUUID();
            SubOrderJpaEntity subOrderNoShow = new SubOrderJpaEntity();
            subOrderNoShow.setId(subOrderNoShowId);
            subOrderNoShow.setMasterOrderId(masterOrderId);
            subOrderNoShow.setStatus(SubOrderStatus.NO_SHOW);

            when(subOrderRepository.findById(subOrderCompletedId)).thenReturn(Optional.of(subOrderCompleted));
            when(subOrderRepository.findById(subOrderCheckedInId)).thenReturn(Optional.of(subOrderCheckedIn));
            when(subOrderRepository.findById(subOrderConfirmedId)).thenReturn(Optional.of(subOrderConfirmed));
            when(subOrderRepository.findById(subOrderNoShowId)).thenReturn(Optional.of(subOrderNoShow));

            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Đánh giá!", List.of());

            // SubOrder 2 (CHECKED_IN) fails
            assertThatThrownBy(() -> createReviewUseCase.execute(subOrderCheckedInId, request, customerId))
                    .isInstanceOf(InvalidReviewSubOrderStateException.class)
                    .hasMessageContaining("CHECKED_IN");

            // SubOrder 3 (CONFIRMED) fails
            assertThatThrownBy(() -> createReviewUseCase.execute(subOrderConfirmedId, request, customerId))
                    .isInstanceOf(InvalidReviewSubOrderStateException.class)
                    .hasMessageContaining("CONFIRMED");

            // SubOrder 4 (NO_SHOW) fails
            assertThatThrownBy(() -> createReviewUseCase.execute(subOrderNoShowId, request, customerId))
                    .isInstanceOf(InvalidReviewSubOrderStateException.class)
                    .hasMessageContaining("NO_SHOW");

            // SubOrder 1 (COMPLETED) succeeds
            ReviewJpaEntity savedReview = ReviewJpaEntity.builder()
                    .subOrderId(subOrderCompletedId)
                    .customerId(customerId)
                    .vendorId(vendorId)
                    .serviceId(serviceId)
                    .rating((short) 5)
                    .comment("Đánh giá!")
                    .isVisible(true)
                    .isFlagged(false)
                    .build();
            savedReview.setId(reviewId);
            savedReview.setCreatedAt(OffsetDateTime.now());

            when(reviewRepository.existsBySubOrderId(subOrderCompletedId)).thenReturn(false);
            when(reviewRepository.saveAndFlush(any(ReviewJpaEntity.class))).thenReturn(savedReview);
            when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(new ServiceJpaEntity()));
            when(vendorRepository.findByIdForUpdate(vendorId)).thenReturn(Optional.of(new VendorJpaEntity()));
            when(reviewRepository.countByServiceIdAndIsVisibleTrue(serviceId)).thenReturn(1L);
            when(reviewRepository.getAvgRatingByServiceId(serviceId)).thenReturn(5.0);
            when(reviewRepository.countByVendorIdAndIsVisibleTrue(vendorId)).thenReturn(1L);
            when(reviewRepository.getAvgRatingByVendorId(vendorId)).thenReturn(5.0);

            ReviewResponse response = createReviewUseCase.execute(subOrderCompletedId, request, customerId);
            assertThat(response).isNotNull();
            assertThat(response.rating()).isEqualTo((short) 5);
        }

        @Test
        @DisplayName("Should reject review if sub-order does not exist")
        void shouldRejectIfSubOrderNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(subOrderRepository.findById(unknownId)).thenReturn(Optional.empty());

            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Comment", null);

            assertThatThrownBy(() -> createReviewUseCase.execute(unknownId, request, customerId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("Sub-order not found");
        }

        @Test
        @DisplayName("Should reject review if customer is not owner")
        void shouldRejectIfNotOwner() {
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(masterOrderId);
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(UUID.randomUUID()); // different customer

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));

            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Comment", null);

            assertThatThrownBy(() -> createReviewUseCase.execute(subOrderId, request, customerId))
                    .isInstanceOf(UnauthorizedReviewAccessException.class)
                    .hasMessageContaining("User is not the owner");
        }

        @Test
        @DisplayName("Should reject duplicate review for same sub-order")
        void shouldRejectDuplicateReview() {
            SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
            subOrder.setId(subOrderId);
            subOrder.setMasterOrderId(masterOrderId);
            subOrder.setVendorId(vendorId);
            subOrder.setServiceId(serviceId);
            subOrder.setStatus(SubOrderStatus.COMPLETED);

            MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
            masterOrder.setId(masterOrderId);
            masterOrder.setCustomerId(customerId);

            when(subOrderRepository.findById(subOrderId)).thenReturn(Optional.of(subOrder));
            when(masterOrderRepository.findById(masterOrderId)).thenReturn(Optional.of(masterOrder));
            when(reviewRepository.existsBySubOrderId(subOrderId)).thenReturn(true);

            CreateReviewRequest request = new CreateReviewRequest((short) 5, "Comment", null);

            assertThatThrownBy(() -> createReviewUseCase.execute(subOrderId, request, customerId))
                    .isInstanceOf(DuplicateReviewException.class)
                    .hasMessageContaining("already been submitted");
        }
    }

    @Nested
    @DisplayName("UpdateReviewUseCase Tests")
    class UpdateReviewTests {

        @Test
        @DisplayName("Should successfully edit review within 7 days")
        void shouldUpdateReviewSuccessfully() {
            ReviewJpaEntity review = ReviewJpaEntity.builder()
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .serviceId(serviceId)
                    .vendorId(vendorId)
                    .rating((short) 3)
                    .comment("Bình thường")
                    .isVisible(true)
                    .build();
            review.setId(reviewId);
            review.setCreatedAt(OffsetDateTime.now().minusDays(2)); // 2 days ago

            when(reviewRepository.findByIdForUpdate(reviewId)).thenReturn(Optional.of(review));
            when(reviewRepository.save(any(ReviewJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

            // Mock rating recalculation
            when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(new ServiceJpaEntity()));
            when(vendorRepository.findByIdForUpdate(vendorId)).thenReturn(Optional.of(new VendorJpaEntity()));

            UpdateReviewRequest request = new UpdateReviewRequest((short) 5, "Sau khi hỗ trợ thì rất ưng ý!", null);
            ReviewResponse response = updateReviewUseCase.execute(reviewId, request, customerId);

            assertThat(response.rating()).isEqualTo((short) 5);
            assertThat(response.comment()).isEqualTo("Sau khi hỗ trợ thì rất ưng ý!");
            verify(reviewRepository).save(review);
        }

        @Test
        @DisplayName("Should reject edit if 7 days have passed")
        void shouldRejectEditAfter7Days() {
            ReviewJpaEntity review = ReviewJpaEntity.builder()
                    .subOrderId(subOrderId)
                    .customerId(customerId)
                    .rating((short) 3)
                    .build();
            review.setId(reviewId);
            review.setCreatedAt(OffsetDateTime.now().minusDays(8)); // 8 days ago

            when(reviewRepository.findByIdForUpdate(reviewId)).thenReturn(Optional.of(review));

            UpdateReviewRequest request = new UpdateReviewRequest((short) 4, "Updated", null);

            assertThatThrownBy(() -> updateReviewUseCase.execute(reviewId, request, customerId))
                    .isInstanceOf(ReviewPeriodExpiredException.class)
                    .hasMessageContaining("within 7 days");
        }

        @Test
        @DisplayName("Should reject edit if user is not author")
        void shouldRejectEditIfNotAuthor() {
            ReviewJpaEntity review = ReviewJpaEntity.builder()
                    .subOrderId(subOrderId)
                    .customerId(UUID.randomUUID()) // different author
                    .build();
            review.setId(reviewId);

            when(reviewRepository.findByIdForUpdate(reviewId)).thenReturn(Optional.of(review));

            UpdateReviewRequest request = new UpdateReviewRequest((short) 4, "Updated", null);

            assertThatThrownBy(() -> updateReviewUseCase.execute(reviewId, request, customerId))
                    .isInstanceOf(UnauthorizedReviewAccessException.class)
                    .hasMessageContaining("not the author");
        }
    }

    @Nested
    @DisplayName("Vendor Reply Tests")
    class VendorReplyTests {

        @Test
        @DisplayName("Vendor should successfully reply to own review")
        void shouldReplySuccessfully() {
            UUID vendorUserId = UUID.randomUUID();
            VendorJpaEntity vendor = new VendorJpaEntity();
            vendor.setId(vendorId);
            vendor.setUserId(vendorUserId);

            ReviewJpaEntity review = ReviewJpaEntity.builder()
                    .vendorId(vendorId)
                    .serviceId(serviceId)
                    .customerId(customerId)
                    .rating((short) 5)
                    .build();
            review.setId(reviewId);

            when(vendorRepository.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));
            when(reviewRepository.findByIdForUpdate(reviewId)).thenReturn(Optional.of(review));
            when(reviewRepository.save(any(ReviewJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

            VendorReplyRequest request = new VendorReplyRequest("Cảm ơn quý khách đã trải nghiệm dịch vụ!");
            ReviewResponse response = replyVendorReviewUseCase.execute(reviewId, request, vendorUserId);

            assertThat(response.vendorReply()).isEqualTo("Cảm ơn quý khách đã trải nghiệm dịch vụ!");
            assertThat(response.vendorRepliedAt()).isNotNull();

            ArgumentCaptor<NotificationCommand> commandCaptor = ArgumentCaptor.forClass(NotificationCommand.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(sendNotificationUseCase, times(1)).executeOnce(commandCaptor.capture(), keyCaptor.capture());

            NotificationCommand command = commandCaptor.getValue();
            assertThat(command.recipientId()).isEqualTo(customerId);
            assertThat(command.type()).isEqualTo("REVIEW_REPLY");
            assertThat(command.channel()).isEqualTo(NotificationChannel.IN_APP);
            assertThat(keyCaptor.getValue()).isEqualTo("vendor_review_reply:" + reviewId);
        }

        @ParameterizedTest
        @MethodSource("localizedReplyCases")
        void rendersNotificationForCustomerSavedLanguage(String locale, String vendorName, String name,
                String nameEn, String title, String body) {
            UUID vendorUserId = UUID.randomUUID();
            VendorJpaEntity vendor = new VendorJpaEntity();
            vendor.setId(vendorId);
            vendor.setBusinessName(vendorName);
            ReviewJpaEntity review = ReviewJpaEntity.builder()
                    .vendorId(vendorId).serviceId(serviceId).customerId(customerId).subOrderId(subOrderId)
                    .rating((short) 5).build();
            review.setId(reviewId);
            ServiceJpaEntity service = new ServiceJpaEntity();
            service.setName(name);
            service.setNameEn(nameEn);
            User customer = new User();
            customer.setLocale(locale);
            AccountInternalApi accounts = mock(AccountInternalApi.class);
            JpaNotificationRepository notifications = mock(JpaNotificationRepository.class);
            SendNotificationUseCase sender = new SendNotificationUseCase(notifications, null, accounts,
                    LocalizedMessageService.standalone());
            ReplyVendorReviewUseCase useCase = new ReplyVendorReviewUseCase(
                    reviewRepository, vendorRepository, reviewMapper, sender, serviceRepository);
            when(vendorRepository.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));
            when(reviewRepository.findByIdForUpdate(reviewId)).thenReturn(Optional.of(review));
            when(reviewRepository.save(review)).thenReturn(review);
            when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
            when(accounts.findUserById(customerId)).thenReturn(Optional.of(customer));

            LocaleContextHolder.setLocale(Locale.forLanguageTag("en".equals(locale) ? "vi" : "en"));
            try {
                useCase.execute(reviewId, new VendorReplyRequest("Hẹn gặp lại!"), vendorUserId);
            } finally {
                LocaleContextHolder.resetLocaleContext();
            }

            verify(notifications).insertInAppOnce(any(UUID.class), eq(customerId), eq("REVIEW_REPLY"),
                    eq(title), eq(body), eq(locale), eq("SUB_ORDER"), eq(subOrderId), any(OffsetDateTime.class),
                    eq("vendor_review_reply:" + reviewId));
        }

        static Stream<Arguments> localizedReplyCases() {
            String enTitle = "Review response from operator";
            String viTitle = "Phản hồi đánh giá từ nhà cung cấp";
            return Stream.of(
                    Arguments.of("en", "Cano Sông Hàn", "Tour cano", "Speedboat tour", enTitle,
                            "Operator Cano Sông Hàn responded to your review for service \"Speedboat tour\"."),
                    Arguments.of("vi", "Cano Sông Hàn", "Tour cano", "Speedboat tour", viTitle,
                            "Nhà cung cấp Cano Sông Hàn đã phản hồi đánh giá của bạn cho dịch vụ \"Tour cano\"."),
                    Arguments.of("en", " ", "Tour cano", "", enTitle,
                            "The operator responded to your review for service \"Tour cano\"."),
                    Arguments.of("en", "Cano Sông Hàn", " ", null, enTitle,
                            "Operator Cano Sông Hàn responded to your review."),
                    Arguments.of("vi", null, " ", " ", viTitle,
                            "Nhà cung cấp đã phản hồi đánh giá của bạn."),
                    Arguments.of("en", null, " ", " ", enTitle,
                            "The operator responded to your review.")
            );
        }

        @Test
        @DisplayName("Vendor cannot reply to review of another vendor")
        void shouldRejectReplyToOtherVendorReview() {
            UUID vendorUserId = UUID.randomUUID();
            VendorJpaEntity vendor = new VendorJpaEntity();
            vendor.setId(UUID.randomUUID()); // Different vendor
            vendor.setUserId(vendorUserId);

            ReviewJpaEntity review = ReviewJpaEntity.builder()
                    .vendorId(vendorId)
                    .build();
            review.setId(reviewId);

            when(vendorRepository.findByUserId(vendorUserId)).thenReturn(Optional.of(vendor));
            when(reviewRepository.findByIdForUpdate(reviewId)).thenReturn(Optional.of(review));

            VendorReplyRequest request = new VendorReplyRequest("Phản hồi");

            assertThatThrownBy(() -> replyVendorReviewUseCase.execute(reviewId, request, vendorUserId))
                    .isInstanceOf(UnauthorizedReviewAccessException.class)
                    .hasMessageContaining("Vendor does not own the service");

            verifyNoInteractions(sendNotificationUseCase);
        }
    }

    @Nested
    @DisplayName("Admin Visibility & Rating Recalculation Tests")
    class AdminVisibilityTests {

        @Test
        @DisplayName("Hiding review should recalculate service and vendor ratings, and adjust badge")
        void shouldRecalculateWhenReviewHidden() {
            ReviewJpaEntity review = ReviewJpaEntity.builder()
                    .serviceId(serviceId)
                    .vendorId(vendorId)
                    .customerId(customerId)
                    .rating((short) 5)
                    .isVisible(true)
                    .build();
            review.setId(reviewId);

            when(reviewRepository.findByIdForUpdate(reviewId)).thenReturn(Optional.of(review));
            when(reviewRepository.save(any(ReviewJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

            // Setup entities for recalculation
            ServiceJpaEntity service = new ServiceJpaEntity();
            service.setId(serviceId);
            service.setAvgRating(new BigDecimal("5.00"));
            service.setRatingCount(1);
            when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(service));

            VendorJpaEntity vendor = new VendorJpaEntity();
            vendor.setId(vendorId);
            vendor.setRatingAvg(new BigDecimal("5.00"));
            vendor.setRatingCount(1);
            vendor.setBadgeTier(BadgeTier.VERIFIED);
            when(vendorRepository.findByIdForUpdate(vendorId)).thenReturn(Optional.of(vendor));

            // After hiding, counts drop to 0
            when(reviewRepository.countByServiceIdAndIsVisibleTrue(serviceId)).thenReturn(0L);
            when(reviewRepository.getAvgRatingByServiceId(serviceId)).thenReturn(null);
            when(reviewRepository.countByVendorIdAndIsVisibleTrue(vendorId)).thenReturn(0L);
            when(reviewRepository.getAvgRatingByVendorId(vendorId)).thenReturn(null);

            UpdateReviewVisibilityRequest request = new UpdateReviewVisibilityRequest(false, "Nội dung vi phạm tiêu chuẩn");
            ReviewResponse response = updateReviewVisibilityUseCase.execute(reviewId, request);

            assertThat(response.isVisible()).isFalse();
            assertThat(response.moderationNote()).isEqualTo("Nội dung vi phạm tiêu chuẩn");

            // Verify service score updated to 0
            ArgumentCaptor<ServiceJpaEntity> serviceCaptor = ArgumentCaptor.forClass(ServiceJpaEntity.class);
            verify(serviceRepository).save(serviceCaptor.capture());
            assertThat(serviceCaptor.getValue().getAvgRating()).isEqualTo(BigDecimal.ZERO);
            assertThat(serviceCaptor.getValue().getRatingCount()).isEqualTo(0);

            // Verify vendor score updated to 0 and badge NONE
            ArgumentCaptor<VendorJpaEntity> vendorCaptor = ArgumentCaptor.forClass(VendorJpaEntity.class);
            verify(vendorRepository).save(vendorCaptor.capture());
            assertThat(vendorCaptor.getValue().getRatingAvg()).isEqualTo(BigDecimal.ZERO);
            assertThat(vendorCaptor.getValue().getRatingCount()).isEqualTo(0);
            assertThat(vendorCaptor.getValue().getBadgeTier()).isEqualTo(BadgeTier.NONE);
        }

        @Test
        @DisplayName("Badge should become TOP_RATED when count >= 5 and ratingAvg >= 4.5")
        void shouldDeriveTopRatedBadge() {
            VendorJpaEntity vendor = new VendorJpaEntity();
            vendor.setId(vendorId);
            when(vendorRepository.findByIdForUpdate(vendorId)).thenReturn(Optional.of(vendor));

            when(reviewRepository.countByVendorIdAndIsVisibleTrue(vendorId)).thenReturn(5L);
            when(reviewRepository.getAvgRatingByVendorId(vendorId)).thenReturn(4.6);

            reviewRatingService.recalculateRatings(null, vendorId);

            ArgumentCaptor<VendorJpaEntity> captor = ArgumentCaptor.forClass(VendorJpaEntity.class);
            verify(vendorRepository).save(captor.capture());
            assertThat(captor.getValue().getBadgeTier()).isEqualTo(BadgeTier.TOP_RATED);
            assertThat(captor.getValue().getRatingAvg()).isEqualTo(new BigDecimal("4.60"));
            assertThat(captor.getValue().getRatingCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Badge should become VERIFIED when count >= 1 and ratingAvg >= 3.5 but below TOP_RATED threshold")
        void shouldDeriveVerifiedBadge() {
            VendorJpaEntity vendor = new VendorJpaEntity();
            vendor.setId(vendorId);
            when(vendorRepository.findByIdForUpdate(vendorId)).thenReturn(Optional.of(vendor));

            when(reviewRepository.countByVendorIdAndIsVisibleTrue(vendorId)).thenReturn(5L);
            when(reviewRepository.getAvgRatingByVendorId(vendorId)).thenReturn(4.4);

            reviewRatingService.recalculateRatings(null, vendorId);

            ArgumentCaptor<VendorJpaEntity> captor = ArgumentCaptor.forClass(VendorJpaEntity.class);
            verify(vendorRepository).save(captor.capture());
            assertThat(captor.getValue().getBadgeTier()).isEqualTo(BadgeTier.VERIFIED);
        }

        @Test
        @DisplayName("Badge should remain NONE when ratingAvg is below 3.5 despite having visible reviews")
        void shouldKeepNoneBadgeWhenRatingBelowVerifiedThreshold() {
            VendorJpaEntity vendor = new VendorJpaEntity();
            vendor.setId(vendorId);
            when(vendorRepository.findByIdForUpdate(vendorId)).thenReturn(Optional.of(vendor));

            when(reviewRepository.countByVendorIdAndIsVisibleTrue(vendorId)).thenReturn(2L);
            when(reviewRepository.getAvgRatingByVendorId(vendorId)).thenReturn(2.0);

            reviewRatingService.recalculateRatings(null, vendorId);

            ArgumentCaptor<VendorJpaEntity> captor = ArgumentCaptor.forClass(VendorJpaEntity.class);
            verify(vendorRepository).save(captor.capture());
            assertThat(captor.getValue().getBadgeTier()).isEqualTo(BadgeTier.NONE);
        }
    }
}

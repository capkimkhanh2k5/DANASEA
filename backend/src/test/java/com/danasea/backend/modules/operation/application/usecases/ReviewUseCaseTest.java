package com.danasea.backend.modules.operation.application.usecases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.operation.domain.exceptions.DuplicateReviewException;
import com.danasea.backend.modules.operation.domain.exceptions.InvalidReviewSubOrderStateException;
import com.danasea.backend.modules.operation.domain.exceptions.ReviewNotFoundException;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
        reviewMapper = new ReviewMapper(objectMapper, mock(com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository.class));
        reviewRatingService = new ReviewRatingService(reviewRepository, serviceRepository, vendorRepository);

        createReviewUseCase = new CreateReviewUseCase(reviewRepository, subOrderRepository, masterOrderRepository, reviewMapper, reviewRatingService);
        updateReviewUseCase = new UpdateReviewUseCase(reviewRepository, reviewMapper, reviewRatingService);
        flagReviewUseCase = new FlagReviewUseCase(reviewRepository);
        getServiceReviewsUseCase = new GetServiceReviewsUseCase(reviewRepository, reviewMapper);
        getVendorReviewsUseCase = new GetVendorReviewsUseCase(reviewRepository, vendorRepository, reviewMapper);
        replyVendorReviewUseCase = new ReplyVendorReviewUseCase(reviewRepository, vendorRepository, reviewMapper);
        getAdminReviewsUseCase = new GetAdminReviewsUseCase(reviewRepository, reviewMapper);
        updateReviewVisibilityUseCase = new UpdateReviewVisibilityUseCase(reviewRepository, reviewMapper, reviewRatingService);
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
        @DisplayName("Badge should become TOP_RATED when count >= 5 and ratingAvg >= 4.0")
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
        @DisplayName("Badge should become VERIFIED when count >= 1 and not TOP_RATED")
        void shouldDeriveVerifiedBadge() {
            VendorJpaEntity vendor = new VendorJpaEntity();
            vendor.setId(vendorId);
            when(vendorRepository.findByIdForUpdate(vendorId)).thenReturn(Optional.of(vendor));

            when(reviewRepository.countByVendorIdAndIsVisibleTrue(vendorId)).thenReturn(2L);
            when(reviewRepository.getAvgRatingByVendorId(vendorId)).thenReturn(3.5);

            reviewRatingService.recalculateRatings(null, vendorId);

            ArgumentCaptor<VendorJpaEntity> captor = ArgumentCaptor.forClass(VendorJpaEntity.class);
            verify(vendorRepository).save(captor.capture());
            assertThat(captor.getValue().getBadgeTier()).isEqualTo(BadgeTier.VERIFIED);
        }
    }
}

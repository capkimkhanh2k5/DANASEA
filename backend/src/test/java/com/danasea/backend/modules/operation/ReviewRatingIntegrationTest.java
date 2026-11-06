package com.danasea.backend.modules.operation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.account.domain.models.Role;
import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.operation.application.usecases.*;
import com.danasea.backend.modules.operation.domain.models.Review;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.operation.presentation.dtos.*;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

import com.danasea.backend.configs.AbstractExternalInfrastructureIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Review & Rating End-to-End Lifecycle Integration Test")
public class ReviewRatingIntegrationTest extends AbstractExternalInfrastructureIntegrationTest {

    @MockitoBean
    private io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager proxyManager;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaVendorRepository vendorRepository;

    @Autowired
    private JpaServiceRepository serviceRepository;

    @Autowired
    private JpaMasterOrderRepository masterOrderRepository;

    @Autowired
    private JpaSubOrderRepository subOrderRepository;

    @Autowired
    private JpaReviewRepository reviewRepository;

    @Autowired
    private CreateReviewUseCase createReviewUseCase;

    @Autowired
    private UpdateReviewUseCase updateReviewUseCase;

    @Autowired
    private GetServiceReviewsUseCase getServiceReviewsUseCase;

    @Autowired
    private ReplyVendorReviewUseCase replyVendorReviewUseCase;

    @Autowired
    private UpdateReviewVisibilityUseCase updateReviewVisibilityUseCase;

    private UserJpaEntity customer;
    private UserJpaEntity vendorUser;
    private VendorJpaEntity vendor;
    private ServiceJpaEntity service;

    @BeforeEach
    void setUp() {
        // 1. Tạo Customer User
        customer = new UserJpaEntity();
        customer.setEmail("customer_" + UUID.randomUUID() + "@example.com");
        customer.setFullName("Nguyen Van Customer");
        customer.setRole(Role.CUSTOMER);
        customer.setIsEmailVerified(true);
        customer.setIsLocked(false);
        customer = userRepository.save(customer);

        // 2. Tạo Vendor User & Vendor Entity
        vendorUser = new UserJpaEntity();
        vendorUser.setEmail("vendor_" + UUID.randomUUID() + "@example.com");
        vendorUser.setFullName("Tran Van Vendor");
        vendorUser.setRole(Role.VENDOR);
        vendorUser.setIsEmailVerified(true);
        vendorUser.setIsLocked(false);
        vendorUser = userRepository.save(vendorUser);

        vendor = new VendorJpaEntity();
        vendor.setUserId(vendorUser.getId());
        vendor.setBusinessName("Danang Water Sports");
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor.setRatingAvg(BigDecimal.ZERO);
        vendor.setRatingCount(0);
        vendor.setBadgeTier(BadgeTier.NONE);
        vendor = vendorRepository.save(vendor);

        // 3. Tạo Service Entity
        service = new ServiceJpaEntity();
        service.setVendorId(vendor.getId());
        service.setName("Chèo SUP Bãi Bụt Sơn Trà");
        service.setSlug("cheo-sup-bai-but-" + UUID.randomUUID());
        service.setStatus(ServiceStatus.PUBLISHED);
        service.setPrice(new BigDecimal("300000"));
        service.setAvgRating(BigDecimal.ZERO);
        service.setRatingCount(0);
        service = serviceRepository.save(service);
    }

    private SubOrderJpaEntity createCompletedSubOrder(UserJpaEntity orderCustomer) {
        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setCustomerId(orderCustomer.getId());
        masterOrder.setTotalAmount(new BigDecimal("300000"));
        masterOrder = masterOrderRepository.save(masterOrder);

        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setMasterOrderId(masterOrder.getId());
        subOrder.setServiceId(service.getId());
        subOrder.setVendorId(vendor.getId());
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrder.setUnitPrice(new BigDecimal("300000"));
        subOrder.setQuantity(1);
        return subOrderRepository.save(subOrder);
    }

    @Test
    @DisplayName("Complete Review & Rating lifecycle: Create -> Recalculate -> Query -> Vendor Reply -> Admin Hide -> Score Recovery -> TOP_RATED badge")
    void testCompleteReviewAndRatingLifecycle() {
        // Bước 1: Khách hàng tạo đánh giá 5 sao cho SubOrder đã hoàn thành
        SubOrderJpaEntity subOrder1 = createCompletedSubOrder(customer);
        CreateReviewRequest request1 = new CreateReviewRequest(
                (short) 5,
                "Chuyến đi rất tuyệt vời, HDV nhiệt tình!",
                List.of("https://danasea.vn/img1.jpg")
        );

        ReviewResponse review1 = createReviewUseCase.execute(subOrder1.getId(), request1, customer.getId());
        assertThat(review1).isNotNull();
        assertThat(review1.rating()).isEqualTo((short) 5);
        assertThat(review1.customerName()).isEqualTo("Nguyen Van Customer");

        // Kiểm tra tự động cập nhật điểm Service và Vendor
        ServiceJpaEntity updatedService = serviceRepository.findById(service.getId()).orElseThrow();
        assertThat(updatedService.getAvgRating()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(updatedService.getRatingCount()).isEqualTo(1);

        VendorJpaEntity updatedVendor = vendorRepository.findById(vendor.getId()).orElseThrow();
        assertThat(updatedVendor.getRatingAvg()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(updatedVendor.getRatingCount()).isEqualTo(1);
        assertThat(updatedVendor.getBadgeTier()).isEqualTo(BadgeTier.VERIFIED);

        // Bước 2: Truy vấn danh sách đánh giá công khai của Service (GET /api/services/{id}/reviews)
        Page<ReviewResponse> publicReviews = getServiceReviewsUseCase.execute(service.getId(), PageRequest.of(0, 10));
        assertThat(publicReviews.getTotalElements()).isEqualTo(1);
        assertThat(publicReviews.getContent().get(0).comment()).isEqualTo("Chuyến đi rất tuyệt vời, HDV nhiệt tình!");

        // Bước 3: Vendor gửi phản hồi đánh giá
        VendorReplyRequest replyRequest = new VendorReplyRequest("Cảm ơn quý khách đã tin tưởng Danang Water Sports!");
        ReviewResponse repliedReview = replyVendorReviewUseCase.execute(review1.id(), replyRequest, vendorUser.getId());
        assertThat(repliedReview.vendorReply()).isEqualTo("Cảm ơn quý khách đã tin tưởng Danang Water Sports!");
        assertThat(repliedReview.vendorRepliedAt()).isNotNull();

        // Bước 4: Khách hàng chỉnh sửa đánh giá từ 5 sao xuống 4 sao
        UpdateReviewRequest updateRequest = new UpdateReviewRequest((short) 4, "Chuyến đi ổn, thời tiết hơi gắt", null);
        ReviewResponse updatedReview = updateReviewUseCase.execute(review1.id(), updateRequest, customer.getId());
        assertThat(updatedReview.rating()).isEqualTo((short) 4);

        // Kiểm tra điểm số tự động cập nhật xuống 4.00
        updatedService = serviceRepository.findById(service.getId()).orElseThrow();
        assertThat(updatedService.getAvgRating()).isEqualByComparingTo(new BigDecimal("4.00"));

        updatedVendor = vendorRepository.findById(vendor.getId()).orElseThrow();
        assertThat(updatedVendor.getRatingAvg()).isEqualByComparingTo(new BigDecimal("4.00"));

        // Bước 5: Admin ẩn đánh giá do vi phạm chính sách (PATCH /api/admin/reviews/{id}/visibility -> isVisible = false)
        UpdateReviewVisibilityRequest hideRequest = new UpdateReviewVisibilityRequest(false, "Nội dung đang được kiểm tra");
        ReviewResponse hiddenReview = updateReviewVisibilityUseCase.execute(review1.id(), hideRequest);
        assertThat(hiddenReview.isVisible()).isFalse();

        // Kiểm tra: Đánh giá bị ẩn không còn xuất hiện ở danh sách công khai
        Page<ReviewResponse> publicAfterHide = getServiceReviewsUseCase.execute(service.getId(), PageRequest.of(0, 10));
        assertThat(publicAfterHide.getTotalElements()).isEqualTo(0);

        // Kiểm tra: Điểm số của Service và Vendor tự động trừ về 0, badge chuyển về NONE
        updatedService = serviceRepository.findById(service.getId()).orElseThrow();
        assertThat(updatedService.getAvgRating()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(updatedService.getRatingCount()).isEqualTo(0);

        updatedVendor = vendorRepository.findById(vendor.getId()).orElseThrow();
        assertThat(updatedVendor.getRatingAvg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(updatedVendor.getRatingCount()).isEqualTo(0);
        assertThat(updatedVendor.getBadgeTier()).isEqualTo(BadgeTier.NONE);

        // Bước 6: Admin khôi phục hiển thị (isVisible = true)
        UpdateReviewVisibilityRequest unhideRequest = new UpdateReviewVisibilityRequest(true, "Đã kiểm tra hợp lệ");
        updateReviewVisibilityUseCase.execute(review1.id(), unhideRequest);

        updatedService = serviceRepository.findById(service.getId()).orElseThrow();
        assertThat(updatedService.getAvgRating()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(updatedService.getRatingCount()).isEqualTo(1);

        updatedVendor = vendorRepository.findById(vendor.getId()).orElseThrow();
        assertThat(updatedVendor.getRatingAvg()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(updatedVendor.getRatingCount()).isEqualTo(1);
        assertThat(updatedVendor.getBadgeTier()).isEqualTo(BadgeTier.VERIFIED);

        // Bước 7: Thêm 4 đánh giá 5 sao từ các đơn hàng khác để đạt mốc >= 5 đánh giá và avg >= 4.0
        for (int i = 0; i < 4; i++) {
            UserJpaEntity otherCustomer = new UserJpaEntity();
            otherCustomer.setEmail("cust_" + i + "_" + UUID.randomUUID() + "@example.com");
            otherCustomer.setFullName("Customer " + i);
            otherCustomer.setRole(Role.CUSTOMER);
            otherCustomer = userRepository.save(otherCustomer);

            SubOrderJpaEntity otherSubOrder = createCompletedSubOrder(otherCustomer);
            createReviewUseCase.execute(
                    otherSubOrder.getId(),
                    new CreateReviewRequest((short) 5, "Quá đã! Lần " + i, null),
                    otherCustomer.getId()
            );
        }

        // Kiểm tra sau khi có 5 đánh giá: 1 review 4 sao + 4 review 5 sao -> Tổng = 24 / 5 = 4.80
        updatedService = serviceRepository.findById(service.getId()).orElseThrow();
        assertThat(updatedService.getRatingCount()).isEqualTo(5);
        assertThat(updatedService.getAvgRating()).isEqualByComparingTo(new BigDecimal("4.80"));

        updatedVendor = vendorRepository.findById(vendor.getId()).orElseThrow();
        assertThat(updatedVendor.getRatingCount()).isEqualTo(5);
        assertThat(updatedVendor.getRatingAvg()).isEqualByComparingTo(new BigDecimal("4.80"));
        // Đạt điều kiện: ratingAvg >= 4.0 và ratingCount >= 5 -> BadgeTier chuyển thành TOP_RATED!
        assertThat(updatedVendor.getBadgeTier()).isEqualTo(BadgeTier.TOP_RATED);
    }
}

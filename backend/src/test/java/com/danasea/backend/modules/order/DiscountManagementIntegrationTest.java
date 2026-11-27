package com.danasea.backend.modules.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.danasea.backend.modules.order.application.dtos.CreateOrderCommand;
import com.danasea.backend.modules.order.application.dtos.MasterOrderDetailResult;
import com.danasea.backend.modules.order.application.usecases.AdminDiscountCodeUseCase;
import com.danasea.backend.modules.order.application.usecases.CreateOrderUseCase;
import com.danasea.backend.modules.order.application.usecases.DiscountPreviewUseCase;
import com.danasea.backend.modules.order.application.usecases.VendorDiscountCodeUseCase;
import com.danasea.backend.modules.order.domain.exceptions.InvalidDiscountException;
import com.danasea.backend.modules.order.domain.models.DiscountScope;
import com.danasea.backend.modules.order.domain.models.DiscountSponsorType;
import com.danasea.backend.modules.order.domain.models.DiscountType;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.ports.BookingLookupPort;
import com.danasea.backend.modules.order.domain.ports.BookingOrderView;
import com.danasea.backend.modules.order.domain.ports.BookingStatusUpdatePort;
import com.danasea.backend.modules.order.domain.ports.CommissionPolicyPort;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.OrderEventPublisherPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.services.DiscountAllocationEngine;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountCodeJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.DiscountRedemptionJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.mappers.DiscountCodeMapper;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountCodeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaDiscountRedemptionRepository;
import com.danasea.backend.modules.order.presentation.dtos.CreateDiscountCodeRequest;
import com.danasea.backend.modules.order.presentation.dtos.DiscountCodeResponse;
import com.danasea.backend.modules.order.presentation.dtos.DiscountPreviewRequest;
import com.danasea.backend.modules.order.presentation.dtos.DiscountPreviewResponse;
import com.danasea.backend.modules.order.presentation.dtos.UpdateDiscountCodeRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Discount Management Tests (Admin, Vendor, Preview, Order Creation)")
class DiscountManagementIntegrationTest {

    @Mock
    private JpaDiscountCodeRepository discountCodeRepository;

    @Mock
    private JpaDiscountRedemptionRepository discountRedemptionRepository;

    @Mock
    private BookingLookupPort bookingLookupPort;

    @Mock
    private BookingStatusUpdatePort bookingStatusUpdatePort;

    @Mock
    private CommissionPolicyPort commissionPolicyPort;

    @Mock
    private MasterOrderRepositoryPort masterOrderRepository;

    @Mock
    private SubOrderRepositoryPort subOrderRepository;

    @Mock
    private OrderEventPublisherPort orderEventPublisherPort;

    private final DiscountAllocationEngine discountAllocationEngine = new DiscountAllocationEngine();
    private final DiscountCodeMapper discountCodeMapper = new DiscountCodeMapper();

    private AdminDiscountCodeUseCase adminDiscountCodeUseCase;
    private VendorDiscountCodeUseCase vendorDiscountCodeUseCase;
    private DiscountPreviewUseCase discountPreviewUseCase;
    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    void setUp() {
        adminDiscountCodeUseCase = new AdminDiscountCodeUseCase(discountCodeRepository);
        vendorDiscountCodeUseCase = new VendorDiscountCodeUseCase(discountCodeRepository);
        discountPreviewUseCase = new DiscountPreviewUseCase(
                bookingLookupPort,
                discountCodeRepository,
                discountRedemptionRepository,
                commissionPolicyPort,
                discountAllocationEngine,
                discountCodeMapper
        );
        createOrderUseCase = new CreateOrderUseCase(
                masterOrderRepository,
                subOrderRepository,
                bookingLookupPort,
                bookingStatusUpdatePort,
                commissionPolicyPort,
                orderEventPublisherPort,
                discountCodeRepository,
                discountRedemptionRepository,
                discountAllocationEngine,
                discountCodeMapper
        );
    }

    @Nested
    @DisplayName("Admin Discount Code Management")
    class AdminManagementTests {

        @Test
        @DisplayName("Admin tạo voucher sàn thành công")
        void adminCreatePlatformVoucher() {
            CreateDiscountCodeRequest request = new CreateDiscountCodeRequest(
                    "DANASEA50",
                    DiscountScope.PLATFORM,
                    DiscountSponsorType.PLATFORM,
                    null,
                    null,
                    DiscountType.PERCENTAGE,
                    new BigDecimal("10.00"),
                    new BigDecimal("200000.00"),
                    new BigDecimal("50000.00"),
                    100,
                    1,
                    OffsetDateTime.now().minusDays(1),
                    OffsetDateTime.now().plusDays(30),
                    true
            );

            when(discountCodeRepository.existsByCodeIgnoreCase("DANASEA50")).thenReturn(false);
            when(discountCodeRepository.save(any())).thenAnswer(invocation -> {
                DiscountCodeJpaEntity entity = invocation.getArgument(0);
                entity.setId(UUID.randomUUID());
                return entity;
            });

            DiscountCodeResponse response = adminDiscountCodeUseCase.createDiscountCode(request);

            assertThat(response).isNotNull();
            assertThat(response.code()).isEqualTo("DANASEA50");
            assertThat(response.scope()).isEqualTo(DiscountScope.PLATFORM);
            assertThat(response.sponsorType()).isEqualTo(DiscountSponsorType.PLATFORM);
        }

        @Test
        @DisplayName("Admin tạo voucher trùng mã ném lỗi")
        void adminCreateDuplicateCodeThrowsException() {
            CreateDiscountCodeRequest request = new CreateDiscountCodeRequest(
                    "EXISTING",
                    DiscountScope.PLATFORM,
                    null,
                    null,
                    null,
                    DiscountType.FIXED,
                    new BigDecimal("50000.00"),
                    BigDecimal.ZERO,
                    null,
                    null,
                    null,
                    null,
                    null,
                    true
            );

            when(discountCodeRepository.existsByCodeIgnoreCase("EXISTING")).thenReturn(true);

            assertThatThrownBy(() -> adminDiscountCodeUseCase.createDiscountCode(request))
                    .isInstanceOf(InvalidDiscountException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("Vendor Discount Code Management")
    class VendorManagementTests {

        @Test
        @DisplayName("Vendor tạo voucher riêng cho cửa hàng mình (Scope VENDOR)")
        void vendorCreateOwnVoucher() {
            UUID vendorId = UUID.randomUUID();
            CreateDiscountCodeRequest request = new CreateDiscountCodeRequest(
                    "VENDORHOT",
                    DiscountScope.VENDOR,
                    null,
                    null,
                    null,
                    DiscountType.FIXED,
                    new BigDecimal("30000.00"),
                    new BigDecimal("100000.00"),
                    null,
                    50,
                    2,
                    null,
                    null,
                    true
            );

            when(discountCodeRepository.existsByCodeIgnoreCase("VENDORHOT")).thenReturn(false);
            when(discountCodeRepository.save(any())).thenAnswer(invocation -> {
                DiscountCodeJpaEntity entity = invocation.getArgument(0);
                entity.setId(UUID.randomUUID());
                return entity;
            });

            DiscountCodeResponse response = vendorDiscountCodeUseCase.createVendorDiscountCode(vendorId, request);

            assertThat(response).isNotNull();
            assertThat(response.code()).isEqualTo("VENDORHOT");
            assertThat(response.scope()).isEqualTo(DiscountScope.VENDOR);
            assertThat(response.sponsorType()).isEqualTo(DiscountSponsorType.VENDOR);
            assertThat(response.vendorId()).isEqualTo(vendorId);
        }

        @Test
        @DisplayName("Vendor chỉnh sửa voucher của người khác bị chặn 403 (IDOR prevention)")
        void vendorCannotUpdateOtherVendorVoucher() {
            UUID vendorA = UUID.randomUUID();
            UUID vendorB = UUID.randomUUID();
            UUID codeId = UUID.randomUUID();

            DiscountCodeJpaEntity entityB = new DiscountCodeJpaEntity();
            entityB.setId(codeId);
            entityB.setVendorId(vendorB);

            when(discountCodeRepository.findByIdForUpdate(codeId)).thenReturn(Optional.of(entityB));

            UpdateDiscountCodeRequest updateRequest = new UpdateDiscountCodeRequest(
                    new BigDecimal("40000.00"), null, null, null, null, null, null, true
            );

            assertThatThrownBy(() -> vendorDiscountCodeUseCase.updateVendorDiscountCode(vendorA, codeId, updateRequest))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Checkout Discount Preview")
    class DiscountPreviewTests {

        @Test
        @DisplayName("Customer preview voucher hợp lệ và nhận chi tiết phân bổ item")
        void previewValidVoucher() {
            UUID customerId = UUID.randomUUID();
            UUID bookingId = UUID.randomUUID();
            UUID vendorId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();

            BookingOrderView booking = new BookingOrderView(
                    bookingId,
                    customerId,
                    "HOLD",
                    new BigDecimal("500000.00"),
                    OffsetDateTime.now().plusMinutes(15),
                    List.of(new BookingOrderView.BookingItemOrderView(
                            itemId,
                            vendorId,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            1,
                            new BigDecimal("500000.00")
                    ))
            );

            DiscountCodeJpaEntity codeEntity = new DiscountCodeJpaEntity();
            codeEntity.setId(UUID.randomUUID());
            codeEntity.setCode("WELCOME50K");
            codeEntity.setScope(DiscountScope.PLATFORM);
            codeEntity.setSponsorType(DiscountSponsorType.PLATFORM);
            codeEntity.setDiscountType(DiscountType.FIXED);
            codeEntity.setDiscountValue(new BigDecimal("50000.00"));
            codeEntity.setIsActive(true);

            when(bookingLookupPort.findBookingForOrder(bookingId)).thenReturn(Optional.of(booking));
            when(discountCodeRepository.findByCodeIgnoreCase("WELCOME50K")).thenReturn(Optional.of(codeEntity));
            when(discountRedemptionRepository.countByDiscountCodeIdAndCustomerId(codeEntity.getId(), customerId)).thenReturn(0L);
            when(commissionPolicyPort.getCommissionRate(vendorId)).thenReturn(new BigDecimal("0.10"));

            DiscountPreviewResponse preview = discountPreviewUseCase.execute(
                    customerId,
                    new DiscountPreviewRequest(bookingId, "WELCOME50K")
            );

            assertThat(preview.valid()).isTrue();
            assertThat(preview.totalDiscountAmount()).isEqualByComparingTo("50000.00");
            assertThat(preview.finalPayableAmount()).isEqualByComparingTo("450000.00");
            assertThat(preview.itemBreakdown()).hasSize(1);
            assertThat(preview.itemBreakdown().get(0).discountAmount()).isEqualByComparingTo("50000.00");
            assertThat(preview.itemBreakdown().get(0).finalSubtotal()).isEqualByComparingTo("450000.00");
        }
    }

    @Nested
    @DisplayName("Order Creation with Discount Application")
    class OrderCreationWithDiscountTests {

        @Test
        @DisplayName("Tạo đơn hàng áp dụng voucher thành công, phân bổ vào SubOrder và lưu Redemption")
        void createOrderWithDiscount() {
            UUID customerId = UUID.randomUUID();
            UUID bookingId = UUID.randomUUID();
            UUID vendorId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            String idempotencyKey = "order-idem-123";

            BookingOrderView booking = new BookingOrderView(
                    bookingId,
                    customerId,
                    "HOLD",
                    new BigDecimal("1000000.00"),
                    OffsetDateTime.now().plusMinutes(15),
                    List.of(new BookingOrderView.BookingItemOrderView(
                            itemId,
                            vendorId,
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            1,
                            new BigDecimal("1000000.00")
                    ))
            );

            DiscountCodeJpaEntity codeEntity = new DiscountCodeJpaEntity();
            codeEntity.setId(UUID.randomUUID());
            codeEntity.setCode("DISCOUNT10");
            codeEntity.setScope(DiscountScope.PLATFORM);
            codeEntity.setSponsorType(DiscountSponsorType.PLATFORM);
            codeEntity.setDiscountType(DiscountType.PERCENTAGE);
            codeEntity.setDiscountValue(new BigDecimal("10.00")); // 10% = 100,000
            codeEntity.setIsActive(true);

            when(masterOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
            when(masterOrderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)).thenReturn(Optional.empty());
            when(bookingLookupPort.findBookingForOrderForUpdate(bookingId)).thenReturn(Optional.of(booking));
            when(discountCodeRepository.findByCodeForUpdate("DISCOUNT10")).thenReturn(Optional.of(codeEntity));
            when(discountRedemptionRepository.countByDiscountCodeIdAndCustomerId(codeEntity.getId(), customerId)).thenReturn(0L);
            when(discountCodeRepository.incrementUsedCount(codeEntity.getId())).thenReturn(1);
            when(commissionPolicyPort.getCommissionRate(vendorId)).thenReturn(new BigDecimal("0.10"));

            when(masterOrderRepository.save(any())).thenAnswer(invocation -> {
                MasterOrder mo = invocation.getArgument(0);
                mo.setId(UUID.randomUUID());
                return mo;
            });
            when(subOrderRepository.saveAll(any())).thenAnswer(invocation -> {
                List<SubOrder> list = invocation.getArgument(0);
                list.forEach(so -> so.setId(UUID.randomUUID()));
                return list;
            });

            MasterOrderDetailResult result = createOrderUseCase.execute(
                    new CreateOrderCommand(customerId, bookingId, idempotencyKey, "DISCOUNT10")
            );

            assertThat(result).isNotNull();
            assertThat(result.totalAmount()).isEqualByComparingTo("900000.00");
            assertThat(result.discountAmount()).isEqualByComparingTo("100000.00");
            assertThat(result.discountCodeId()).isEqualTo(codeEntity.getId());
            assertThat(result.subOrders().get(0).discountAmount()).isEqualByComparingTo("100000.00");
            assertThat(result.subOrders().get(0).platformDiscountAmount()).isEqualByComparingTo("100000.00");
            assertThat(result.subOrders().get(0).finalAmount()).isEqualByComparingTo("900000.00");
            // Sàn tài trợ -> vendor nhận đủ 900k (1tr - 100k hoa hồng)
            assertThat(result.subOrders().get(0).vendorPayoutAmount()).isEqualByComparingTo("900000.00");

            verify(discountRedemptionRepository, times(1)).save(any(DiscountRedemptionJpaEntity.class));
            verify(discountCodeRepository, times(1)).incrementUsedCount(codeEntity.getId());
        }
    }
}

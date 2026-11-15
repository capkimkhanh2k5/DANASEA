package com.danasea.backend.modules.settlement.application.usecases;

import com.danasea.backend.modules.operation.domain.models.PayoutRequest;
import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.settlement.application.dtos.ApprovePayoutRequest;
import com.danasea.backend.modules.settlement.application.dtos.CreatePayoutRequestRequest;
import com.danasea.backend.modules.settlement.application.dtos.EligibleSettlementPayoutResponse;
import com.danasea.backend.modules.settlement.application.dtos.MarkPaidPayoutRequest;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestDetailResponse;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.application.dtos.RejectPayoutRequest;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidPayoutRequestStateException;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutAmountExceedsBalanceException;
import com.danasea.backend.modules.settlement.domain.exceptions.PayoutRequestNotFoundException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFinalizedException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementNotFoundException;
import com.danasea.backend.modules.settlement.domain.exceptions.UnauthorizedSettlementAccessException;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.PayoutRequestMapper;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutRequestUseCaseTest — Comprehensive Unit Tests for Settlement Payout Flow")
class PayoutRequestUseCaseTest {

    @Mock
    private JpaSettlementRepository settlementRepository;

    @Mock
    private JpaPayoutRequestRepository payoutRequestRepository;

    @Mock
    private VendorInternalApi vendorInternalApi;

    private PayoutRequestMapper mapper;

    private CreatePayoutRequestUseCase createPayoutRequestUseCase;
    private ApprovePayoutRequestUseCase approvePayoutRequestUseCase;
    private RejectPayoutRequestUseCase rejectPayoutRequestUseCase;
    private MarkPayoutPaidUseCase markPayoutPaidUseCase;
    private GetVendorEligibleSettlementsUseCase getVendorEligibleSettlementsUseCase;
    private GetPayoutRequestDetailUseCase getPayoutRequestDetailUseCase;
    private GetVendorPayoutRequestsUseCase getVendorPayoutRequestsUseCase;
    private GetAdminPayoutRequestsUseCase getAdminPayoutRequestsUseCase;

    private UUID vendorId;
    private UUID otherVendorId;
    private UUID settlementId;
    private UUID payoutRequestId;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mapper = new PayoutRequestMapper();

        createPayoutRequestUseCase = new CreatePayoutRequestUseCase(
                payoutRequestRepository,
                settlementRepository,
                mapper
        );

        approvePayoutRequestUseCase = new ApprovePayoutRequestUseCase(
                payoutRequestRepository,
                settlementRepository,
                mapper
        );

        rejectPayoutRequestUseCase = new RejectPayoutRequestUseCase(
                payoutRequestRepository,
                mapper
        );

        markPayoutPaidUseCase = new MarkPayoutPaidUseCase(
                payoutRequestRepository,
                settlementRepository,
                mapper
        );

        getVendorEligibleSettlementsUseCase = new GetVendorEligibleSettlementsUseCase(
                settlementRepository,
                payoutRequestRepository
        );

        getPayoutRequestDetailUseCase = new GetPayoutRequestDetailUseCase(
                payoutRequestRepository,
                settlementRepository,
                vendorInternalApi,
                mapper
        );

        getVendorPayoutRequestsUseCase = new GetVendorPayoutRequestsUseCase(
                payoutRequestRepository,
                mapper
        );

        getAdminPayoutRequestsUseCase = new GetAdminPayoutRequestsUseCase(
                payoutRequestRepository,
                mapper
        );

        vendorId = UUID.randomUUID();
        otherVendorId = UUID.randomUUID();
        settlementId = UUID.randomUUID();
        payoutRequestId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
    }

    private SettlementJpaEntity createSettlement(UUID vId, SettlementStatus status, BigDecimal netPayable) {
        SettlementJpaEntity entity = new SettlementJpaEntity();
        entity.setId(settlementId);
        entity.setVendorId(vId);
        entity.setPeriodStart(LocalDate.of(2026, 9, 1));
        entity.setPeriodEnd(LocalDate.of(2026, 9, 30));
        entity.setGrossAmount(new BigDecimal("10000000.00"));
        entity.setCommissionAmount(new BigDecimal("1000000.00"));
        entity.setNetPayableAmount(netPayable);
        entity.setStatus(status);
        entity.setGeneratedAt(OffsetDateTime.now());
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }

    private PayoutRequestJpaEntity createPayoutRequestEntity(PayoutRequestStatus status, BigDecimal amount) {
        PayoutRequestJpaEntity entity = new PayoutRequestJpaEntity();
        entity.setId(payoutRequestId);
        entity.setSettlementId(settlementId);
        entity.setVendorId(vendorId);
        entity.setAmount(amount);
        entity.setStatus(status);
        entity.setVendorNote("Yêu cầu rút tiền đợt 1");
        entity.setAdminNote(null);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }

    // =========================================================================
    // 1. CREATE PAYOUT REQUEST
    // =========================================================================
    @Nested
    @DisplayName("CreatePayoutRequestUseCase Tests")
    class CreatePayoutRequestTests {

        @Test
        @DisplayName("Tạo Payout Request thành công khi số tiền hợp lệ và kỳ đã FINALIZED")
        void testCreatePayoutRequestSuccess() {
            SettlementJpaEntity settlement = createSettlement(vendorId, SettlementStatus.FINALIZED, new BigDecimal("9000000.00"));
            when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
            when(payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(eq(settlementId), any()))
                    .thenReturn(new BigDecimal("2000000.00")); // Đã cam kết 2tr, còn 7tr

            when(payoutRequestRepository.save(any(PayoutRequestJpaEntity.class))).thenAnswer(invocation -> {
                PayoutRequestJpaEntity saved = invocation.getArgument(0);
                saved.setId(payoutRequestId);
                return saved;
            });

            CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(
                    settlementId,
                    new BigDecimal("5000000.00"),
                    "Rút 5 triệu kỳ tháng 9"
            );

            PayoutRequestResponse response = createPayoutRequestUseCase.execute(vendorId, request);

            assertThat(response).isNotNull();
            assertThat(response.settlementId()).isEqualTo(settlementId);
            assertThat(response.vendorId()).isEqualTo(vendorId);
            assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
            assertThat(response.status()).isEqualTo(PayoutRequestStatus.REQUESTED);
            assertThat(response.note()).isEqualTo("Rút 5 triệu kỳ tháng 9");
            assertThat(response.vendorNote()).isEqualTo("Rút 5 triệu kỳ tháng 9");
            assertThat(response.adminNote()).isNull();
            assertThat(response.rejectionReason()).isNull();
        }

        @Test
        @DisplayName("Ném SettlementNotFinalizedException khi settlement đang ở trạng thái DRAFT")
        void testFailWhenSettlementNotFinalized() {
            SettlementJpaEntity settlement = createSettlement(vendorId, SettlementStatus.DRAFT, new BigDecimal("9000000.00"));
            when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));

            CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(
                    settlementId,
                    new BigDecimal("5000000.00"),
                    null
            );

            assertThatThrownBy(() -> createPayoutRequestUseCase.execute(vendorId, request))
                    .isInstanceOf(SettlementNotFinalizedException.class)
                    .hasMessageContaining("FINALIZED");
        }

        @Test
        @DisplayName("Ném UnauthorizedSettlementAccessException khi vendor không sở hữu settlement")
        void testFailWhenVendorMismatch() {
            SettlementJpaEntity settlement = createSettlement(otherVendorId, SettlementStatus.FINALIZED, new BigDecimal("9000000.00"));
            when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));

            CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(
                    settlementId,
                    new BigDecimal("5000000.00"),
                    null
            );

            assertThatThrownBy(() -> createPayoutRequestUseCase.execute(vendorId, request))
                    .isInstanceOf(UnauthorizedSettlementAccessException.class)
                    .hasMessageContaining("does not own");
        }

        @Test
        @DisplayName("Ném PayoutAmountExceedsBalanceException khi số tiền yêu cầu vượt quá số dư còn lại")
        void testFailWhenAmountExceedsBalance() {
            SettlementJpaEntity settlement = createSettlement(vendorId, SettlementStatus.FINALIZED, new BigDecimal("9000000.00"));
            when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
            when(payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(eq(settlementId), any()))
                    .thenReturn(new BigDecimal("7000000.00")); // Còn lại 2tr

            CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(
                    settlementId,
                    new BigDecimal("3000000.00"), // Yêu cầu 3tr > 2tr
                    null
            );

            assertThatThrownBy(() -> createPayoutRequestUseCase.execute(vendorId, request))
                    .isInstanceOf(PayoutAmountExceedsBalanceException.class)
                    .hasMessageContaining("vượt quá số dư");
        }

        @Test
        @DisplayName("Ném SettlementNotFoundException khi không tìm thấy Settlement")
        void testFailWhenSettlementNotFound() {
            when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.empty());

            CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(
                    settlementId,
                    new BigDecimal("1000000.00"),
                    null
            );

            assertThatThrownBy(() -> createPayoutRequestUseCase.execute(vendorId, request))
                    .isInstanceOf(SettlementNotFoundException.class);
        }
    }

    // =========================================================================
    // 2. APPROVE PAYOUT REQUEST
    // =========================================================================
    @Nested
    @DisplayName("ApprovePayoutRequestUseCase Tests")
    class ApprovePayoutRequestTests {

        @Test
        @DisplayName("Admin duyệt Payout Request thành công chuyển REQUESTED -> APPROVED")
        void testApproveSuccess() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.REQUESTED, new BigDecimal("5000000.00"));
            when(payoutRequestRepository.findByIdForUpdate(payoutRequestId)).thenReturn(Optional.of(entity));
            when(payoutRequestRepository.save(any(PayoutRequestJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

            SettlementJpaEntity settlement = createSettlement(vendorId, SettlementStatus.FINALIZED, new BigDecimal("9000000.00"));
            when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
            when(payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(eq(settlementId), any()))
                    .thenReturn(BigDecimal.ZERO);

            ApprovePayoutRequest request = new ApprovePayoutRequest("Hồ sơ hợp lệ, đã duyệt lệnh chi");

            PayoutRequestResponse response = approvePayoutRequestUseCase.execute(payoutRequestId, adminUserId, request);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PayoutRequestStatus.APPROVED);
            assertThat(response.adminNote()).isEqualTo("Hồ sơ hợp lệ, đã duyệt lệnh chi");
            assertThat(response.vendorNote()).isEqualTo("Yêu cầu rút tiền đợt 1");
            assertThat(entity.getProcessedBy()).isEqualTo(adminUserId);
            assertThat(entity.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("Ném InvalidPayoutRequestStateException khi duyệt yêu cầu không ở trạng thái REQUESTED")
        void testFailWhenNotRequested() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.APPROVED, new BigDecimal("5000000.00"));
            when(payoutRequestRepository.findByIdForUpdate(payoutRequestId)).thenReturn(Optional.of(entity));

            ApprovePayoutRequest request = new ApprovePayoutRequest("Duyệt lại");

            assertThatThrownBy(() -> approvePayoutRequestUseCase.execute(payoutRequestId, adminUserId, request))
                    .isInstanceOf(InvalidPayoutRequestStateException.class)
                    .hasMessageContaining("Chỉ có thể duyệt");
        }

        @Test
        @DisplayName("Ném PayoutRequestNotFoundException khi không tìm thấy yêu cầu chi trả")
        void testFailWhenNotFound() {
            when(payoutRequestRepository.findByIdForUpdate(payoutRequestId)).thenReturn(Optional.empty());

            ApprovePayoutRequest request = new ApprovePayoutRequest("Duyệt");

            assertThatThrownBy(() -> approvePayoutRequestUseCase.execute(payoutRequestId, adminUserId, request))
                    .isInstanceOf(PayoutRequestNotFoundException.class);
        }
    }

    // =========================================================================
    // 3. REJECT PAYOUT REQUEST
    // =========================================================================
    @Nested
    @DisplayName("RejectPayoutRequestUseCase Tests")
    class RejectPayoutRequestTests {

        @Test
        @DisplayName("Admin từ chối Payout Request thành công chuyển REQUESTED -> REJECTED")
        void testRejectSuccess() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.REQUESTED, new BigDecimal("5000000.00"));
            when(payoutRequestRepository.findByIdForUpdate(payoutRequestId)).thenReturn(Optional.of(entity));
            when(payoutRequestRepository.save(any(PayoutRequestJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

            RejectPayoutRequest request = new RejectPayoutRequest(
                    "Thông tin tài khoản ngân hàng không khớp tên doanh nghiệp",
                    "Đã thông báo cho vendor cập nhật"
            );

            PayoutRequestResponse response = rejectPayoutRequestUseCase.execute(payoutRequestId, adminUserId, request);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PayoutRequestStatus.REJECTED);
            assertThat(response.rejectionReason()).isEqualTo("Thông tin tài khoản ngân hàng không khớp tên doanh nghiệp");
            assertThat(response.adminNote()).isEqualTo("Đã thông báo cho vendor cập nhật");
            assertThat(response.vendorNote()).isEqualTo("Yêu cầu rút tiền đợt 1");
            assertThat(entity.getProcessedBy()).isEqualTo(adminUserId);
            assertThat(entity.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("Ném InvalidPayoutRequestStateException khi từ chối yêu cầu đã APPROVED hoặc PAID")
        void testFailWhenAlreadyPaid() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.PAID, new BigDecimal("5000000.00"));
            when(payoutRequestRepository.findByIdForUpdate(payoutRequestId)).thenReturn(Optional.of(entity));

            RejectPayoutRequest request = new RejectPayoutRequest("Lý do từ chối", null);

            assertThatThrownBy(() -> rejectPayoutRequestUseCase.execute(payoutRequestId, adminUserId, request))
                    .isInstanceOf(InvalidPayoutRequestStateException.class)
                    .hasMessageContaining("Không thể từ chối");
        }
    }

    // =========================================================================
    // 4. MARK PAYOUT PAID & SETTLEMENT ROLLUP
    // =========================================================================
    @Nested
    @DisplayName("MarkPayoutPaidUseCase Tests")
    class MarkPayoutPaidTests {

        @Test
        @DisplayName("Ghi nhận PAID thành công; khi chưa giải ngân hết thì Settlement vẫn giữ FINALIZED")
        void testMarkPaidPartialSettlement() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.APPROVED, new BigDecimal("4000000.00"));
            when(payoutRequestRepository.findByIdForUpdate(payoutRequestId)).thenReturn(Optional.of(entity));
            when(payoutRequestRepository.saveAndFlush(any(PayoutRequestJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

            SettlementJpaEntity settlement = createSettlement(vendorId, SettlementStatus.FINALIZED, new BigDecimal("10000000.00"));
            when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
            // Tổng đã trả mới 4tr < 10tr
            when(payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(eq(settlementId), eq(List.of(PayoutRequestStatus.PAID))))
                    .thenReturn(new BigDecimal("4000000.00"));

            MarkPaidPayoutRequest request = new MarkPaidPayoutRequest("FT2611129999", "Chuyển khoản thành công qua VCB");

            PayoutRequestResponse response = markPayoutPaidUseCase.execute(payoutRequestId, adminUserId, request);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PayoutRequestStatus.PAID);
            assertThat(response.bankReference()).isEqualTo("FT2611129999");
            assertThat(response.vendorNote()).isEqualTo("Yêu cầu rút tiền đợt 1");
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FINALIZED); // Vẫn FINALIZED
        }

        @Test
        @DisplayName("Ghi nhận PAID thành công với bankReference null/optional")
        void testMarkPaidWithoutBankReference() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.APPROVED, new BigDecimal("2000000.00"));
            when(payoutRequestRepository.findByIdForUpdate(payoutRequestId)).thenReturn(Optional.of(entity));
            when(payoutRequestRepository.saveAndFlush(any(PayoutRequestJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

            SettlementJpaEntity settlement = createSettlement(vendorId, SettlementStatus.FINALIZED, new BigDecimal("10000000.00"));
            when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
            when(payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(eq(settlementId), eq(List.of(PayoutRequestStatus.PAID))))
                    .thenReturn(new BigDecimal("2000000.00"));

            // Không nhập bankReference
            MarkPaidPayoutRequest request = new MarkPaidPayoutRequest(null, "Chi trả nội bộ không qua cổng ngân hàng");

            PayoutRequestResponse response = markPayoutPaidUseCase.execute(payoutRequestId, adminUserId, request);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PayoutRequestStatus.PAID);
            assertThat(response.bankReference()).isNull();
            assertThat(response.adminNote()).isEqualTo("Chi trả nội bộ không qua cổng ngân hàng");
            assertThat(response.vendorNote()).isEqualTo("Yêu cầu rút tiền đợt 1");
        }

        @Test
        @DisplayName("Ghi nhận PAID thành công; khi tổng số tiền PAID >= netPayableAmount thì Settlement tự chuyển sang PAID")
        void testMarkPaidFullSettlementTransitionsToPaid() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.APPROVED, new BigDecimal("6000000.00"));
            when(payoutRequestRepository.findByIdForUpdate(payoutRequestId)).thenReturn(Optional.of(entity));
            when(payoutRequestRepository.saveAndFlush(any(PayoutRequestJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

            SettlementJpaEntity settlement = createSettlement(vendorId, SettlementStatus.FINALIZED, new BigDecimal("10000000.00"));
            when(settlementRepository.findByIdForUpdate(settlementId)).thenReturn(Optional.of(settlement));
            // Đợt 1 trả 4tr, đợt 2 trả 6tr -> Tổng 10tr >= 10tr
            when(payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(eq(settlementId), eq(List.of(PayoutRequestStatus.PAID))))
                    .thenReturn(new BigDecimal("10000000.00"));

            MarkPaidPayoutRequest request = new MarkPaidPayoutRequest("FT2611129999", "Đã thanh toán đủ toàn bộ kỳ");

            PayoutRequestResponse response = markPayoutPaidUseCase.execute(payoutRequestId, adminUserId, request);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PayoutRequestStatus.PAID);
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID); // Đã chuyển sang PAID!

            verify(settlementRepository).save(settlement);
        }

        @Test
        @DisplayName("Ném InvalidPayoutRequestStateException khi cố ghi nhận PAID cho yêu cầu chưa được APPROVE")
        void testFailWhenNotApproved() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.REQUESTED, new BigDecimal("5000000.00"));
            when(payoutRequestRepository.findByIdForUpdate(payoutRequestId)).thenReturn(Optional.of(entity));

            MarkPaidPayoutRequest request = new MarkPaidPayoutRequest("REF123", null);

            assertThatThrownBy(() -> markPayoutPaidUseCase.execute(payoutRequestId, adminUserId, request))
                    .isInstanceOf(InvalidPayoutRequestStateException.class)
                    .hasMessageContaining("Chỉ có thể ghi nhận chi trả");
        }
    }

    // =========================================================================
    // 5. GET ELIGIBLE SETTLEMENTS
    // =========================================================================
    @Nested
    @DisplayName("GetVendorEligibleSettlementsUseCase Tests")
    class GetVendorEligibleSettlementsTests {

        @Test
        @DisplayName("Chỉ trả về các kỳ Settlement FINALIZED có số dư khả dụng > 0")
        void testGetEligibleSettlementsSuccess() {
            SettlementJpaEntity s1 = createSettlement(vendorId, SettlementStatus.FINALIZED, new BigDecimal("10000000.00"));
            s1.setId(UUID.randomUUID());
            SettlementJpaEntity s2 = createSettlement(vendorId, SettlementStatus.FINALIZED, new BigDecimal("5000000.00"));
            s2.setId(UUID.randomUUID());

            when(settlementRepository.findByVendorIdAndStatus(eq(vendorId), eq(SettlementStatus.FINALIZED), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(s1, s2)));

            // s1 đã yêu cầu 4tr, đã trả 0 -> còn lại 6tr
            when(payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(eq(s1.getId()), eq(List.of(PayoutRequestStatus.REQUESTED, PayoutRequestStatus.APPROVED))))
                    .thenReturn(new BigDecimal("4000000.00"));
            when(payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(eq(s1.getId()), eq(List.of(PayoutRequestStatus.PAID))))
                    .thenReturn(BigDecimal.ZERO);

            // s2 đã cam kết đủ 5tr -> còn lại 0
            when(payoutRequestRepository.sumAmountBySettlementIdAndStatusIn(eq(s2.getId()), any()))
                    .thenReturn(new BigDecimal("5000000.00"));

            List<EligibleSettlementPayoutResponse> results = getVendorEligibleSettlementsUseCase.execute(vendorId);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).settlementId()).isEqualTo(s1.getId());
            assertThat(results.get(0).netPayableAmount()).isEqualByComparingTo(new BigDecimal("10000000.00"));
            assertThat(results.get(0).alreadyRequestedAmount()).isEqualByComparingTo(new BigDecimal("4000000.00"));
            assertThat(results.get(0).remainingEligibleBalance()).isEqualByComparingTo(new BigDecimal("6000000.00"));
        }
    }

    // =========================================================================
    // 6. GET DETAIL USE CASE
    // =========================================================================
    @Nested
    @DisplayName("GetPayoutRequestDetailUseCase Tests")
    class GetPayoutRequestDetailTests {

        @Test
        @DisplayName("Vendor lấy chi tiết Payout Request của chính mình thành công")
        void testVendorGetDetailSuccess() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.REQUESTED, new BigDecimal("3000000.00"));
            when(payoutRequestRepository.findById(payoutRequestId)).thenReturn(Optional.of(entity));

            SettlementJpaEntity settlement = createSettlement(vendorId, SettlementStatus.FINALIZED, new BigDecimal("9000000.00"));
            when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));

            Vendor mockVendor = new Vendor();
            mockVendor.setId(vendorId);
            mockVendor.setBusinessName("Danang Tours Co.");
            mockVendor.setBankName("Vietcombank");
            mockVendor.setBankAccountNumber("0123456789");
            mockVendor.setBankAccountHolder("NGUYEN VAN A");
            when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(mockVendor));

            PayoutRequestDetailResponse detail = getPayoutRequestDetailUseCase.execute(payoutRequestId, vendorId);

            assertThat(detail).isNotNull();
            assertThat(detail.id()).isEqualTo(payoutRequestId);
            assertThat(detail.vendorName()).isEqualTo("Danang Tours Co.");
            assertThat(detail.periodStart()).isEqualTo(settlement.getPeriodStart());
            assertThat(detail.settlementNetPayableAmount()).isEqualByComparingTo(new BigDecimal("9000000.00"));
        }

        @Test
        @DisplayName("Vendor bị từ chối truy cập 403 khi xem Payout Request của Vendor khác")
        void testVendorGetDetailForbidden() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.REQUESTED, new BigDecimal("3000000.00"));
            when(payoutRequestRepository.findById(payoutRequestId)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> getPayoutRequestDetailUseCase.execute(payoutRequestId, otherVendorId))
                    .isInstanceOf(UnauthorizedSettlementAccessException.class)
                    .hasMessageContaining("does not own");
        }

        @Test
        @DisplayName("Admin (vendorId = null) có thể xem chi tiết bất kỳ Payout Request nào")
        void testAdminGetDetailSuccess() {
            PayoutRequestJpaEntity entity = createPayoutRequestEntity(PayoutRequestStatus.APPROVED, new BigDecimal("3000000.00"));
            when(payoutRequestRepository.findById(payoutRequestId)).thenReturn(Optional.of(entity));

            SettlementJpaEntity settlement = createSettlement(vendorId, SettlementStatus.FINALIZED, new BigDecimal("9000000.00"));
            when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));

            Vendor mockVendor = new Vendor();
            mockVendor.setId(vendorId);
            mockVendor.setBusinessName("Danang Tours Co.");
            when(vendorInternalApi.findById(vendorId)).thenReturn(Optional.of(mockVendor));

            PayoutRequestDetailResponse detail = getPayoutRequestDetailUseCase.execute(payoutRequestId, null);

            assertThat(detail).isNotNull();
            assertThat(detail.id()).isEqualTo(payoutRequestId);
        }
    }
}

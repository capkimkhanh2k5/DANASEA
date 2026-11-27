package com.danasea.backend.modules.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentOrderStatus;
import com.danasea.backend.modules.order.domain.models.PaymentProvider;
import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.PaymentJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.report.application.ports.MasterOrderRecord;
import com.danasea.backend.modules.report.application.ports.PaymentRecord;
import com.danasea.backend.modules.report.application.ports.RefundRecord;
import com.danasea.backend.modules.report.application.ports.ReviewRecord;
import com.danasea.backend.modules.report.application.ports.ServiceSlotRecord;
import com.danasea.backend.modules.report.application.ports.SubOrderRecord;
import com.danasea.backend.modules.report.application.ports.VendorRecord;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.infrastructure.persistence.JpaReportDataAdapter;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Collections;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaReportDataAdapter Persistence Layer Tests")
class JpaReportDataAdapterTest {

    @Mock private EntityManager entityManager;

    @Mock private TypedQuery<SubOrderJpaEntity> subOrderQuery;

    @Mock private TypedQuery<MasterOrderJpaEntity> masterOrderQuery;

    @Mock private TypedQuery<PaymentJpaEntity> paymentQuery;

    @Mock private TypedQuery<RefundJpaEntity> refundQuery;

    @Mock private TypedQuery<Object[]> objectArrayQuery;

    @Mock private TypedQuery<VendorJpaEntity> vendorQuery;

    @Mock private TypedQuery<ReviewJpaEntity> reviewQuery;

    private JpaReportDataAdapter adapter;
    private TimeRange timeRange;

    @BeforeEach
    void setUp() {
        adapter = new JpaReportDataAdapter(entityManager);
        timeRange = TimeRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8));
    }

    @Test
    @DisplayName("findSubOrders không truyền vendorId (Admin toàn hệ thống)")
    void shouldFindSubOrdersWithoutVendorId() {
        SubOrderJpaEntity entity = new SubOrderJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setMasterOrderId(UUID.randomUUID());
        entity.setVendorId(UUID.randomUUID());
        entity.setServiceId(UUID.randomUUID());
        entity.setSlotId(UUID.randomUUID());
        entity.setQuantity(2);
        entity.setUnitPrice(BigDecimal.valueOf(500_000));
        entity.setSubtotalAmount(BigDecimal.valueOf(1_000_000));
        entity.setCommissionRate(BigDecimal.valueOf(0.10));
        entity.setCommissionAmount(BigDecimal.valueOf(100_000));
        entity.setVendorPayoutAmount(BigDecimal.valueOf(900_000));
        entity.setStatus(SubOrderStatus.COMPLETED);
        entity.setCreatedAt(OffsetDateTime.now());

        when(entityManager.createQuery(anyString(), eq(SubOrderJpaEntity.class)))
                .thenReturn(subOrderQuery);
        when(subOrderQuery.setParameter("start", timeRange.getStartDateTime()))
                .thenReturn(subOrderQuery);
        when(subOrderQuery.setParameter("end", timeRange.getEndExclusiveDateTime()))
                .thenReturn(subOrderQuery);
        when(subOrderQuery.getResultList()).thenReturn(List.of(entity));

        List<SubOrderRecord> results = adapter.findSubOrders(timeRange, null);

        assertThat(results).hasSize(1);
        SubOrderRecord record = results.getFirst();
        assertThat(record.id()).isEqualTo(entity.getId());
        assertThat(record.subtotalAmount()).isEqualByComparingTo("1000000");
        assertThat(record.vendorPayoutAmount()).isEqualByComparingTo("900000");
        assertThat(record.status()).isEqualTo(SubOrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("findSubOrders có truyền vendorId (Vendor cụ thể)")
    void shouldFindSubOrdersWithVendorId() {
        UUID vendorId = UUID.randomUUID();

        when(entityManager.createQuery(anyString(), eq(SubOrderJpaEntity.class)))
                .thenReturn(subOrderQuery);
        when(subOrderQuery.setParameter("start", timeRange.getStartDateTime()))
                .thenReturn(subOrderQuery);
        when(subOrderQuery.setParameter("end", timeRange.getEndExclusiveDateTime()))
                .thenReturn(subOrderQuery);
        when(subOrderQuery.setParameter("vendorId", vendorId)).thenReturn(subOrderQuery);
        when(subOrderQuery.getResultList()).thenReturn(List.of());

        List<SubOrderRecord> results = adapter.findSubOrders(timeRange, vendorId);

        assertThat(results).isEmpty();
        verify(subOrderQuery).setParameter("vendorId", vendorId);
    }

    @Test
    @DisplayName("findMasterOrders truy xuất đơn đặt MasterOrder theo TimeRange")
    void shouldFindMasterOrders() {
        MasterOrderJpaEntity entity = new MasterOrderJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setBookingId(UUID.randomUUID());
        entity.setCustomerId(UUID.randomUUID());
        entity.setStatus(MasterOrderStatus.PAID);
        entity.setTotalAmount(BigDecimal.valueOf(2_000_000));
        entity.setDiscountAmount(BigDecimal.valueOf(200_000));
        entity.setPaymentStatus(PaymentOrderStatus.PAID);
        entity.setCreatedAt(OffsetDateTime.now());

        when(entityManager.createQuery(anyString(), eq(MasterOrderJpaEntity.class)))
                .thenReturn(masterOrderQuery);
        when(masterOrderQuery.setParameter("start", timeRange.getStartDateTime()))
                .thenReturn(masterOrderQuery);
        when(masterOrderQuery.setParameter("end", timeRange.getEndExclusiveDateTime()))
                .thenReturn(masterOrderQuery);
        when(masterOrderQuery.getResultList()).thenReturn(List.of(entity));

        List<MasterOrderRecord> results = adapter.findMasterOrders(timeRange);

        assertThat(results).hasSize(1);
        MasterOrderRecord record = results.getFirst();
        assertThat(record.id()).isEqualTo(entity.getId());
        assertThat(record.totalAmount()).isEqualByComparingTo("2000000");
        assertThat(record.discountAmount()).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("findMasterOrdersByIds với danh sách ID rỗng trả về danh sách rỗng")
    void shouldReturnEmptyListWhenMasterOrderIdsEmpty() {
        List<MasterOrderRecord> results = adapter.findMasterOrdersByIds(List.of());
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("findPayments truy xuất thanh toán Payment theo TimeRange")
    void shouldFindPayments() {
        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setMasterOrderId(UUID.randomUUID());
        entity.setAmount(BigDecimal.valueOf(1_800_000));
        entity.setStatus(PaymentStatus.SUCCESS);
        entity.setProvider(PaymentProvider.VNPAY);
        entity.setCreatedAt(OffsetDateTime.now());

        when(entityManager.createQuery(anyString(), eq(PaymentJpaEntity.class)))
                .thenReturn(paymentQuery);
        when(paymentQuery.setParameter("start", timeRange.getStartDateTime()))
                .thenReturn(paymentQuery);
        when(paymentQuery.setParameter("end", timeRange.getEndExclusiveDateTime()))
                .thenReturn(paymentQuery);
        when(paymentQuery.getResultList()).thenReturn(List.of(entity));

        List<PaymentRecord> results = adapter.findPayments(timeRange);

        assertThat(results).hasSize(1);
        PaymentRecord record = results.getFirst();
        assertThat(record.id()).isEqualTo(entity.getId());
        assertThat(record.amount()).isEqualByComparingTo("1800000");
        assertThat(record.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(record.provider()).isEqualTo(PaymentProvider.VNPAY);
    }

    @Test
    @DisplayName("findRefunds và countRefundsByReason truy xuất hoàn tiền và phân rã lý do")
    void shouldFindRefundsAndCountByReason() {
        RefundJpaEntity refund = new RefundJpaEntity();
        refund.setId(UUID.randomUUID());
        refund.setSubOrderId(UUID.randomUUID());
        refund.setPaymentId(UUID.randomUUID());
        refund.setAmount(BigDecimal.valueOf(500_000));
        refund.setRefundPercentage(BigDecimal.valueOf(100));
        refund.setReason(RefundReason.WEATHER);
        refund.setStatus(RefundStatus.PROCESSED);
        refund.setCreatedAt(OffsetDateTime.now());

        when(entityManager.createQuery(anyString(), eq(RefundJpaEntity.class)))
                .thenReturn(refundQuery);
        when(refundQuery.setParameter("start", timeRange.getStartDateTime()))
                .thenReturn(refundQuery);
        when(refundQuery.setParameter("end", timeRange.getEndExclusiveDateTime()))
                .thenReturn(refundQuery);
        when(refundQuery.getResultList()).thenReturn(List.of(refund));

        List<RefundRecord> refunds = adapter.findRefunds(timeRange, null);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.getFirst().reason()).isEqualTo(RefundReason.WEATHER);
        assertThat(refunds.getFirst().amount()).isEqualByComparingTo("500000");

        // countRefundsByReason
        Object[] row1 = new Object[] {RefundReason.WEATHER, 5L};
        Object[] row2 = new Object[] {RefundReason.CUSTOMER_CANCEL, 2L};

        when(entityManager.createQuery(anyString(), eq(Object[].class)))
                .thenReturn(objectArrayQuery);
        when(objectArrayQuery.setParameter("start", timeRange.getStartDateTime()))
                .thenReturn(objectArrayQuery);
        when(objectArrayQuery.setParameter("end", timeRange.getEndExclusiveDateTime()))
                .thenReturn(objectArrayQuery);
        when(objectArrayQuery.getResultList()).thenReturn(List.of(row1, row2));

        Map<RefundReason, Long> countMap = adapter.countRefundsByReason(timeRange, null);
        assertThat(countMap).containsEntry(RefundReason.WEATHER, 5L);
        assertThat(countMap).containsEntry(RefundReason.CUSTOMER_CANCEL, 2L);
    }

    @Test
    @DisplayName("findServiceSlots truy xuất các suất dịch vụ trong khoảng thời gian")
    void shouldFindServiceSlots() {
        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setId(UUID.randomUUID());
        slot.setServiceId(UUID.randomUUID());
        slot.setDate(LocalDate.of(2026, 10, 5));
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(10, 0));
        slot.setCapacity(20);
        slot.setBookedCount(16);
        slot.setStatus(SlotStatus.OPEN);

        UUID vendorId = UUID.randomUUID();
        Object[] row = new Object[] {slot, vendorId};

        when(entityManager.createQuery(anyString(), eq(Object[].class)))
                .thenReturn(objectArrayQuery);
        when(objectArrayQuery.setParameter("fromDate", timeRange.getFrom()))
                .thenReturn(objectArrayQuery);
        when(objectArrayQuery.setParameter("toDate", timeRange.getTo()))
                .thenReturn(objectArrayQuery);
        when(objectArrayQuery.getResultList()).thenReturn(Collections.singletonList(row));

        List<ServiceSlotRecord> results = adapter.findServiceSlots(timeRange, null);

        assertThat(results).hasSize(1);
        ServiceSlotRecord record = results.getFirst();
        assertThat(record.id()).isEqualTo(slot.getId());
        assertThat(record.vendorId()).isEqualTo(vendorId);
        assertThat(record.capacity()).isEqualTo(20);
        assertThat(record.bookedCount()).isEqualTo(16);
    }

    @Test
    @DisplayName("findApprovedVendors truy xuất danh sách Vendor được phê duyệt")
    void shouldFindApprovedVendors() {
        VendorJpaEntity v = new VendorJpaEntity();
        v.setId(UUID.randomUUID());
        v.setUserId(UUID.randomUUID());
        v.setBusinessName("Danasea Tours");
        v.setVerificationStatus(VerificationStatus.APPROVED);
        v.setRatingAvg(BigDecimal.valueOf(4.8));
        v.setRatingCount(50);
        v.setBadgeTier(BadgeTier.TOP_RATED);

        when(entityManager.createQuery(anyString(), eq(VendorJpaEntity.class)))
                .thenReturn(vendorQuery);
        when(vendorQuery.setParameter("status", VerificationStatus.APPROVED))
                .thenReturn(vendorQuery);
        when(vendorQuery.getResultList()).thenReturn(List.of(v));

        List<VendorRecord> results = adapter.findApprovedVendors();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().businessName()).isEqualTo("Danasea Tours");
        assertThat(results.getFirst().badgeTier()).isEqualTo(BadgeTier.TOP_RATED);
    }

    @Test
    @DisplayName("findVendorById và findVendorByUserId")
    void shouldFindVendorByIdAndUserId() {
        UUID vendorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        VendorJpaEntity v = new VendorJpaEntity();
        v.setId(vendorId);
        v.setUserId(userId);
        v.setBusinessName("Vendor Marine");
        v.setVerificationStatus(VerificationStatus.APPROVED);

        when(entityManager.find(VendorJpaEntity.class, vendorId)).thenReturn(v);
        Optional<VendorRecord> byId = adapter.findVendorById(vendorId);
        assertThat(byId).isPresent();
        assertThat(byId.get().businessName()).isEqualTo("Vendor Marine");

        when(entityManager.createQuery(anyString(), eq(VendorJpaEntity.class)))
                .thenReturn(vendorQuery);
        when(vendorQuery.setParameter("userId", userId)).thenReturn(vendorQuery);
        when(vendorQuery.setMaxResults(1)).thenReturn(vendorQuery);
        when(vendorQuery.getResultList()).thenReturn(List.of(v));

        Optional<VendorRecord> byUserId = adapter.findVendorByUserId(userId);
        assertThat(byUserId).isPresent();
        assertThat(byUserId.get().id()).isEqualTo(vendorId);
    }

    @Test
    @DisplayName("findReviews truy xuất đánh giá trong TimeRange")
    void shouldFindReviews() {
        ReviewJpaEntity review = new ReviewJpaEntity();
        review.setId(UUID.randomUUID());
        review.setSubOrderId(UUID.randomUUID());
        review.setVendorId(UUID.randomUUID());
        review.setServiceId(UUID.randomUUID());
        review.setRating((short) 5);
        review.setCreatedAt(OffsetDateTime.now());

        when(entityManager.createQuery(anyString(), eq(ReviewJpaEntity.class)))
                .thenReturn(reviewQuery);
        when(reviewQuery.setParameter("start", timeRange.getStartDateTime()))
                .thenReturn(reviewQuery);
        when(reviewQuery.setParameter("end", timeRange.getEndExclusiveDateTime()))
                .thenReturn(reviewQuery);
        when(reviewQuery.getResultList()).thenReturn(List.of(review));

        List<ReviewRecord> results = adapter.findReviews(timeRange, null);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().rating()).isEqualTo((short) 5);
    }
}

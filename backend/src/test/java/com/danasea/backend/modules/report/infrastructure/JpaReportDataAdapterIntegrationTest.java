package com.danasea.backend.modules.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.VerificationStatus;
import com.danasea.backend.modules.vendor.infrastructure.persistence.entities.VendorJpaEntity;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.configs.AbstractExternalInfrastructureIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaReportDataAdapter Real H2 Database Integration Test")
class JpaReportDataAdapterIntegrationTest extends AbstractExternalInfrastructureIntegrationTest {

    @MockitoBean
    private io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager<byte[]> proxyManager;

    @Autowired private JpaReportDataAdapter adapter;

    @Autowired private EntityManager entityManager;

    private UUID vendorId;
    private UUID userId;
    private TimeRange timeRange;

    @BeforeEach
    void setUp() {
        vendorId = UUID.randomUUID();
        userId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        timeRange = TimeRange.of(today.minusDays(1), today.plusDays(1));
    }

    @Test
    @DisplayName("Thực thi truy vấn SubOrder trên cơ sở dữ liệu thật H2")
    void shouldQuerySubOrdersFromRealDatabase() {
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setMasterOrderId(UUID.randomUUID());
        subOrder.setVendorId(vendorId);
        subOrder.setQuantity(2);
        subOrder.setUnitPrice(BigDecimal.valueOf(250_000));
        subOrder.setSubtotalAmount(BigDecimal.valueOf(500_000));
        subOrder.setCommissionRate(BigDecimal.valueOf(0.10));
        subOrder.setCommissionAmount(BigDecimal.valueOf(50_000));
        subOrder.setVendorPayoutAmount(BigDecimal.valueOf(450_000));
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        // BaseJpaEntity createdAt is populated on persist
        entityManager.persist(subOrder);
        entityManager.flush();

        List<SubOrderRecord> allSubOrders = adapter.findSubOrders(timeRange, null);
        assertThat(allSubOrders).isNotEmpty();

        List<SubOrderRecord> vendorSubOrders = adapter.findSubOrders(timeRange, vendorId);
        assertThat(vendorSubOrders).hasSize(1);
        assertThat(vendorSubOrders.getFirst().vendorId()).isEqualTo(vendorId);
        assertThat(vendorSubOrders.getFirst().subtotalAmount()).isEqualByComparingTo("500000");
    }

    @Test
    @DisplayName("Thực thi truy vấn MasterOrder và Payment trên cơ sở dữ liệu thật H2")
    void shouldQueryMasterOrdersAndPayments() {
        MasterOrderJpaEntity masterOrder = new MasterOrderJpaEntity();
        masterOrder.setBookingId(UUID.randomUUID());
        masterOrder.setCustomerId(UUID.randomUUID());
        masterOrder.setStatus(MasterOrderStatus.PAID);
        masterOrder.setTotalAmount(BigDecimal.valueOf(1_000_000));
        masterOrder.setDiscountAmount(BigDecimal.valueOf(100_000));
        masterOrder.setPaymentStatus(PaymentOrderStatus.PAID);
        entityManager.persist(masterOrder);

        PaymentJpaEntity payment = new PaymentJpaEntity();
        payment.setMasterOrderId(masterOrder.getId());
        payment.setAmount(BigDecimal.valueOf(900_000));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProvider(PaymentProvider.VNPAY);
        entityManager.persist(payment);
        entityManager.flush();

        List<MasterOrderRecord> masterOrders = adapter.findMasterOrders(timeRange);
        assertThat(masterOrders).isNotEmpty();

        List<MasterOrderRecord> byIds = adapter.findMasterOrdersByIds(List.of(masterOrder.getId()));
        assertThat(byIds).hasSize(1);
        assertThat(byIds.getFirst().totalAmount()).isEqualByComparingTo("1000000");

        List<PaymentRecord> payments = adapter.findPayments(timeRange);
        assertThat(payments).isNotEmpty();

        List<PaymentRecord> paymentsByMasterOrder =
                adapter.findPaymentsByMasterOrderIds(List.of(masterOrder.getId()));
        assertThat(paymentsByMasterOrder).hasSize(1);
        assertThat(paymentsByMasterOrder.getFirst().amount()).isEqualByComparingTo("900000");
    }

    @Test
    @DisplayName("Thực thi truy vấn Refund và countRefundsByReason trên H2")
    void shouldQueryRefundsAndCountByReason() {
        SubOrderJpaEntity subOrder = new SubOrderJpaEntity();
        subOrder.setMasterOrderId(UUID.randomUUID());
        subOrder.setVendorId(vendorId);
        subOrder.setSubtotalAmount(BigDecimal.valueOf(300_000));
        subOrder.setStatus(SubOrderStatus.REFUNDED);
        entityManager.persist(subOrder);

        RefundJpaEntity refund = new RefundJpaEntity();
        refund.setSubOrderId(subOrder.getId());
        refund.setAmount(BigDecimal.valueOf(300_000));
        refund.setRefundPercentage(BigDecimal.valueOf(100));
        refund.setReason(RefundReason.WEATHER);
        refund.setStatus(RefundStatus.PROCESSED);
        refund.setProcessedAt(OffsetDateTime.now());
        entityManager.persist(refund);
        entityManager.flush();

        List<RefundRecord> refunds = adapter.findRefunds(timeRange, vendorId);
        assertThat(refunds).hasSize(1);
        assertThat(refunds.getFirst().reason()).isEqualTo(RefundReason.WEATHER);

        Map<RefundReason, Long> reasonCounts = adapter.countRefundsByReason(timeRange, vendorId);
        assertThat(reasonCounts).containsEntry(RefundReason.WEATHER, 1L);
    }

    @Test
    @DisplayName("Thực thi truy vấn ServiceSlot và Service trên H2")
    void shouldQueryServiceSlots() {
        ServiceJpaEntity service = new ServiceJpaEntity();
        service.setVendorId(vendorId);
        service.setName("Tour Lặn Biển");
        service.setPrice(BigDecimal.valueOf(500_000));
        service.setStatus(ServiceStatus.PUBLISHED);
        entityManager.persist(service);

        ServiceSlotJpaEntity slot = new ServiceSlotJpaEntity();
        slot.setServiceId(service.getId());
        slot.setDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(11, 0));
        slot.setCapacity(25);
        slot.setBookedCount(20);
        slot.setStatus(SlotStatus.OPEN);
        entityManager.persist(slot);
        entityManager.flush();

        List<ServiceSlotRecord> slots = adapter.findServiceSlots(timeRange, vendorId);
        assertThat(slots).hasSize(1);
        assertThat(slots.getFirst().serviceId()).isEqualTo(service.getId());
        assertThat(slots.getFirst().vendorId()).isEqualTo(vendorId);
        assertThat(slots.getFirst().capacity()).isEqualTo(25);
        assertThat(slots.getFirst().bookedCount()).isEqualTo(20);
    }

    @Test
    @DisplayName("Thực thi truy vấn Vendor và Review trên H2")
    void shouldQueryVendorsAndReviews() {
        VendorJpaEntity vendor = new VendorJpaEntity();
        vendor.setUserId(userId);
        vendor.setBusinessName("Danasea Diving Co.");
        vendor.setVerificationStatus(VerificationStatus.APPROVED);
        vendor.setRatingAvg(BigDecimal.valueOf(4.9));
        vendor.setRatingCount(42);
        vendor.setBadgeTier(BadgeTier.TOP_RATED);
        entityManager.persist(vendor);

        ReviewJpaEntity review = new ReviewJpaEntity();
        review.setSubOrderId(UUID.randomUUID());
        review.setCustomerId(userId);
        review.setVendorId(vendor.getId());
        review.setServiceId(UUID.randomUUID());
        review.setRating((short) 5);
        entityManager.persist(review);
        entityManager.flush();

        List<VendorRecord> approved = adapter.findApprovedVendors();
        assertThat(approved).anyMatch(v -> v.businessName().equals("Danasea Diving Co."));

        Optional<VendorRecord> byId = adapter.findVendorById(vendor.getId());
        assertThat(byId).isPresent();
        assertThat(byId.get().businessName()).isEqualTo("Danasea Diving Co.");

        Optional<VendorRecord> byUserId = adapter.findVendorByUserId(userId);
        assertThat(byUserId).isPresent();
        assertThat(byUserId.get().id()).isEqualTo(vendor.getId());

        List<ReviewRecord> reviews = adapter.findReviews(timeRange, vendor.getId());
        assertThat(reviews).hasSize(1);
        assertThat(reviews.getFirst().rating()).isEqualTo((short) 5);
    }
}

package com.danasea.backend.modules.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
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
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.configs.AbstractExternalInfrastructureIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaReportDataAdapter Adversarial Boundary & Isolation Tests on H2")
class JpaReportDataAdapterAdversarialStressTest extends AbstractExternalInfrastructureIntegrationTest {

    @MockitoBean
    private io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager<byte[]> proxyManager;

    @Autowired private JpaReportDataAdapter adapter;

    @Autowired private EntityManager entityManager;

    private UUID vendorA;
    private UUID vendorB;
    private TimeRange timeRange;

    @BeforeEach
    void setUp() {
        vendorA = UUID.randomUUID();
        vendorB = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        timeRange = TimeRange.of(today.minusDays(1), today.plusDays(1));
    }

    @Test
    @DisplayName(
            "Vendor Isolation: Truy vấn theo Vendor A tuyệt đối không lẫn lộn dữ liệu của Vendor B")
    void shouldStrictlyIsolateDataBetweenVendors() {
        // Tạo sub-order cho Vendor A
        SubOrderJpaEntity subOrderA = new SubOrderJpaEntity();
        subOrderA.setMasterOrderId(UUID.randomUUID());
        subOrderA.setVendorId(vendorA);
        subOrderA.setSubtotalAmount(BigDecimal.valueOf(1_000_000));
        subOrderA.setStatus(SubOrderStatus.COMPLETED);
        entityManager.persist(subOrderA);

        // Tạo sub-order cho Vendor B
        SubOrderJpaEntity subOrderB = new SubOrderJpaEntity();
        subOrderB.setMasterOrderId(UUID.randomUUID());
        subOrderB.setVendorId(vendorB);
        subOrderB.setSubtotalAmount(BigDecimal.valueOf(2_000_000));
        subOrderB.setStatus(SubOrderStatus.COMPLETED);
        entityManager.persist(subOrderB);

        entityManager.flush();

        // Vendor A chỉ thấy subOrderA
        List<SubOrderRecord> resultsA = adapter.findSubOrders(timeRange, vendorA);
        assertThat(resultsA).hasSize(1);
        assertThat(resultsA.getFirst().vendorId()).isEqualTo(vendorA);
        assertThat(resultsA.getFirst().subtotalAmount()).isEqualByComparingTo("1000000");

        // Vendor B chỉ thấy subOrderB
        List<SubOrderRecord> resultsB = adapter.findSubOrders(timeRange, vendorB);
        assertThat(resultsB).hasSize(1);
        assertThat(resultsB.getFirst().vendorId()).isEqualTo(vendorB);
        assertThat(resultsB.getFirst().subtotalAmount()).isEqualByComparingTo("2000000");

        // Admin (vendorId = null) thấy cả 2
        List<SubOrderRecord> resultsAdmin = adapter.findSubOrders(timeRange, null);
        assertThat(resultsAdmin).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Empty & Null Collection Defense: find*ByIds không sinh lỗi cú pháp SQL 'IN ()'")
    void shouldHandleEmptyAndNullCollectionsGracefully() {
        List<MasterOrderRecord> masterOrdersNull = adapter.findMasterOrdersByIds(null);
        assertThat(masterOrdersNull).isEmpty();

        List<MasterOrderRecord> masterOrdersEmpty =
                adapter.findMasterOrdersByIds(Collections.emptyList());
        assertThat(masterOrdersEmpty).isEmpty();

        List<PaymentRecord> paymentsNull = adapter.findPaymentsByMasterOrderIds(null);
        assertThat(paymentsNull).isEmpty();

        List<PaymentRecord> paymentsEmpty =
                adapter.findPaymentsByMasterOrderIds(Collections.emptyList());
        assertThat(paymentsEmpty).isEmpty();

        List<RefundRecord> refundsNull = adapter.findRefundsBySubOrderIds(null);
        assertThat(refundsNull).isEmpty();

        List<RefundRecord> refundsEmpty = adapter.findRefundsBySubOrderIds(Collections.emptyList());
        assertThat(refundsEmpty).isEmpty();
    }

    @Test
    @DisplayName(
            "Unknown UUIDs Defense: Truy vấn ID không tồn tại trả về Optional.empty() và danh sách"
                    + " rỗng")
    void shouldHandleUnknownUuidsWithoutErrors() {
        UUID nonExistentId = UUID.randomUUID();

        Optional<VendorRecord> vendorById = adapter.findVendorById(nonExistentId);
        assertThat(vendorById).isEmpty();

        Optional<VendorRecord> vendorByUserId = adapter.findVendorByUserId(nonExistentId);
        assertThat(vendorByUserId).isEmpty();

        Optional<VendorRecord> vendorNullId = adapter.findVendorById(null);
        assertThat(vendorNullId).isEmpty();

        Optional<VendorRecord> vendorNullUserId = adapter.findVendorByUserId(null);
        assertThat(vendorNullUserId).isEmpty();

        List<SubOrderRecord> subOrders = adapter.findSubOrders(timeRange, nonExistentId);
        assertThat(subOrders).isEmpty();

        List<RefundRecord> refunds = adapter.findRefunds(timeRange, nonExistentId);
        assertThat(refunds).isEmpty();

        Map<RefundReason, Long> refundCounts =
                adapter.countRefundsByReason(timeRange, nonExistentId);
        assertThat(refundCounts).isEmpty();

        List<ServiceSlotRecord> serviceSlots = adapter.findServiceSlots(timeRange, nonExistentId);
        assertThat(serviceSlots).isEmpty();

        List<ReviewRecord> reviews = adapter.findReviews(timeRange, nonExistentId);
        assertThat(reviews).isEmpty();
    }

    @Test
    @DisplayName("TimeRange ngoài biên dữ liệu: Khoảng thời gian tương lai trả về tập kết quả rỗng")
    void shouldHandleFutureTimeRangeWithZeroResults() {
        TimeRange futureRange = TimeRange.of(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 31));

        assertThat(adapter.findSubOrders(futureRange, null)).isEmpty();
        assertThat(adapter.findMasterOrders(futureRange)).isEmpty();
        assertThat(adapter.findPayments(futureRange)).isEmpty();
        assertThat(adapter.findRefunds(futureRange, null)).isEmpty();
        assertThat(adapter.countRefundsByReason(futureRange, null)).isEmpty();
        assertThat(adapter.findServiceSlots(futureRange, null)).isEmpty();
        assertThat(adapter.findReviews(futureRange, null)).isEmpty();
    }
}

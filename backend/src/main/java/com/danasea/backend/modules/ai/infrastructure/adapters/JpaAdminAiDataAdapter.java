package com.danasea.backend.modules.ai.infrastructure.adapters;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiAssessmentCaseRepository;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiCustomerSupportRequestRepository;
import com.danasea.backend.modules.checkin.infrastructure.persistence.entities.CheckinTokenJpaEntity;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.report.application.usecases.GetAdminDashboardUseCase;
import com.danasea.backend.modules.report.application.usecases.GetBookingReportUseCase;
import com.danasea.backend.modules.report.application.usecases.GetRevenueReportUseCase;
import com.danasea.backend.modules.report.application.usecases.GetVendorPerformanceReportUseCase;
import com.danasea.backend.modules.report.domain.enums.GroupByPeriod;
import com.danasea.backend.modules.report.domain.enums.VendorAlertSeverity;
import com.danasea.backend.modules.report.domain.models.BookingReportItem;
import com.danasea.backend.modules.report.domain.models.DashboardMetrics;
import com.danasea.backend.modules.report.domain.models.RevenueReportItem;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorDiagnosisAlert;
import com.danasea.backend.modules.report.domain.models.VendorPerformanceItem;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

@Component
public class JpaAdminAiDataAdapter implements AdminAiDataPort {

    private final GetAdminDashboardUseCase adminDashboardUseCase;
    private final GetRevenueReportUseCase revenueReportUseCase;
    private final GetBookingReportUseCase bookingReportUseCase;
    private final GetVendorPerformanceReportUseCase vendorPerformanceReportUseCase;

    private final JpaDisputeRepository disputeRepo;
    private final JpaSubOrderRepository subOrderRepo;
    private final JpaUserRepository userRepo;
    private final JpaServiceRepository serviceRepo;
    private final JpaServiceSlotRepository slotRepo;
    private final JpaCheckinTokenRepository checkinRepo;
    private final JpaAiAssessmentCaseRepository assessmentCaseRepo;
    private final JpaAiCustomerSupportRequestRepository supportRequestRepo;
    private final VendorInternalApi vendorInternalApi;
    private final JdbcTemplate jdbc;

    public JpaAdminAiDataAdapter(
            GetAdminDashboardUseCase adminDashboardUseCase,
            GetRevenueReportUseCase revenueReportUseCase,
            GetBookingReportUseCase bookingReportUseCase,
            GetVendorPerformanceReportUseCase vendorPerformanceReportUseCase,
            JpaDisputeRepository disputeRepo,
            JpaSubOrderRepository subOrderRepo,
            JpaUserRepository userRepo,
            JpaServiceRepository serviceRepo,
            JpaServiceSlotRepository slotRepo,
            JpaCheckinTokenRepository checkinRepo,
            JpaAiAssessmentCaseRepository assessmentCaseRepo,
            JpaAiCustomerSupportRequestRepository supportRequestRepo,
            VendorInternalApi vendorInternalApi
    ) {
        this(adminDashboardUseCase, revenueReportUseCase, bookingReportUseCase, vendorPerformanceReportUseCase,
                disputeRepo, subOrderRepo, userRepo, serviceRepo, slotRepo, checkinRepo,
                assessmentCaseRepo, supportRequestRepo, vendorInternalApi, null);
    }

    @Autowired
    public JpaAdminAiDataAdapter(
            GetAdminDashboardUseCase adminDashboardUseCase,
            GetRevenueReportUseCase revenueReportUseCase,
            GetBookingReportUseCase bookingReportUseCase,
            GetVendorPerformanceReportUseCase vendorPerformanceReportUseCase,
            JpaDisputeRepository disputeRepo,
            JpaSubOrderRepository subOrderRepo,
            JpaUserRepository userRepo,
            JpaServiceRepository serviceRepo,
            JpaServiceSlotRepository slotRepo,
            JpaCheckinTokenRepository checkinRepo,
            JpaAiAssessmentCaseRepository assessmentCaseRepo,
            JpaAiCustomerSupportRequestRepository supportRequestRepo,
            VendorInternalApi vendorInternalApi,
            @Autowired(required = false) JdbcTemplate jdbc
    ) {
        this.adminDashboardUseCase = adminDashboardUseCase;
        this.revenueReportUseCase = revenueReportUseCase;
        this.bookingReportUseCase = bookingReportUseCase;
        this.vendorPerformanceReportUseCase = vendorPerformanceReportUseCase;
        this.disputeRepo = disputeRepo;
        this.subOrderRepo = subOrderRepo;
        this.userRepo = userRepo;
        this.serviceRepo = serviceRepo;
        this.slotRepo = slotRepo;
        this.checkinRepo = checkinRepo;
        this.assessmentCaseRepo = assessmentCaseRepo;
        this.supportRequestRepo = supportRequestRepo;
        this.vendorInternalApi = vendorInternalApi;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformDashboardMetrics getPlatformDashboardMetrics() {
        LocalDate today = LocalDate.now(TimeRange.VIETNAM_ZONE);
        TimeRange range = TimeRange.of(today.minusDays(30), today);
        DashboardMetrics metrics = adminDashboardUseCase.execute(range, null);

        List<VendorPerformanceItem> vendors = vendorPerformanceReportUseCase.execute(range, null);
        BigDecimal netPayout = revenueReportUseCase.execute(range, GroupByPeriod.DAY, null).stream()
                .map(RevenueReportItem::getNetVendorPayout)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long criticalVendors = vendors.stream()
                .filter(item -> item.getSeverity() == VendorAlertSeverity.CRITICAL)
                .count();
        return new PlatformDashboardMetrics(
                metrics.getTotalRevenue(),
                netPayout,
                metrics.getNetRevenue(),
                metrics.getNewOrders(),
                serviceRepo.countByStatus(ServiceStatus.PUBLISHED),
                vendors.size(),
                criticalVendors,
                metrics.getAlerts().stream().map(VendorDiagnosisAlert::getMessage).toList()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevenueTimeSeriesItem> getRevenueTimeSeries(LocalDate from, LocalDate to, String groupBy) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now(TimeRange.VIETNAM_ZONE).minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now(TimeRange.VIETNAM_ZONE);
        TimeRange range = TimeRange.of(effectiveFrom, effectiveTo);
        GroupByPeriod period = GroupByPeriod.fromString(groupBy);

        List<RevenueReportItem> items = revenueReportUseCase.execute(range, period, null);
        return items.stream().map(item -> new RevenueTimeSeriesItem(
                item.getPeriodKey(),
                item.getGmv(),
                item.getCollectedCash(),
                item.getRefunds(),
                item.getPlatformCommission(),
                item.getNetVendorPayout()
        )).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingCancellationBreakdown getBookingCancellationBreakdown(LocalDate from, LocalDate to, String groupBy) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now(TimeRange.VIETNAM_ZONE).minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now(TimeRange.VIETNAM_ZONE);
        TimeRange range = TimeRange.of(effectiveFrom, effectiveTo);
        GroupByPeriod period = GroupByPeriod.fromString(groupBy);

        List<BookingReportItem> items = bookingReportUseCase.execute(range, period, null);
        long totalBookings = 0;
        long completedBookings = 0;
        long totalCancelled = 0;
        long weather = 0;
        long vendorFault = 0;
        long customer = 0;
        long adminOverride = 0;
        long dispute = 0;
        Map<String, Long> reasons = new HashMap<>();

        for (BookingReportItem item : items) {
            totalBookings += item.getTotalOrders();
            completedBookings += item.getCompletedOrders();
            totalCancelled += item.getCancelledOrders();
            weather += item.getCancellationCount(RefundReason.WEATHER);
            vendorFault += item.getCancellationCount(RefundReason.VENDOR_FAULT);
            customer += item.getCancellationCount(RefundReason.CUSTOMER_CANCEL)
                    + item.getCancellationCount(RefundReason.CUSTOMER_REQUEST);
            adminOverride += item.getCancellationCount(RefundReason.ADMIN_OVERRIDE);
            dispute += item.getCancellationCount(RefundReason.DISPUTE);
            item.getCancellationReasonBreakdown().forEach((reason, count) -> reasons.merge(reason.name(), count, Long::sum));
        }

        return new BookingCancellationBreakdown(
                totalBookings, completedBookings, totalCancelled, weather, vendorFault, customer,
                adminOverride, dispute, reasons
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorDiagnosticItem> getVendorDiagnostics(LocalDate from, LocalDate to, UUID vendorId) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now(TimeRange.VIETNAM_ZONE).minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now(TimeRange.VIETNAM_ZONE);
        TimeRange range = TimeRange.of(effectiveFrom, effectiveTo);

        List<VendorPerformanceItem> items = vendorPerformanceReportUseCase.execute(range, vendorId);
        return items.stream().map(item -> new VendorDiagnosticItem(
                item.getVendorId(),
                item.getBusinessName(),
                item.getRevenue(),
                item.getTotalOrders(),
                item.getCancellationRate(),
                item.getSlotOccupancyRate(),
                item.getRating(),
                item.getRatingCount(),
                item.getSeverity().name(),
                item.getIssues().stream().map(Enum::name).toList()
        )).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DisputeFullEvidence> getDisputeFullEvidence(UUID disputeId) {
        Optional<DisputeJpaEntity> disputeOpt = disputeRepo.findById(disputeId);
        if (disputeOpt.isEmpty()) {
            return Optional.empty();
        }
        DisputeJpaEntity d = disputeOpt.get();
        SubOrderJpaEntity subOrder = subOrderRepo.findById(d.getSubOrderId()).orElse(null);

        ServiceJpaEntity service = subOrder != null && subOrder.getServiceId() != null
                ? serviceRepo.findById(subOrder.getServiceId()).orElse(null) : null;
        ServiceSlotJpaEntity slot = subOrder != null && subOrder.getSlotId() != null
                ? slotRepo.findById(subOrder.getSlotId()).orElse(null) : null;

        OffsetDateTime checkinUsedAt = subOrder == null ? null : checkinRepo.findAllBySubOrderId(subOrder.getId()).stream()
                .map(CheckinTokenJpaEntity::getUsedAt)
                .filter(Objects::nonNull)
                .min(OffsetDateTime::compareTo)
                .orElse(null);
        String vendorName = Optional.ofNullable(subOrder)
                .map(SubOrderJpaEntity::getVendorId)
                .flatMap(vendorInternalApi::findById)
                .map(Vendor::getBusinessName)
                .orElse("Đối tác cung cấp");
        String customerName = Optional.ofNullable(d.getCustomerId())
                .flatMap(userRepo::findById)
                .map(UserJpaEntity::getFullName)
                .orElse("Khách hàng");

        String slotTimeRange = slot != null
                ? slot.getStartTime() + " - " + slot.getEndTime()
                : "Không xác định";

        return Optional.of(new DisputeFullEvidence(
                d.getId(),
                d.getOrderId(),
                d.getSubOrderId(),
                d.getCustomerId(),
                customerName,
                d.getReason() != null ? d.getReason().name() : "OTHER",
                d.getDescription() != null ? d.getDescription() : "",
                d.getEvidenceUrls() != null ? d.getEvidenceUrls() : List.of(),
                d.getStatus() != null ? d.getStatus().name() : "OPEN",
                d.getCreatedAt(),
                service != null ? service.getId() : null,
                service != null ? service.getName() : "Trải nghiệm biển",
                subOrder != null ? subOrder.getVendorId() : null,
                vendorName,
                subOrder != null ? subOrder.getSubtotalAmount() : BigDecimal.ZERO,
                subOrder != null ? subOrder.getQuantity() : 1,
                slot != null ? slot.getId() : null,
                slot != null ? slot.getDate() : null,
                slotTimeRange,
                slot != null && slot.getStatus() != null ? slot.getStatus().name() : "UNKNOWN",
                checkinUsedAt != null,
                checkinUsedAt,
                d.getVendorResponse() != null ? d.getVendorResponse() : "",
                d.getVendorEvidenceUrls() != null ? d.getVendorEvidenceUrls() : List.of(),
                d.getVendorRespondedAt()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public QueueOverview getQueueOverview() {
        long pendingRisk = assessmentCaseRepo.countByStatus("NEEDS_REVIEW");
        long pendingSupport = supportRequestRepo.countByStatus("WAITING_REVIEW");
        long openDisputes = disputeRepo.countByStatus(DisputeStatus.OPEN) + disputeRepo.countByStatus(DisputeStatus.UNDER_REVIEW);

        return new QueueOverview(pendingRisk, pendingSupport, openDisputes);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminOrderDetail> getOrderDetail(UUID orderOrSubOrderId) {
        if (jdbc == null || orderOrSubOrderId == null) return Optional.empty();
        return jdbc.query("""
                SELECT so.id AS sub_order_id, so.master_order_id, so.vendor_id, v.business_name,
                       so.service_id, s.name AS service_title, u.full_name AS customer_name,
                       sl.date AS slot_date, sl.start_time AS slot_start, sl.end_time AS slot_end,
                       so.quantity,
                       COALESCE(so.final_amount, GREATEST(COALESCE(so.subtotal_amount, 0) - COALESCE(so.discount_amount, 0), 0)) AS final_amount,
                       so.commission_amount, so.vendor_payout_amount,
                       so.status, mo.payment_status, so.cancellation_reason, so.checked_in_at, so.created_at
                FROM sub_orders so
                LEFT JOIN vendors v ON v.id = so.vendor_id
                LEFT JOIN services s ON s.id = so.service_id
                LEFT JOIN master_orders mo ON mo.id = so.master_order_id
                LEFT JOIN users u ON u.id = mo.customer_id
                LEFT JOIN service_slots sl ON sl.id = COALESCE(so.actual_slot_id, so.slot_id)
                WHERE so.id = ? OR so.master_order_id = ?
                ORDER BY CASE WHEN so.id = ? THEN 0 ELSE 1 END, so.created_at DESC
                LIMIT 1
                """, (rs, i) -> new AdminOrderDetail(
                rs.getObject("sub_order_id", UUID.class),
                rs.getObject("master_order_id", UUID.class),
                rs.getObject("vendor_id", UUID.class),
                rs.getString("business_name"),
                rs.getObject("service_id", UUID.class),
                rs.getString("service_title"),
                rs.getString("customer_name"),
                rs.getObject("slot_date", LocalDate.class),
                rs.getObject("slot_start", LocalTime.class),
                rs.getObject("slot_end", LocalTime.class),
                (Integer) rs.getObject("quantity"),
                rs.getBigDecimal("final_amount"),
                rs.getBigDecimal("commission_amount"),
                rs.getBigDecimal("vendor_payout_amount"),
                rs.getString("status"),
                rs.getString("payment_status"),
                rs.getString("cancellation_reason"),
                rs.getObject("checked_in_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        ), orderOrSubOrderId, orderOrSubOrderId, orderOrSubOrderId).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminServiceDetail> getServiceDetail(UUID serviceId) {
        if (jdbc == null || serviceId == null) return Optional.empty();
        return jdbc.query("""
                SELECT s.id, s.vendor_id, v.business_name, s.name AS title, c.name AS category_name,
                       s.status, s.price, s.duration_minutes, s.capacity_per_slot, s.avg_rating, s.rating_count
                FROM services s
                LEFT JOIN vendors v ON v.id = s.vendor_id
                LEFT JOIN categories c ON c.id = s.category_id
                WHERE s.id = ?
                """, (rs, i) -> new AdminServiceDetail(
                rs.getObject("id", UUID.class),
                rs.getObject("vendor_id", UUID.class),
                rs.getString("business_name"),
                rs.getString("title"),
                rs.getString("category_name"),
                rs.getString("status"),
                rs.getBigDecimal("price"),
                (Integer) rs.getObject("duration_minutes"),
                (Integer) rs.getObject("capacity_per_slot"),
                rs.getBigDecimal("avg_rating"),
                (Integer) rs.getObject("rating_count")
        ), serviceId).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminVendorDetail> getVendorDetail(UUID vendorId) {
        if (jdbc == null || vendorId == null) return Optional.empty();
        return jdbc.query("""
                SELECT v.id, v.business_name, v.verification_status AS status, v.badge_tier, v.rating_avg, v.rating_count,
                       (SELECT COUNT(*) FROM services s WHERE s.vendor_id = v.id AND s.status = 'PUBLISHED') AS published_services_count
                FROM vendors v
                WHERE v.id = ?
                """, (rs, i) -> new AdminVendorDetail(
                rs.getObject("id", UUID.class),
                rs.getString("business_name"),
                rs.getString("status"),
                rs.getString("badge_tier"),
                rs.getBigDecimal("rating_avg"),
                (Integer) rs.getObject("rating_count"),
                rs.getLong("published_services_count")
        ), vendorId).stream().findFirst();
    }
}

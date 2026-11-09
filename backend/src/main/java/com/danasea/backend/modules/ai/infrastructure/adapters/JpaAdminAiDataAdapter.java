package com.danasea.backend.modules.ai.infrastructure.adapters;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiAssessmentCaseRepository;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiCustomerSupportRequestRepository;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.report.application.usecases.GetAdminDashboardUseCase;
import com.danasea.backend.modules.report.application.usecases.GetBookingReportUseCase;
import com.danasea.backend.modules.report.application.usecases.GetRevenueReportUseCase;
import com.danasea.backend.modules.report.application.usecases.GetVendorPerformanceReportUseCase;
import com.danasea.backend.modules.report.domain.enums.GroupByPeriod;
import com.danasea.backend.modules.report.domain.models.BookingReportItem;
import com.danasea.backend.modules.report.domain.models.DashboardMetrics;
import com.danasea.backend.modules.report.domain.models.RevenueReportItem;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorPerformanceItem;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaAdminAiDataAdapter implements AdminAiDataPort {

    private final GetAdminDashboardUseCase adminDashboardUseCase;
    private final GetRevenueReportUseCase revenueReportUseCase;
    private final GetBookingReportUseCase bookingReportUseCase;
    private final GetVendorPerformanceReportUseCase vendorPerformanceReportUseCase;

    private final JpaDisputeRepository disputeRepo;
    private final JpaSubOrderRepository subOrderRepo;
    private final JpaMasterOrderRepository masterOrderRepo;
    private final JpaServiceRepository serviceRepo;
    private final JpaServiceSlotRepository slotRepo;
    private final JpaCheckinTokenRepository checkinRepo;
    private final JpaAiAssessmentCaseRepository assessmentCaseRepo;
    private final JpaAiCustomerSupportRequestRepository supportRequestRepo;
    private final VendorInternalApi vendorInternalApi;

    @Override
    @Transactional(readOnly = true)
    public PlatformDashboardMetrics getPlatformDashboardMetrics() {
        LocalDate today = LocalDate.now();
        TimeRange range = TimeRange.of(today.minusDays(30), today);
        DashboardMetrics metrics = adminDashboardUseCase.execute(range, null);

        List<String> alerts = metrics.diagnosisAlert() != null && metrics.diagnosisAlert().alertMessages() != null
                ? metrics.diagnosisAlert().alertMessages() : List.of();

        return new PlatformDashboardMetrics(
                metrics.gmv() != null ? metrics.gmv() : BigDecimal.ZERO,
                metrics.netVendorPayout() != null ? metrics.netVendorPayout() : BigDecimal.ZERO,
                metrics.platformCommission() != null ? metrics.platformCommission() : BigDecimal.ZERO,
                metrics.totalBookings(),
                metrics.activeServicesCount(),
                metrics.activeVendorsCount(),
                metrics.criticalVendorsCount(),
                alerts
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevenueTimeSeriesItem> getRevenueTimeSeries(LocalDate from, LocalDate to, String groupBy) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        TimeRange range = TimeRange.of(effectiveFrom, effectiveTo);
        GroupByPeriod period = GroupByPeriod.fromString(groupBy);

        List<RevenueReportItem> items = revenueReportUseCase.execute(range, period, null);
        return items.stream().map(item -> new RevenueTimeSeriesItem(
                item.period(),
                item.gmv() != null ? item.gmv() : BigDecimal.ZERO,
                item.collectedCash() != null ? item.collectedCash() : BigDecimal.ZERO,
                item.refunds() != null ? item.refunds() : BigDecimal.ZERO,
                item.commission() != null ? item.commission() : BigDecimal.ZERO,
                item.netVendorPayout() != null ? item.netVendorPayout() : BigDecimal.ZERO
        )).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingCancellationBreakdown getBookingCancellationBreakdown(LocalDate from, LocalDate to, String groupBy) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
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
            totalBookings += item.totalBookings();
            completedBookings += item.completedBookings();
            totalCancelled += item.cancelledBookings();
            weather += item.weatherCancellations();
            vendorFault += item.vendorFaultCancellations();
            customer += item.customerCancellations();
            adminOverride += item.adminOverrideCancellations();
            dispute += item.disputeCancellations();

            if (item.cancellationsByReason() != null) {
                item.cancellationsByReason().forEach((k, v) -> reasons.merge(k, v, Long::sum));
            }
        }

        return new BookingCancellationBreakdown(
                totalBookings, completedBookings, totalCancelled, weather, vendorFault, customer,
                adminOverride, dispute, reasons
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorDiagnosticItem> getVendorDiagnostics(LocalDate from, LocalDate to, UUID vendorId) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        TimeRange range = TimeRange.of(effectiveFrom, effectiveTo);

        List<VendorPerformanceItem> items = vendorPerformanceReportUseCase.execute(range, vendorId);
        List<VendorDiagnosticItem> result = new ArrayList<>();

        for (VendorPerformanceItem item : items) {
            String severity = item.severity() != null ? item.severity().name() : "HEALTHY";
            List<String> issueTypes = item.issues() != null
                    ? item.issues().stream().map(Enum::name).toList()
                    : List.of();

            result.add(new VendorDiagnosticItem(
                    item.vendorId(),
                    item.businessName() != null ? item.businessName() : "Đối tác",
                    item.gmv() != null ? item.gmv() : BigDecimal.ZERO,
                    item.totalBookings(),
                    item.cancellationRate(),
                    item.slotOccupancyRate(),
                    item.ratingAvg(),
                    item.ratingCount(),
                    severity,
                    issueTypes
            ));
        }
        return result;
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
        MasterOrderJpaEntity masterOrder = d.getOrderId() != null
                ? masterOrderRepo.findById(d.getOrderId()).orElse(null)
                : (subOrder != null ? masterOrderRepo.findById(subOrder.getMasterOrderId()).orElse(null) : null);

        ServiceJpaEntity service = subOrder != null && subOrder.getServiceId() != null
                ? serviceRepo.findById(subOrder.getServiceId()).orElse(null) : null;
        ServiceSlotJpaEntity slot = subOrder != null && subOrder.getSlotId() != null
                ? slotRepo.findById(subOrder.getSlotId()).orElse(null) : null;

        var checkin = subOrder != null ? checkinRepo.findBySubOrderId(subOrder.getId()) : Optional.empty();
        var vendor = subOrder != null && subOrder.getVendorId() != null
                ? vendorInternalApi.findById(subOrder.getVendorId()) : Optional.empty();

        boolean checkinUsed = checkin.isPresent() && checkin.get().getUsedAt() != null;
        OffsetDateTime checkinUsedAt = checkinUsed ? checkin.get().getUsedAt() : null;

        String customerName = "Khách hàng";
        if (masterOrder != null && masterOrder.getCustomer() != null && masterOrder.getCustomer().getFullName() != null) {
            customerName = masterOrder.getCustomer().getFullName();
        }

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
                service != null ? service.getTitle() : "Trải nghiệm biển",
                subOrder != null ? subOrder.getVendorId() : null,
                vendor.isPresent() ? vendor.get().getBusinessName() : "Đối tác cung cấp",
                subOrder != null ? subOrder.getSubtotalAmount() : BigDecimal.ZERO,
                subOrder != null ? subOrder.getQuantity() : 1,
                slot != null ? slot.getId() : null,
                slot != null ? slot.getDate() : LocalDate.now(),
                slotTimeRange,
                slot != null && slot.getStatus() != null ? slot.getStatus().name() : "ACTIVE",
                checkinUsed,
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
}

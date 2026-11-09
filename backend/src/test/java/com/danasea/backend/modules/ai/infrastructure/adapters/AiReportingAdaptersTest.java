package com.danasea.backend.modules.ai.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.ai.application.ports.AdminAiDataPort.QueueOverview;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiAssessmentCaseRepository;
import com.danasea.backend.modules.ai.infrastructure.persistence.repositories.JpaAiCustomerSupportRequestRepository;
import com.danasea.backend.modules.checkin.infrastructure.persistence.entities.CheckinTokenJpaEntity;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.dispute.domain.models.DisputeReason;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaReviewRepository;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.report.application.usecases.GetAdminDashboardUseCase;
import com.danasea.backend.modules.report.application.usecases.GetBookingReportUseCase;
import com.danasea.backend.modules.report.application.usecases.GetRevenueReportUseCase;
import com.danasea.backend.modules.report.application.usecases.GetVendorPerformanceReportUseCase;
import com.danasea.backend.modules.report.domain.enums.GroupByPeriod;
import com.danasea.backend.modules.report.domain.enums.VendorAlertSeverity;
import com.danasea.backend.modules.report.domain.enums.VendorIssueType;
import com.danasea.backend.modules.report.domain.models.BookingReportItem;
import com.danasea.backend.modules.report.domain.models.DashboardMetrics;
import com.danasea.backend.modules.report.domain.models.RevenueReportItem;
import com.danasea.backend.modules.report.domain.models.VendorDiagnosisAlert;
import com.danasea.backend.modules.report.domain.models.VendorPerformanceItem;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

class AiReportingAdaptersTest {
    private final UUID vendorId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 9);
    private final BigDecimal gmv = BigDecimal.valueOf(500);
    private final BigDecimal payout = BigDecimal.valueOf(400);
    private final BigDecimal commission = BigDecimal.valueOf(70);
    private final JpaServiceRepository services = mock(JpaServiceRepository.class);
    private final JpaServiceSlotRepository slots = mock(JpaServiceSlotRepository.class);
    private final JpaCategoryRepository categories = mock(JpaCategoryRepository.class);
    private final JpaReviewRepository reviews = mock(JpaReviewRepository.class);
    private final VendorInternalApi vendors = mock(VendorInternalApi.class);
    private final GetVendorPerformanceReportUseCase vendorReports = mock(GetVendorPerformanceReportUseCase.class);
    private final GetAdminDashboardUseCase dashboards = mock(GetAdminDashboardUseCase.class);
    private final GetRevenueReportUseCase revenues = mock(GetRevenueReportUseCase.class);
    private final GetBookingReportUseCase bookings = mock(GetBookingReportUseCase.class);
    private final JpaDisputeRepository disputes = mock(JpaDisputeRepository.class);
    private final JpaSubOrderRepository subOrders = mock(JpaSubOrderRepository.class);
    private final JpaUserRepository users = mock(JpaUserRepository.class);
    private final JpaCheckinTokenRepository checkins = mock(JpaCheckinTokenRepository.class);
    private final JpaAiAssessmentCaseRepository riskCases = mock(JpaAiAssessmentCaseRepository.class);
    private final JpaAiCustomerSupportRequestRepository support = mock(JpaAiCustomerSupportRequestRepository.class);
    private final JpaVendorAiDataAdapter vendorAdapter =
            new JpaVendorAiDataAdapter(vendors, services, categories, slots, reviews, vendorReports);
    private final JpaAdminAiDataAdapter adminAdapter = new JpaAdminAiDataAdapter(dashboards, revenues, bookings,
            vendorReports, disputes, subOrders, users, services, slots, checkins, riskCases, support, vendors);

    @Test
    void dashboardKeepsGmvCommissionAndPayoutDistinct() {
        var alert = new VendorDiagnosisAlert(vendorId, "Vendor", VendorIssueType.HIGH_CANCELLATION,
                VendorAlertSeverity.CRITICAL, "Check cancellations", 60);
        var item = RevenueReportItem.of("2026-11-09", gmv, BigDecimal.TEN, BigDecimal.ONE, commission, payout, 8);
        var critical = performance("4.9", VendorAlertSeverity.CRITICAL);
        var healthy = performance("4.5", VendorAlertSeverity.HEALTHY);
        when(dashboards.execute(any(), isNull())).thenReturn(DashboardMetrics.of(gmv, commission, 8, 4, 3, List.of(alert)));
        when(vendorReports.execute(any(), isNull())).thenReturn(List.of(critical, healthy));
        when(revenues.execute(any(), eq(GroupByPeriod.DAY), isNull())).thenReturn(List.of(item));
        when(services.countByStatus(ServiceStatus.PUBLISHED)).thenReturn(5L);

        var dashboard = adminAdapter.getPlatformDashboardMetrics();

        assertThat(dashboard.totalGmv()).isEqualTo(gmv);
        assertThat(dashboard.netPayout()).isEqualTo(payout);
        assertThat(dashboard.platformCommission()).isEqualTo(commission);
        assertThat(dashboard.totalBookings()).isEqualTo(8);
        assertThat(dashboard.activeVendors()).isEqualTo(2);
        assertThat(dashboard.activeServices()).isEqualTo(5);
        assertThat(dashboard.criticalVendorsCount()).isEqualTo(1);
        assertThat(dashboard.operationalAlerts()).containsExactly("Check cancellations");
        assertThat(adminAdapter.getRevenueTimeSeries(today, today, "day")).singleElement()
                .satisfies(point -> assertThat(point.collectedCash()).isEqualTo(BigDecimal.valueOf(490)));
        assertThat(adminAdapter.getRevenueTimeSeries(null, null, null)).hasSize(1);
        assertThat(adminAdapter.getVendorDiagnostics(null, null, null)).extracting(row -> row.severity())
                .containsExactly("CRITICAL", "HEALTHY");
        when(vendorReports.execute(any(), eq(vendorId))).thenReturn(List.of(critical));
        assertThat(adminAdapter.getVendorDiagnostics(today, today, vendorId)).singleElement()
                .satisfies(row -> assertThat(row.gmv()).isEqualTo(gmv));
    }

    @Test
    void reportsCombineCancellationCountsAcrossPeriodsAndReadQueueStatuses() {
        var first = new BookingReportItem("day 1", 8, 2, 6, Map.of(
                RefundReason.CUSTOMER_CANCEL, 1L, RefundReason.CUSTOMER_REQUEST, 1L, RefundReason.WEATHER, 4L));
        var second = new BookingReportItem("day 2", 7, 2, 5, Map.of(
                RefundReason.WEATHER, 2L, RefundReason.VENDOR_FAULT, 1L, RefundReason.ADMIN_OVERRIDE, 1L, RefundReason.DISPUTE, 1L));
        when(bookings.execute(any(), eq(GroupByPeriod.DAY), isNull())).thenReturn(List.of(first, second));
        when(riskCases.countByStatus("NEEDS_REVIEW")).thenReturn(4L);
        when(support.countByStatus("WAITING_REVIEW")).thenReturn(5L);
        when(disputes.countByStatus(DisputeStatus.OPEN)).thenReturn(6L);
        when(disputes.countByStatus(DisputeStatus.UNDER_REVIEW)).thenReturn(7L);

        var result = adminAdapter.getBookingCancellationBreakdown(today.minusDays(1), today, "day");

        assertThat(result.totalBookings()).isEqualTo(15);
        assertThat(result.completedBookings()).isEqualTo(4);
        assertThat(result.totalCancelled()).isEqualTo(11);
        assertThat(result.cancellationsByReason()).containsEntry("WEATHER", 6L);
        assertThat(result.customerCancellations()).isEqualTo(2);
        assertThat(result.vendorFaultCancellations()).isEqualTo(1);
        assertThat(result.adminOverrideCancellations()).isEqualTo(1);
        assertThat(result.disputeCancellations()).isEqualTo(1);
        assertThat(adminAdapter.getBookingCancellationBreakdown(null, null, null).weatherCancellations()).isEqualTo(6);
        assertThat(adminAdapter.getQueueOverview()).isEqualTo(new QueueOverview(4, 5, 13));
    }

    @Test
    void disputeReadsUsedTokensAndDoesNotInferFactsFromMissingRows() {
        UUID disputeId = UUID.randomUUID();
        UUID subOrderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        var dispute = new DisputeJpaEntity();
        dispute.setId(disputeId);
        dispute.setSubOrderId(subOrderId);
        assertThat(adminAdapter.getDisputeFullEvidence(disputeId)).isEmpty();
        when(disputes.findById(disputeId)).thenReturn(Optional.of(dispute));

        dispute.setEvidenceUrls(null);
        dispute.setVendorEvidenceUrls(null);
        var missing = adminAdapter.getDisputeFullEvidence(disputeId).orElseThrow();
        assertThat(missing.slotStatus()).isEqualTo("UNKNOWN");
        assertThat(missing.slotDate()).isNull();
        assertThat(missing.checkinUsed()).isFalse();

        var subOrder = new SubOrderJpaEntity();
        subOrder.setId(subOrderId);
        subOrder.setQuantity(2);
        when(subOrders.findById(subOrderId)).thenReturn(Optional.of(subOrder));
        assertThat(adminAdapter.getDisputeFullEvidence(disputeId).orElseThrow().slotDate()).isNull();

        dispute.setCustomerId(customerId);
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        dispute.setReason(DisputeReason.VENDOR_NO_SHOW);
        dispute.setDescription("Operator did not arrive");
        dispute.setEvidenceUrls(List.of("claim.jpg"));
        dispute.setVendorResponse("Staff waited at the pier");
        dispute.setVendorEvidenceUrls(List.of("response.jpg"));
        subOrder.setServiceId(serviceId);
        subOrder.setSlotId(slotId);
        subOrder.setVendorId(vendorId);
        subOrder.setSubtotalAmount(gmv);
        var service = service(ServiceStatus.PUBLISHED, vendorId);
        var slot = slot(SlotStatus.CANCELLED);
        slot.setId(slotId);
        var customer = new UserJpaEntity();
        customer.setFullName("Guest");
        when(users.findById(customerId)).thenReturn(Optional.of(customer));
        when(services.findById(serviceId)).thenReturn(Optional.of(service));
        when(slots.findById(slotId)).thenReturn(Optional.of(slot));
        when(vendors.findById(vendorId)).thenReturn(Optional.of(vendor()));
        var checkedIn = OffsetDateTime.parse("2026-11-09T09:00:00+07:00");
        when(checkins.findAllBySubOrderId(subOrderId)).thenReturn(List.of(
                CheckinTokenJpaEntity.builder().usedAt(checkedIn.plusMinutes(2)).build(),
                CheckinTokenJpaEntity.builder().build(),
                CheckinTokenJpaEntity.builder().usedAt(checkedIn).build()));

        var evidence = adminAdapter.getDisputeFullEvidence(disputeId).orElseThrow();
        assertThat(evidence.checkinUsed()).isTrue();
        assertThat(evidence.checkinUsedAt()).isEqualTo(checkedIn);
        assertThat(evidence.serviceTitle()).isEqualTo("Kayak");
        assertThat(evidence.slotStatus()).isEqualTo("CANCELLED");
        assertThat(evidence.customerName()).isEqualTo("Guest");
        assertThat(evidence.vendorBusinessName()).isEqualTo("Marine vendor");
        assertThat(evidence.vendorEvidenceUrls()).containsExactly("response.jpg");
    }

    @Test
    void vendorCatalogScopesServicesSlotsAndReviews() {
        var service = service(ServiceStatus.PUBLISHED, vendorId);
        var foreign = service(ServiceStatus.PUBLISHED, UUID.randomUUID());
        foreign.setId(UUID.randomUUID());
        var draft = service(ServiceStatus.DRAFT, vendorId);
        UUID categoryId = UUID.randomUUID();
        service.setCategoryId(categoryId);
        service.setAvgRating(new BigDecimal("4.9"));
        service.setRatingCount(12);
        var category = new CategoryJpaEntity();
        category.setName("Kayaking");
        when(categories.findById(categoryId)).thenReturn(Optional.of(category));
        when(services.findById(serviceId)).thenReturn(Optional.of(service));
        when(services.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        when(services.findByVendorIdOrderByCreatedAtDesc(vendorId)).thenReturn(List.of(service, draft));
        when(services.findByVendorId(vendorId)).thenReturn(List.of(service));
        when(slots.findByServiceIdAndDateBetweenOrderByDateAscStartTimeAsc(eq(serviceId), any(), any()))
                .thenReturn(List.of(slot(SlotStatus.CANCELLED), slot(SlotStatus.OPEN)));

        assertThat(vendorAdapter.listServices(vendorId)).singleElement().satisfies(item -> {
            assertThat(item.categoryName()).isEqualTo("Kayaking");
            assertThat(item.reviewCount()).isEqualTo(12);
        });
        assertThat(vendorAdapter.getService(vendorId, foreign.getId())).isEmpty();
        assertThat(vendorAdapter.getService(vendorId, serviceId)).isPresent();
        assertThat(vendorAdapter.listUpcomingSlots(vendorId, foreign.getId(), 3)).isEmpty();
        verify(slots, never()).findByServiceIdAndDateBetweenOrderByDateAscStartTimeAsc(eq(foreign.getId()), any(), any());
        assertThat(vendorAdapter.listUpcomingSlots(vendorId, serviceId, 3)).hasSize(1);
        assertThat(vendorAdapter.listUpcomingSlots(vendorId, null, 3)).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo("OPEN"));

        var review = ReviewJpaEntity.builder().serviceId(serviceId).rating((short) 5).comment("Good guide").build();
        when(services.findAllById(List.of(serviceId))).thenReturn(List.of(service));
        var page = new PageImpl<>(List.of(review));
        when(reviews.findByVendorIdAndServiceIdAndIsVisibleTrue(vendorId, serviceId, PageRequest.of(0, 100))).thenReturn(page);
        when(reviews.findByVendorIdAndIsVisibleTrue(vendorId, PageRequest.of(0, 1))).thenReturn(page);
        assertThat(vendorAdapter.listReviews(vendorId, serviceId, 999)).singleElement()
                .satisfies(item -> assertThat(item.serviceTitle()).isEqualTo("Kayak"));
        assertThat(vendorAdapter.listReviews(vendorId, null, 0)).singleElement()
                .satisfies(item -> assertThat(item.comment()).isEqualTo("Good guide"));
    }

    @Test
    void vendorProfilesAndPerformanceUseStoredValuesAndEmptyDefaults() {
        var vendor = vendor();
        when(vendors.findById(vendorId)).thenReturn(Optional.of(vendor));
        assertThat(vendorAdapter.findProfileByVendorId(vendorId).orElseThrow().badgeTier()).isEqualTo("NONE");
        vendor.setRatingAvg(new BigDecimal("4.9"));
        vendor.setRatingCount(12);
        vendor.setBadgeTier(BadgeTier.TOP_RATED);
        when(vendors.findByUserId(vendor.getUserId())).thenReturn(Optional.of(vendor));
        assertThat(vendorAdapter.findProfileByUserId(vendor.getUserId()).orElseThrow().badgeTier()).isEqualTo("TOP_RATED");
        assertThat(vendorAdapter.getPerformanceMetrics(vendorId, null, null).badgeTier()).isEqualTo("NONE");

        when(vendorReports.execute(any(), eq(vendorId))).thenReturn(
                List.of(performance("4.9", VendorAlertSeverity.HEALTHY)),
                List.of(performance("4.7", VendorAlertSeverity.WARNING)),
                List.of(performance("3.5", VendorAlertSeverity.WARNING)));
        assertThat(vendorAdapter.getPerformanceMetrics(vendorId, today, today).badgeTier()).isEqualTo("TOP_RATED");
        vendor.setBadgeTier(BadgeTier.VERIFIED);
        assertThat(vendorAdapter.getPerformanceMetrics(vendorId, today, today).badgeTier()).isEqualTo("VERIFIED");
        vendor.setBadgeTier(null);
        assertThat(vendorAdapter.getPerformanceMetrics(vendorId, today, today).badgeTier()).isEqualTo("NONE");
    }

    private VendorPerformanceItem performance(String rating, VendorAlertSeverity severity) {
        return new VendorPerformanceItem(vendorId, "Vendor", gmv, payout, 8, 4, 3, 37.5, 80,
                new BigDecimal(rating), 12, severity, List.of(VendorIssueType.HIGH_CANCELLATION));
    }

    private Vendor vendor() {
        var vendor = new Vendor();
        vendor.setId(vendorId);
        vendor.setUserId(UUID.randomUUID());
        vendor.setBusinessName("Marine vendor");
        return vendor;
    }

    private ServiceJpaEntity service(ServiceStatus status, UUID owner) {
        var service = new ServiceJpaEntity();
        service.setId(serviceId);
        service.setStatus(status);
        service.setVendorId(owner);
        service.setName("Kayak");
        return service;
    }

    private ServiceSlotJpaEntity slot(SlotStatus status) {
        var slot = new ServiceSlotJpaEntity();
        slot.setId(UUID.randomUUID());
        slot.setStatus(status);
        slot.setDate(today);
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(10, 0));
        slot.setCapacity(12);
        slot.setBookedCount(4);
        return slot;
    }
}

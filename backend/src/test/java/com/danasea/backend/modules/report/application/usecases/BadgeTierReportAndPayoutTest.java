package com.danasea.backend.modules.report.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.operation.domain.models.PayoutRequestStatus;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.PayoutRequestJpaEntity;
import com.danasea.backend.modules.operation.infrastructure.persistence.repositories.JpaPayoutRequestRepository;
import com.danasea.backend.modules.report.application.ports.ReportDataPort;
import com.danasea.backend.modules.report.application.ports.VendorRecord;
import com.danasea.backend.modules.report.domain.enums.VendorIssueType;
import com.danasea.backend.modules.report.domain.models.DashboardMetrics;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import com.danasea.backend.modules.report.domain.models.VendorPerformanceItem;
import com.danasea.backend.modules.settlement.application.dtos.PayoutRequestResponse;
import com.danasea.backend.modules.settlement.application.usecases.GetAdminPayoutRequestsUseCase;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.PayoutRequestMapper;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class BadgeTierReportAndPayoutTest {

    @Test
    @DisplayName("VendorPerformanceItem flags BADGE_AT_RISK for TOP_RATED vendor dipping below 4.65")
    void flagsTopRatedVendorAtRisk() {
        UUID vendorId = UUID.randomUUID();
        VendorPerformanceItem atRisk = VendorPerformanceItem.evaluate(
                vendorId,
                "Danang Ocean Club",
                BadgeTier.TOP_RATED,
                new BigDecimal("10000000"),
                new BigDecimal("9000000"),
                10,
                10,
                0,
                40,
                100,
                new BigDecimal("4.55"),
                12);

        assertThat(atRisk.getBadgeTier()).isEqualTo(BadgeTier.TOP_RATED);
        assertThat(atRisk.getIssues()).contains(VendorIssueType.BADGE_AT_RISK);

        VendorPerformanceItem healthy = VendorPerformanceItem.evaluate(
                vendorId,
                "Danang Ocean Club",
                BadgeTier.TOP_RATED,
                new BigDecimal("10000000"),
                new BigDecimal("9000000"),
                10,
                10,
                0,
                40,
                100,
                new BigDecimal("4.85"),
                12);

        assertThat(healthy.getIssues()).doesNotContain(VendorIssueType.BADGE_AT_RISK);
    }

    @Test
    @DisplayName("GetAdminDashboardUseCase aggregates vendorBadgeDistribution and emits BADGE_AT_RISK alert")
    void computesBadgeDistributionAndAtRiskAlert() {
        ReportDataPort port = mock(ReportDataPort.class);
        UUID topAtRiskId = UUID.randomUUID();
        UUID verifiedId = UUID.randomUUID();
        UUID noneId = UUID.randomUUID();

        when(port.findSubOrders(any(), any())).thenReturn(List.of());
        when(port.findServiceSlots(any(), any())).thenReturn(List.of());
        when(port.findApprovedVendors()).thenReturn(List.of(
                new VendorRecord(topAtRiskId, UUID.randomUUID(), "Top At Risk", null, new BigDecimal("4.55"), 8, BadgeTier.TOP_RATED),
                new VendorRecord(verifiedId, UUID.randomUUID(), "Verified Co", null, new BigDecimal("4.10"), 3, BadgeTier.VERIFIED),
                new VendorRecord(noneId, UUID.randomUUID(), "New Partner", null, BigDecimal.ZERO, 0, BadgeTier.NONE)
        ));

        GetAdminDashboardUseCase useCase = new GetAdminDashboardUseCase(port);
        DashboardMetrics metrics = useCase.execute(TimeRange.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 25)), null);

        assertThat(metrics.getVendorBadgeDistribution())
                .containsEntry("TOP_RATED", 1L)
                .containsEntry("VERIFIED", 1L)
                .containsEntry("NONE", 1L);
        assertThat(metrics.getAlerts())
                .anySatisfy(alert -> {
                    assertThat(alert.getVendorId()).isEqualTo(topAtRiskId);
                    assertThat(alert.getIssueType()).isEqualTo(VendorIssueType.BADGE_AT_RISK);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("GetAdminPayoutRequestsUseCase enriches payout queue items with vendorBadgeTier")
    void enrichesAdminPayoutQueueWithBadgeTier() {
        JpaPayoutRequestRepository repo = mock(JpaPayoutRequestRepository.class);
        VendorInternalApi vendorInternalApi = mock(VendorInternalApi.class);
        PayoutRequestMapper mapper = new PayoutRequestMapper();

        UUID vendorId = UUID.randomUUID();
        PayoutRequestJpaEntity entity = new PayoutRequestJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setVendorId(vendorId);
        entity.setSettlementId(UUID.randomUUID());
        entity.setAmount(new BigDecimal("5000000.00"));
        entity.setStatus(PayoutRequestStatus.REQUESTED);

        Page<PayoutRequestJpaEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(vendorInternalApi.findBadgeTiersByVendorIds(Set.of(vendorId)))
                .thenReturn(Map.of(vendorId, BadgeTier.TOP_RATED));

        GetAdminPayoutRequestsUseCase useCase = new GetAdminPayoutRequestsUseCase(repo, mapper, vendorInternalApi);
        Page<PayoutRequestResponse> response = useCase.execute(null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().vendorBadgeTier()).isEqualTo(BadgeTier.TOP_RATED);
    }
}

package com.danasea.backend.modules.service.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.application.dtos.ServiceSummaryResult;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchServicesUseCaseBadgeTest {

    @Test
    @DisplayName("SearchServicesUseCase batch-loads vendorBadgeTier and defaults missing vendors to NONE")
    void populatesVendorBadgeTiersInSingleBatch() {
        ServiceRepositoryPort serviceRepo = mock(ServiceRepositoryPort.class);
        ServiceImageRepositoryPort imageRepo = mock(ServiceImageRepositoryPort.class);
        VendorPort vendorPort = mock(VendorPort.class);

        UUID topVendorId = UUID.randomUUID();
        UUID unrankedVendorId = UUID.randomUUID();
        UUID s1Id = UUID.randomUUID();
        UUID s2Id = UUID.randomUUID();

        Service s1 = Service.builder()
                .id(s1Id)
                .vendorId(topVendorId)
                .name("Lặn san hô Sơn Trà")
                .price(new BigDecimal("650000"))
                .avgRating(new BigDecimal("4.90"))
                .ratingCount(25)
                .build();
        Service s2 = Service.builder()
                .id(s2Id)
                .vendorId(unrankedVendorId)
                .name("Chèo SUP Mỹ Khê")
                .price(new BigDecimal("350000"))
                .avgRating(BigDecimal.ZERO)
                .ratingCount(0)
                .build();

        when(serviceRepo.searchPublishedServices(any())).thenReturn(List.of(s1, s2));
        when(imageRepo.findByServiceId(any())).thenReturn(List.of());
        when(vendorPort.findBadgeTiersByVendorIds(Set.of(topVendorId, unrankedVendorId)))
                .thenReturn(Map.of(topVendorId, BadgeTier.TOP_RATED));

        SearchServicesUseCase useCase = new SearchServicesUseCase(serviceRepo, imageRepo, null, vendorPort);
        List<ServiceSummaryResult> results = useCase.execute(SearchServicesCriteria.builder().build());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getVendorId()).isEqualTo(topVendorId);
        assertThat(results.get(0).getVendorBadgeTier()).isEqualTo(BadgeTier.TOP_RATED);
        assertThat(results.get(1).getVendorId()).isEqualTo(unrankedVendorId);
        assertThat(results.get(1).getVendorBadgeTier()).isEqualTo(BadgeTier.NONE);
        verify(vendorPort).findBadgeTiersByVendorIds(Set.of(topVendorId, unrankedVendorId));
    }
}

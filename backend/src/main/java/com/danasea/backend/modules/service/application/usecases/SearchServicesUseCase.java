package com.danasea.backend.modules.service.application.usecases;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.application.dtos.ServiceSummaryResult;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.vendor.domain.models.BadgeTier;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;

@Component
public class SearchServicesUseCase {
    private final ServiceRepositoryPort serviceRepositoryPort;
    private final ServiceImageRepositoryPort serviceImageRepositoryPort;
    private final LocalizedContentSelector localizedContentSelector;
    private final VendorPort vendorPort;

    @Autowired
    public SearchServicesUseCase(
            ServiceRepositoryPort serviceRepositoryPort,
            ServiceImageRepositoryPort serviceImageRepositoryPort,
            LocalizedContentSelector localizedContentSelector,
            @Autowired(required = false) VendorPort vendorPort) {
        this.serviceRepositoryPort = serviceRepositoryPort;
        this.serviceImageRepositoryPort = serviceImageRepositoryPort;
        this.localizedContentSelector = localizedContentSelector;
        this.vendorPort = vendorPort;
    }

    public SearchServicesUseCase(
            ServiceRepositoryPort serviceRepositoryPort,
            ServiceImageRepositoryPort serviceImageRepositoryPort,
            LocalizedContentSelector localizedContentSelector) {
        this(serviceRepositoryPort, serviceImageRepositoryPort, localizedContentSelector, null);
    }

    public SearchServicesUseCase(
            ServiceRepositoryPort serviceRepositoryPort,
            ServiceImageRepositoryPort serviceImageRepositoryPort) {
        this(serviceRepositoryPort, serviceImageRepositoryPort, null, null);
    }

    public SearchServicesUseCase(
            ServiceRepositoryPort serviceRepositoryPort,
            LocalizedContentSelector localizedContentSelector) {
        this(serviceRepositoryPort, null, localizedContentSelector, null);
    }

    public List<ServiceSummaryResult> execute(SearchServicesCriteria criteria) {
        List<Service> services = serviceRepositoryPort.searchPublishedServices(criteria);
        Map<UUID, BadgeTier> badgeTiers = resolveVendorBadgeTiers(services);

        return services.stream()
                .map(service -> mapToResult(service, badgeTiers))
                .collect(Collectors.toList());
    }

    public long count(SearchServicesCriteria criteria) {
        return serviceRepositoryPort.countPublishedServices(criteria);
    }

    private Map<UUID, BadgeTier> resolveVendorBadgeTiers(List<Service> services) {
        if (vendorPort == null || services == null || services.isEmpty()) {
            return Map.of();
        }
        Set<UUID> vendorIds = services.stream()
                .map(Service::getVendorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (vendorIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BadgeTier> result = vendorPort.findBadgeTiersByVendorIds(vendorIds);
        return result != null ? result : Map.of();
    }

    private ServiceSummaryResult mapToResult(Service service, Map<UUID, BadgeTier> badgeTiers) {
        String primaryImageUrl = serviceImageRepositoryPort == null ? null
                : serviceImageRepositoryPort.findByServiceId(service.getId()).stream()
                .findFirst()
                .map(image -> image.getUrl())
                .orElse(null);
        BadgeTier vendorBadgeTier = service.getVendorId() == null
                ? BadgeTier.NONE
                : badgeTiers.getOrDefault(service.getVendorId(), BadgeTier.NONE);
        return ServiceSummaryResult.builder()
                .id(service.getId())
                .vendorId(service.getVendorId())
                .vendorBadgeTier(vendorBadgeTier)
                .name(localize(service.getName(), service.getNameEn()))
                .slug(service.getSlug())
                .shortDescription(localize(service.getDescription(), service.getDescriptionEn()))
                .price(service.getPrice())
                .address(service.getAddress())
                .averageRating(service.getAvgRating())
                .reviewCount(service.getRatingCount() != null ? service.getRatingCount() : 0)
                .viewCount(service.getViewCount() != null ? service.getViewCount() : 0)
                .primaryImageUrl(primaryImageUrl)
                .build();
    }

    private String localize(String vietnamese, String english) {
        return localizedContentSelector == null ? vietnamese : localizedContentSelector.select(vietnamese, english);
    }
}

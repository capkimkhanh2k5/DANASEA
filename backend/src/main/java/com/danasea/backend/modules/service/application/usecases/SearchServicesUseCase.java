package com.danasea.backend.modules.service.application.usecases;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.application.dtos.ServiceSummaryResult;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;

@Component
public class SearchServicesUseCase {
    private final ServiceRepositoryPort serviceRepositoryPort;
    private final ServiceImageRepositoryPort serviceImageRepositoryPort;
    private final LocalizedContentSelector localizedContentSelector;

    @Autowired
    public SearchServicesUseCase(
            ServiceRepositoryPort serviceRepositoryPort,
            ServiceImageRepositoryPort serviceImageRepositoryPort,
            LocalizedContentSelector localizedContentSelector) {
        this.serviceRepositoryPort = serviceRepositoryPort;
        this.serviceImageRepositoryPort = serviceImageRepositoryPort;
        this.localizedContentSelector = localizedContentSelector;
    }

    public SearchServicesUseCase(
            ServiceRepositoryPort serviceRepositoryPort,
            ServiceImageRepositoryPort serviceImageRepositoryPort) {
        this(serviceRepositoryPort, serviceImageRepositoryPort, null);
    }

    public SearchServicesUseCase(
            ServiceRepositoryPort serviceRepositoryPort,
            LocalizedContentSelector localizedContentSelector) {
        this(serviceRepositoryPort, null, localizedContentSelector);
    }

    public List<ServiceSummaryResult> execute(SearchServicesCriteria criteria) {
        List<Service> services = serviceRepositoryPort.searchPublishedServices(criteria);

        return services.stream().map(this::mapToResult).collect(Collectors.toList());
    }

    public long count(SearchServicesCriteria criteria) {
        return serviceRepositoryPort.countPublishedServices(criteria);
    }

    private ServiceSummaryResult mapToResult(Service service) {
        String primaryImageUrl = serviceImageRepositoryPort == null ? null
                : serviceImageRepositoryPort.findByServiceId(service.getId()).stream()
                .findFirst()
                .map(image -> image.getUrl())
                .orElse(null);
        return ServiceSummaryResult.builder()
                .id(service.getId())
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

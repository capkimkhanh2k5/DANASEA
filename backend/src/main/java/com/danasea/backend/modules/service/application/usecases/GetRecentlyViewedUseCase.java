package com.danasea.backend.modules.service.application.usecases;

import com.danasea.backend.modules.service.application.dtos.RecentlyViewedResult;
import com.danasea.backend.modules.service.domain.models.RecentlyViewed;
import com.danasea.backend.modules.service.domain.ports.RecentlyViewedRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class GetRecentlyViewedUseCase {
    private final RecentlyViewedRepositoryPort recentlyViewedRepositoryPort;
    private final ServiceRepositoryPort serviceRepositoryPort;
    private final ServiceImageRepositoryPort serviceImageRepositoryPort;
    private final LocalizedContentSelector localizedContentSelector;

    @Autowired
    public GetRecentlyViewedUseCase(
            RecentlyViewedRepositoryPort recentlyViewedRepositoryPort,
            ServiceRepositoryPort serviceRepositoryPort,
            ServiceImageRepositoryPort serviceImageRepositoryPort,
            LocalizedContentSelector localizedContentSelector) {
        this.recentlyViewedRepositoryPort = recentlyViewedRepositoryPort;
        this.serviceRepositoryPort = serviceRepositoryPort;
        this.serviceImageRepositoryPort = serviceImageRepositoryPort;
        this.localizedContentSelector = localizedContentSelector;
    }

    public GetRecentlyViewedUseCase(
            RecentlyViewedRepositoryPort recentlyViewedRepositoryPort,
            ServiceRepositoryPort serviceRepositoryPort,
            ServiceImageRepositoryPort serviceImageRepositoryPort) {
        this(recentlyViewedRepositoryPort, serviceRepositoryPort, serviceImageRepositoryPort, null);
    }

    public GetRecentlyViewedUseCase(
            RecentlyViewedRepositoryPort recentlyViewedRepositoryPort,
            ServiceRepositoryPort serviceRepositoryPort,
            LocalizedContentSelector localizedContentSelector) {
        this(recentlyViewedRepositoryPort, serviceRepositoryPort, null, localizedContentSelector);
    }

    public List<RecentlyViewedResult> execute(UUID userId, String sessionId) {
        List<RecentlyViewed> entities;
        if (userId != null) {
            entities = recentlyViewedRepositoryPort.findAllByUserId(userId);
        } else if (sessionId != null && !sessionId.trim().isEmpty()) {
            entities = recentlyViewedRepositoryPort.findAllBySessionId(sessionId);
        } else {
            return List.of();
        }

        return entities.stream().flatMap(rv ->
                serviceRepositoryPort.findPublishedById(rv.getServiceId()).stream().map(service ->
            RecentlyViewedResult.builder()
                .id(rv.getId())
                .serviceId(rv.getServiceId())
                .serviceName(localizedContentSelector == null ? service.getName()
                        : localizedContentSelector.select(service.getName(), service.getNameEn()))
                .primaryImageUrl(primaryImageUrl(service.getId()))
                .viewedAt(rv.getViewedAt() != null ? rv.getViewedAt().toLocalDateTime() : null)
                .build()
        )).collect(Collectors.toList());
    }

    private String primaryImageUrl(UUID serviceId) {
        if (serviceImageRepositoryPort == null) {
            return null;
        }
        return serviceImageRepositoryPort.findByServiceId(serviceId).stream()
                .findFirst()
                .map(image -> image.getUrl())
                .orElse(null);
    }
}

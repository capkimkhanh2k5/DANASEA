package com.danasea.backend.modules.service.application.usecases;

import com.danasea.backend.modules.service.application.dtos.WishlistItemResult;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.models.Wishlist;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.WishlistRepositoryPort;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WishlistUseCase {
    private final WishlistRepositoryPort wishlistRepositoryPort;
    private final ServiceRepositoryPort serviceRepositoryPort;
    private final ServiceImageRepositoryPort serviceImageRepositoryPort;
    private final LocalizedContentSelector localizedContentSelector;

    public void addWishlist(UUID userId, UUID serviceId) {
        serviceRepositoryPort.findPublishedById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(
                        "Service not found or not published: " + serviceId));

        if (wishlistRepositoryPort.existsByUserIdAndServiceId(userId, serviceId)) {
            return; // Idempotent
        }

        Wishlist wishlist = new Wishlist();
        
        wishlist.setUserId(userId);
        wishlist.setServiceId(serviceId);
        wishlist.setCreatedAt(OffsetDateTime.now());
        
        wishlistRepositoryPort.save(wishlist);
    }

    public void removeWishlist(UUID userId, UUID serviceId) {
        wishlistRepositoryPort.deleteByUserIdAndServiceId(userId, serviceId); // Idempotent by design
    }

    public List<WishlistItemResult> getWishlists(UUID userId) {
        return wishlistRepositoryPort.findAllByUserId(userId).stream().flatMap(w ->
                serviceRepositoryPort.findPublishedById(w.getServiceId()).stream().map(service -> {
            LocalDateTime added = w.getCreatedAt() != null ? w.getCreatedAt().toLocalDateTime() : null;
            return WishlistItemResult.builder()
                .id(w.getId())
                .serviceId(w.getServiceId())
                .serviceName(localizedContentSelector == null ? service.getName()
                        : localizedContentSelector.select(service.getName(), service.getNameEn()))
                .primaryImageUrl(primaryImageUrl(service.getId()))
                .addedAt(added)
                .build();
        })).collect(Collectors.toList());
    }

    private String primaryImageUrl(UUID serviceId) {
        return serviceImageRepositoryPort.findByServiceId(serviceId).stream()
                .findFirst()
                .map(image -> image.getUrl())
                .orElse(null);
    }
}

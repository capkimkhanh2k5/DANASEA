package com.danasea.backend.modules.service.domain.ports;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;

public interface ServiceRepositoryPort {
    Service save(Service service);

    Optional<Service> findById(UUID id);

    Optional<Service> findByIdForUpdate(UUID id);

    List<Service> findByVendorId(UUID vendorId);

    List<Service> findByStatus(ServiceStatus status);

    List<Service> findAll();

    void deleteById(UUID id);

    boolean existsById(UUID id);

    Optional<Service> findPublishedById(UUID id);

    Optional<Service> findPublishedBySlug(String slug);

    Optional<Service> findBySlug(String slug);

    boolean existsBySlug(String slug);

    int incrementViewCount(UUID id, ServiceStatus status);

    List<Service> searchPublishedServices(
            UUID categoryId,
            String keyword,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal lat,
            BigDecimal lng,
            Double radiusKm,
            int page,
            int size
    );

    long countPublishedServices(
            UUID categoryId,
            String keyword,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal lat,
            BigDecimal lng,
            Double radiusKm
    );

    default List<Service> searchPublishedServices(SearchServicesCriteria criteria) {
        if (criteria == null) {
            return List.of();
        }
        return searchPublishedServices(
                criteria.getCategoryId(),
                criteria.getKeyword(),
                criteria.getMinPrice(),
                criteria.getMaxPrice(),
                criteria.getLat(),
                criteria.getLng(),
                criteria.getRadiusKm(),
                criteria.getPage(),
                criteria.getSize()
        );
    }

    default long countPublishedServices(SearchServicesCriteria criteria) {
        if (criteria == null) {
            return 0L;
        }
        return countPublishedServices(
                criteria.getCategoryId(),
                criteria.getKeyword(),
                criteria.getMinPrice(),
                criteria.getMaxPrice(),
                criteria.getLat(),
                criteria.getLng(),
                criteria.getRadiusKm()
        );
    }
}

package com.danasea.backend.modules.service.infrastructure.persistence.mappers;

import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ServiceMapper {

    public Service toDomain(ServiceJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Service domain = new Service();
        domain.setId(entity.getId());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        domain.setVendorId(entity.getVendorId());
        domain.setCategoryId(entity.getCategoryId());
        domain.setName(entity.getName());
        domain.setNameEn(entity.getNameEn());
        domain.setSlug(entity.getSlug());
        domain.setDescription(entity.getDescription());
        domain.setDescriptionEn(entity.getDescriptionEn());
        domain.setPrice(entity.getPrice());
        domain.setDurationMinutes(entity.getDurationMinutes());
        domain.setCapacityPerSlot(entity.getCapacityPerSlot());
        domain.setLocationName(entity.getLocationName());
        domain.setAddress(entity.getAddress());
        domain.setLatitude(entity.getLatitude());
        domain.setLongitude(entity.getLongitude());
        domain.setStatus(entity.getStatus());
        domain.setRejectionReason(entity.getRejectionReason());
        domain.setWaiverContent(entity.getWaiverContent());
        domain.setWaiverContentEn(entity.getWaiverContentEn());
        domain.setWaiverRequired(entity.getWaiverRequired() != null ? entity.getWaiverRequired() : Boolean.FALSE);
        domain.setWaiverVersion(entity.getWaiverVersion() != null ? entity.getWaiverVersion() : 1);
        domain.setWeatherSensitive(entity.getWeatherSensitive());
        domain.setMinWindKmh(entity.getMinWindKmh());
        domain.setMaxWaveM(entity.getMaxWaveM());
        domain.setAvgRating(entity.getAvgRating());
        domain.setRatingCount(entity.getRatingCount());
        domain.setViewCount(entity.getViewCount());
        domain.setOperationType(entity.getOperationType() != null ? entity.getOperationType() : com.danasea.backend.modules.service.domain.models.OperationType.FIXED_DEPARTURE);
        return domain;
    }

    public ServiceJpaEntity toEntity(Service domain) {
        if (domain == null) {
            return null;
        }
        ServiceJpaEntity entity = new ServiceJpaEntity();
        entity.setId(domain.getId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setVendorId(domain.getVendorId());
        entity.setCategoryId(domain.getCategoryId());
        entity.setName(domain.getName());
        entity.setNameEn(domain.getNameEn());
        entity.setSlug(domain.getSlug());
        entity.setDescription(domain.getDescription());
        entity.setDescriptionEn(domain.getDescriptionEn());
        entity.setPrice(domain.getPrice());
        entity.setDurationMinutes(domain.getDurationMinutes());
        entity.setCapacityPerSlot(domain.getCapacityPerSlot());
        entity.setLocationName(domain.getLocationName());
        entity.setAddress(domain.getAddress());
        entity.setLatitude(domain.getLatitude());
        entity.setLongitude(domain.getLongitude());
        entity.setStatus(domain.getStatus() != null ? domain.getStatus() : ServiceStatus.DRAFT);
        entity.setRejectionReason(domain.getRejectionReason());
        entity.setWaiverContent(domain.getWaiverContent());
        entity.setWaiverContentEn(domain.getWaiverContentEn());
        entity.setWaiverRequired(domain.getWaiverRequired() != null ? domain.getWaiverRequired() : Boolean.FALSE);
        entity.setWaiverVersion(domain.getWaiverVersion() != null ? domain.getWaiverVersion() : 1);
        entity.setWeatherSensitive(domain.getWeatherSensitive() != null ? domain.getWeatherSensitive() : Boolean.FALSE);
        entity.setMinWindKmh(domain.getMinWindKmh());
        entity.setMaxWaveM(domain.getMaxWaveM());
        entity.setAvgRating(domain.getAvgRating() != null ? domain.getAvgRating() : BigDecimal.ZERO);
        entity.setRatingCount(domain.getRatingCount() != null ? domain.getRatingCount() : 0);
        entity.setViewCount(domain.getViewCount() != null ? domain.getViewCount() : 0);
        entity.setOperationType(domain.getOperationType() != null ? domain.getOperationType() : com.danasea.backend.modules.service.domain.models.OperationType.FIXED_DEPARTURE);
        return entity;
    }

    public List<Service> toDomainList(List<ServiceJpaEntity> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(this::toDomain)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ServiceJpaEntity> toEntityList(List<Service> domains) {
        if (domains == null) {
            return Collections.emptyList();
        }
        return domains.stream()
                .map(this::toEntity)
                .filter(Objects::nonNull)
                .toList();
    }
}

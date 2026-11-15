package com.danasea.backend.modules.operation.infrastructure.persistence.mappers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.danasea.backend.modules.account.infrastructure.persistence.entities.UserJpaEntity;
import com.danasea.backend.modules.account.infrastructure.persistence.repositories.JpaUserRepository;
import com.danasea.backend.modules.operation.domain.models.Review;
import com.danasea.backend.modules.operation.infrastructure.persistence.entities.ReviewJpaEntity;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse;
import com.danasea.backend.modules.operation.presentation.dtos.ReviewResponse.ReviewResponseBuilder;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReviewMapper {

    private final ObjectMapper objectMapper;
    private final JpaUserRepository userRepository;
    private final JpaServiceRepository serviceRepository;

    @Autowired
    public ReviewMapper(ObjectMapper objectMapper, JpaUserRepository userRepository, JpaServiceRepository serviceRepository) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
    }

    public ReviewMapper(ObjectMapper objectMapper, JpaUserRepository userRepository) {
        this(objectMapper, userRepository, null);
    }

    public Review toDomain(ReviewJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Review.builder()
                .subOrderId(entity.getSubOrderId())
                .customerId(entity.getCustomerId())
                .vendorId(entity.getVendorId())
                .serviceId(entity.getServiceId())
                .rating(entity.getRating())
                .comment(entity.getComment())
                .images(entity.getImages())
                .vendorReply(entity.getVendorReply())
                .vendorRepliedAt(entity.getVendorRepliedAt())
                .isFlagged(entity.getIsFlagged())
                .isVisible(entity.getIsVisible())
                .flagReason(entity.getFlagReason())
                .build();
    }

    public ReviewJpaEntity toEntity(Review domain) {
        if (domain == null) {
            return null;
        }
        return ReviewJpaEntity.builder()
                .subOrderId(domain.getSubOrderId())
                .customerId(domain.getCustomerId())
                .vendorId(domain.getVendorId())
                .serviceId(domain.getServiceId())
                .rating(domain.getRating())
                .comment(domain.getComment())
                .images(domain.getImages())
                .vendorReply(domain.getVendorReply())
                .vendorRepliedAt(domain.getVendorRepliedAt())
                .isFlagged(domain.getIsFlagged())
                .isVisible(domain.getIsVisible() != null ? domain.getIsVisible() : true)
                .flagReason(domain.getFlagReason())
                .build();
    }

    public ReviewResponse toResponse(ReviewJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        String serviceName = null;
        if (entity.getServiceId() != null && serviceRepository != null) {
            serviceName = serviceRepository.findById(entity.getServiceId())
                    .map(ServiceJpaEntity::getName)
                    .orElse(null);
        }
        return toResponse(entity, serviceName);
    }

    public ReviewResponse toResponse(ReviewJpaEntity entity, String serviceName) {
        return responseBuilder(entity, serviceName)
                .customerId(entity.getCustomerId())
                .subOrderId(entity.getSubOrderId())
                .isFlagged(entity.getIsFlagged())
                .flagReason(entity.getFlagReason())
                .moderationNote(entity.getModerationNote())
                .build();
    }

    public Page<ReviewResponse> toResponsePage(Page<ReviewJpaEntity> page) {
        Map<UUID, String> serviceNames = serviceNamesForPage(page);
        return page.map(review -> toResponse(review, serviceNames.get(review.getServiceId())));
    }

    public Page<ReviewResponse> toPublicResponsePage(Page<ReviewJpaEntity> page) {
        Map<UUID, String> serviceNames = serviceNamesForPage(page);
        return page.map(review -> responseBuilder(review, serviceNames.get(review.getServiceId())).build());
    }

    private Map<UUID, String> serviceNamesForPage(Page<ReviewJpaEntity> page) {
        Set<UUID> serviceIds = page.stream()
                .map(ReviewJpaEntity::getServiceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (serviceIds.isEmpty() || serviceRepository == null) {
            return Collections.emptyMap();
        }
        return serviceRepository.findAllById(serviceIds).stream()
                .filter(service -> service.getName() != null)
                .collect(Collectors.toMap(ServiceJpaEntity::getId, ServiceJpaEntity::getName));
    }

    private ReviewResponseBuilder responseBuilder(ReviewJpaEntity entity, String serviceName) {
        String customerName = "Khách hàng";
        String customerAvatar = null;
        if (entity.getCustomerId() != null) {
            Optional<UserJpaEntity> userOpt = userRepository.findById(entity.getCustomerId());
            if (userOpt.isPresent()) {
                UserJpaEntity user = userOpt.get();
                if (user.getFullName() != null && !user.getFullName().isBlank()) {
                    customerName = user.getFullName();
                }
                customerAvatar = user.getAvatarUrl();
            }
        }

        return ReviewResponse.builder()
                .id(entity.getId())
                .customerName(customerName)
                .customerAvatar(customerAvatar)
                .vendorId(entity.getVendorId())
                .serviceId(entity.getServiceId())
                .serviceName(serviceName)
                .rating(entity.getRating())
                .comment(entity.getComment())
                .images(parseImages(entity.getImages()))
                .vendorReply(entity.getVendorReply())
                .vendorRepliedAt(entity.getVendorRepliedAt())
                .isVisible(entity.getIsVisible())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());
    }

    public String serializeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            log.error("Failed to serialize review images to JSON", e);
            return String.join(",", images);
        }
    }

    public List<String> parseImages(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("[]")) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }
}

package com.danasea.backend.modules.service.infrastructure.mappers;

import java.util.ArrayList;
import java.util.List;

import com.danasea.backend.modules.service.domain.models.Category;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.presentation.dtos.CategoryResponse;
import com.danasea.backend.modules.service.presentation.dtos.CategoryTreeResponse;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public Category toDomain(CategoryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Category domain = new Category();
        domain.setId(entity.getId());
        domain.setName(entity.getName());
        domain.setNameEn(entity.getNameEn());
        domain.setSlug(entity.getSlug());
        domain.setParentId(entity.getParentId());
        domain.setIconUrl(entity.getIconUrl());
        domain.setIsActive(entity.getIsActive());
        domain.setRequiresSafetyCert(entity.getRequiresSafetyCert());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        return domain;
    }

    public List<Category> toDomainList(List<CategoryJpaEntity> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }
        return entities.stream()
                .map(this::toDomain)
                .toList();
    }

    public CategoryJpaEntity toEntity(Category domain) {
        if (domain == null) {
            return null;
        }
        CategoryJpaEntity entity = new CategoryJpaEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setNameEn(domain.getNameEn());
        entity.setSlug(domain.getSlug());
        entity.setParentId(domain.getParentId());
        entity.setIconUrl(domain.getIconUrl());
        entity.setIsActive(domain.getIsActive());
        entity.setRequiresSafetyCert(Boolean.TRUE.equals(domain.getRequiresSafetyCert()));
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    public CategoryResponse toResponse(Category domain) {
        if (domain == null) {
            return null;
        }
        return new CategoryResponse(
                domain.getId(),
                domain.getName(),
                domain.getNameEn(),
                domain.getSlug(),
                domain.getParentId(),
                domain.getIconUrl(),
                domain.getIsActive(),
                domain.getRequiresSafetyCert() != null ? domain.getRequiresSafetyCert() : false,
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }

    public CategoryTreeResponse toTreeResponse(Category domain, List<CategoryTreeResponse> children) {
        if (domain == null) {
            return null;
        }
        return new CategoryTreeResponse(
                domain.getId(),
                domain.getName(),
                domain.getNameEn(),
                domain.getSlug(),
                domain.getParentId(),
                domain.getIconUrl(),
                domain.getIsActive(),
                domain.getRequiresSafetyCert() != null ? domain.getRequiresSafetyCert() : false,
                children != null ? children : new ArrayList<>()
        );
    }
}

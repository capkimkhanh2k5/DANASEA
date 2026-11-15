package com.danasea.backend.modules.service.infrastructure.persistence.adapters;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.service.application.ports.output.ActiveServiceCheckPort;
import com.danasea.backend.modules.service.application.ports.output.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.models.Category;
import com.danasea.backend.modules.service.domain.models.DocStatus;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.mappers.CategoryMapper;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.CategoryJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaCategoryRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CategoryPersistenceAdapter implements CategoryRepositoryPort, ActiveServiceCheckPort {

    private final JpaCategoryRepository jpaCategoryRepository;
    private final JpaServiceRepository jpaServiceRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public Category save(Category category) {
        CategoryJpaEntity entity = categoryMapper.toEntity(category);
        CategoryJpaEntity saved = jpaCategoryRepository.saveAndFlush(entity);
        return categoryMapper.toDomain(saved);
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return jpaCategoryRepository.findById(id).map(categoryMapper::toDomain);
    }

    @Override
    public Optional<Category> findByIdForUpdate(UUID id) {
        return jpaCategoryRepository.findByIdForUpdate(id).map(categoryMapper::toDomain);
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return jpaCategoryRepository.findBySlug(slug).map(categoryMapper::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpaCategoryRepository.existsBySlug(slug);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaCategoryRepository.existsById(id);
    }

    @Override
    public List<Category> findAll() {
        return jpaCategoryRepository.findAll()
                .stream()
                .map(categoryMapper::toDomain)
                .toList();
    }

    @Override
    public List<Category> findAllActive() {
        return jpaCategoryRepository.findAllByIsActiveTrue()
                .stream()
                .map(categoryMapper::toDomain)
                .toList();
    }

    @Override
    public boolean hasActiveServices(UUID categoryId) {
        return jpaServiceRepository.existsByCategoryIdAndStatus(categoryId, ServiceStatus.PUBLISHED);
    }

    @Override
    public boolean hasPublishedServicesWithoutApprovedSafetyDocuments(UUID categoryId) {
        return jpaServiceRepository.existsByCategoryAndStatusWithoutSafetyDocument(
                categoryId, ServiceStatus.PUBLISHED, DocStatus.APPROVED);
    }

    @Override
    public void deleteById(UUID id) {
        jpaCategoryRepository.deleteById(id);
    }

    @Override
    public boolean hasLinkedServices(UUID categoryId) {
        return jpaServiceRepository.existsByCategoryId(categoryId);
    }

    @Override
    public boolean hasChildren(UUID parentId) {
        return jpaCategoryRepository.existsByParentId(parentId);
    }

    @Override
    public boolean hasCommissionRules(UUID categoryId) {
        return jpaCategoryRepository.existsCommissionRulesByCategoryId(categoryId);
    }

    @Override
    public boolean hasSafetyRules(UUID categoryId) {
        return jpaCategoryRepository.existsCategorySafetyRulesByCategoryId(categoryId);
    }
}

package com.danasea.backend.modules.service.application.ports.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.danasea.backend.modules.service.domain.models.Category;

public interface CategoryRepositoryPort {
    Category save(Category category);
    Optional<Category> findById(UUID id);
    Optional<Category> findByIdForUpdate(UUID id);
    Optional<Category> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsById(UUID id);
    List<Category> findAll();
    List<Category> findAllActive();
    void deleteById(UUID id);
    boolean hasLinkedServices(UUID categoryId);
    boolean hasChildren(UUID parentId);
    boolean hasCommissionRules(UUID categoryId);
    boolean hasSafetyRules(UUID categoryId);
}

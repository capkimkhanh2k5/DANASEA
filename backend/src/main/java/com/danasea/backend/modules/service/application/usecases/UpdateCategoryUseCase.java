package com.danasea.backend.modules.service.application.usecases;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.danasea.backend.modules.service.application.ports.output.CategoryRepositoryPort;
import com.danasea.backend.modules.service.application.ports.output.ActiveServiceCheckPort;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasActiveServicesException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHierarchyLoopException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.SlugAlreadyExistsException;
import com.danasea.backend.modules.service.domain.models.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateCategoryUseCase {

    private final CategoryRepositoryPort categoryRepositoryPort;
    private final ActiveServiceCheckPort activeServiceCheckPort;

    @Transactional
    public Category execute(UUID id, UpdateCategoryCommand command) {
        Category category = categoryRepositoryPort.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + id));

        if (Boolean.FALSE.equals(command.isActive()) && activeServiceCheckPort.hasActiveServices(id)) {
            throw new CategoryHasActiveServicesException(id);
        }

        if (command.slug() != null && !command.slug().isBlank() && !command.slug().equals(category.getSlug())) {
            if (categoryRepositoryPort.existsBySlug(command.slug())) {
                throw SlugAlreadyExistsException.ofSlug(command.slug());
            }
            category.setSlug(command.slug());
        }

        if (command.name() != null && !command.name().isBlank()) {
            category.setName(command.name());
        }

        if (command.nameEn() != null) {
            category.setNameEn(command.nameEn());
        }

        if (command.iconUrl() != null) {
            category.setIconUrl(command.iconUrl());
        }

        if (command.parentId() != null) {
            if (command.parentId().equals(id)) {
                throw new CategoryHierarchyLoopException("Cannot set category as its own parent");
            }

            categoryRepositoryPort.findById(command.parentId())
                    .orElseThrow(() -> new CategoryNotFoundException("Parent category not found with id: " + command.parentId()));

            validateNoHierarchyLoop(id, command.parentId());
            category.setParentId(command.parentId());
        }

        if (command.isActive() != null) {
            category.setIsActive(command.isActive());
        }

        if (command.requiresSafetyCert() != null) {
            category.setRequiresSafetyCert(command.requiresSafetyCert());
        }
        return categoryRepositoryPort.save(category);
    }

    @Transactional
    public Category execute(UUID id, String name, String nameEn, String slug, UUID parentId, String iconUrl) {
        return execute(id, new UpdateCategoryCommand(name, nameEn, slug, parentId, iconUrl));
    }

    @Transactional
    public Category execute(UUID id, String name, String nameEn, String slug, UUID parentId, String iconUrl, Boolean isActive, Boolean requiresSafetyCert) {
        return execute(id, new UpdateCategoryCommand(name, nameEn, slug, parentId, iconUrl, isActive, requiresSafetyCert));
    }

    private void validateNoHierarchyLoop(UUID categoryId, UUID targetParentId) {
        UUID currentParentId = targetParentId;
        Set<UUID> visited = new HashSet<>();
        visited.add(categoryId);

        while (currentParentId != null) {
            if (visited.contains(currentParentId)) {
                throw new CategoryHierarchyLoopException("Circular hierarchy detected between category and parent");
            }
            visited.add(currentParentId);

            Category currentParent = categoryRepositoryPort.findById(currentParentId).orElse(null);
            if (currentParent == null) {
                break;
            }
            currentParentId = currentParent.getParentId();
        }
    }
}

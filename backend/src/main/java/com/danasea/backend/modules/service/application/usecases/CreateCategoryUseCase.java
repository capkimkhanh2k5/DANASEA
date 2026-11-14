package com.danasea.backend.modules.service.application.usecases;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.danasea.backend.modules.service.application.ports.output.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHierarchyLoopException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.SlugAlreadyExistsException;
import com.danasea.backend.modules.service.domain.models.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateCategoryUseCase {

    private final CategoryRepositoryPort categoryRepositoryPort;

    public Category execute(CreateCategoryCommand command) {
        if (categoryRepositoryPort.existsBySlug(command.slug())) {
            throw SlugAlreadyExistsException.ofSlug(command.slug());
        }

        if (command.parentId() != null) {
            Category parent = categoryRepositoryPort.findById(command.parentId())
                    .orElseThrow(() -> new CategoryNotFoundException("Parent category not found with id: " + command.parentId()));

            if (command.id() != null) {
                if (command.parentId().equals(command.id())) {
                    throw new CategoryHierarchyLoopException("Cannot set category as its own parent");
                }
                validateNoHierarchyLoop(command.id(), command.parentId());
            }
        }

        Category category = Category.builder()
                .id(command.id())
                .name(command.name())
                .nameEn(command.nameEn())
                .slug(command.slug())
                .parentId(command.parentId())
                .iconUrl(command.iconUrl())
                .isActive(true)
                .requiresSafetyCert(Boolean.TRUE.equals(command.requiresSafetyCert()))
                .build();

        return categoryRepositoryPort.save(category);
    }

    public Category execute(String name, String nameEn, String slug, UUID parentId, String iconUrl) {
        return execute(new CreateCategoryCommand(null, name, nameEn, slug, parentId, iconUrl, false));
    }

    public Category execute(String name, String nameEn, String slug, UUID parentId, String iconUrl, Boolean requiresSafetyCert) {
        return execute(new CreateCategoryCommand(null, name, nameEn, slug, parentId, iconUrl, requiresSafetyCert));
    }

    public Category execute(UUID id, String name, String nameEn, String slug, UUID parentId, String iconUrl) {
        return execute(new CreateCategoryCommand(id, name, nameEn, slug, parentId, iconUrl, false));
    }

    public Category execute(UUID id, String name, String nameEn, String slug, UUID parentId, String iconUrl, Boolean requiresSafetyCert) {
        return execute(new CreateCategoryCommand(id, name, nameEn, slug, parentId, iconUrl, requiresSafetyCert));
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

package com.danasea.backend.modules.service.application.usecases;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.danasea.backend.modules.service.application.ports.output.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.models.Category;
import com.danasea.backend.modules.service.presentation.dtos.CategoryTreeResponse;
import com.danasea.backend.shared.i18n.LocalizedContentSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GetCategoryTreeUseCase {

    private final CategoryRepositoryPort categoryRepositoryPort;
    private final LocalizedContentSelector localizedContentSelector;

    public GetCategoryTreeUseCase(CategoryRepositoryPort categoryRepositoryPort) {
        this(categoryRepositoryPort, null);
    }

    @Autowired
    public GetCategoryTreeUseCase(
            CategoryRepositoryPort categoryRepositoryPort,
            LocalizedContentSelector localizedContentSelector) {
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.localizedContentSelector = localizedContentSelector;
    }

    public List<CategoryTreeResponse> execute() {
        return execute(false);
    }

    public List<CategoryTreeResponse> execute(boolean includeInactive) {
        List<Category> categories;
        if (includeInactive) {
            categories = categoryRepositoryPort.findAll();
        } else {
            categories = categoryRepositoryPort.findAllActive();
            if (categories == null || categories.isEmpty()) {
                List<Category> all = categoryRepositoryPort.findAll();
                if (all != null && !all.isEmpty()) {
                    categories = all.stream()
                            .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                            .toList();
                } else {
                    categories = List.of();
                }
            } else {
                categories = categories.stream()
                        .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                        .toList();
            }
        }

        return buildTree(categories, !includeInactive);
    }

    private List<CategoryTreeResponse> buildTree(List<Category> categories, boolean localize) {
        if (categories == null || categories.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, CategoryTreeResponse> nodeMap = new LinkedHashMap<>();
        Map<UUID, UUID> parentMap = new HashMap<>();

        for (Category cat : categories) {
            if (cat.getId() != null) {
                nodeMap.put(cat.getId(), new CategoryTreeResponse(
                        cat.getId(),
                        localize && localizedContentSelector != null
                                ? localizedContentSelector.select(cat.getName(), cat.getNameEn())
                                : cat.getName(),
                        cat.getNameEn(),
                        cat.getSlug(),
                        cat.getParentId(),
                        cat.getIconUrl(),
                        cat.getIsActive(),
                        cat.getRequiresSafetyCert() != null ? cat.getRequiresSafetyCert() : false,
                        new ArrayList<>()
                ));
                parentMap.put(cat.getId(), cat.getParentId());
            }
        }

        List<CategoryTreeResponse> rootNodes = new ArrayList<>();

        for (Category cat : categories) {
            if (cat.getId() == null) {
                continue;
            }

            CategoryTreeResponse currentNode = nodeMap.get(cat.getId());
            UUID parentId = cat.getParentId();

            if (parentId != null && nodeMap.containsKey(parentId)) {
                if (!isAncestor(cat.getId(), parentId, parentMap)) {
                    CategoryTreeResponse parentNode = nodeMap.get(parentId);
                    parentNode.children().add(currentNode);
                    continue;
                }
            }
            rootNodes.add(currentNode);
        }

        return rootNodes;
    }

    private boolean isAncestor(UUID ancestorId, UUID childId, Map<UUID, UUID> parentMap) {
        UUID current = childId;
        Set<UUID> seen = new HashSet<>();

        while (current != null) {
            if (current.equals(ancestorId)) {
                return true;
            }
            if (!seen.add(current)) {
                break;
            }
            current = parentMap.get(current);
        }

        return false;
    }
}

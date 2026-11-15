package com.danasea.backend.modules.service.presentation.dtos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CategoryTreeResponse(
    UUID id,
    String name,
    String nameEn,
    String slug,
    UUID parentId,
    String iconUrl,
    Boolean isActive,
    Boolean requiresSafetyCert,
    List<CategoryTreeResponse> children
) {
    public CategoryTreeResponse {
        if (children == null) {
            children = new ArrayList<>();
        }
        if (requiresSafetyCert == null) {
            requiresSafetyCert = false;
        }
    }

    public CategoryTreeResponse(
            UUID id,
            String name,
            String nameEn,
            String slug,
            UUID parentId,
            String iconUrl,
            Boolean isActive,
            List<CategoryTreeResponse> children) {
        this(id, name, nameEn, slug, parentId, iconUrl, isActive, false, children);
    }
}

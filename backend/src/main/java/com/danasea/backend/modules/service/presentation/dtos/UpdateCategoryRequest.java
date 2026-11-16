package com.danasea.backend.modules.service.presentation.dtos;

import java.util.UUID;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
    @Pattern(regexp = ".*\\S.*") @Size(max = 100) String name,
    @Size(max = 100) String nameEn,
    @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 120) String slug,
    UUID parentId,
    @Size(max = 2048) String iconUrl,
    Boolean isActive,
    Boolean requiresSafetyCert
) {
    public UpdateCategoryRequest(String name, String nameEn, String slug, UUID parentId, String iconUrl) {
        this(name, nameEn, slug, parentId, iconUrl, null, null);
    }

    public UpdateCategoryRequest(String name, String nameEn, String slug, UUID parentId, String iconUrl, Boolean isActive) {
        this(name, nameEn, slug, parentId, iconUrl, isActive, null);
    }
}

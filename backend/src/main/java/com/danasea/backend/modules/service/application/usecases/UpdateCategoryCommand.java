package com.danasea.backend.modules.service.application.usecases;

import java.util.UUID;

public record UpdateCategoryCommand(
    String name,
    String nameEn,
    String slug,
    UUID parentId,
    String iconUrl,
    Boolean isActive,
    Boolean requiresSafetyCert
) {
    public UpdateCategoryCommand(String name, String nameEn, String slug, UUID parentId, String iconUrl) {
        this(name, nameEn, slug, parentId, iconUrl, null, null);
    }

    public UpdateCategoryCommand(String name, String nameEn, String slug, UUID parentId, String iconUrl, Boolean isActive) {
        this(name, nameEn, slug, parentId, iconUrl, isActive, null);
    }
}

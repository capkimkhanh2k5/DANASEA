package com.danasea.backend.modules.service.application.usecases;

import java.util.UUID;

public record CreateCategoryCommand(
    UUID id,
    String name,
    String nameEn,
    String slug,
    UUID parentId,
    String iconUrl,
    Boolean requiresSafetyCert
) {
    public CreateCategoryCommand(String name, String nameEn, String slug, UUID parentId, String iconUrl) {
        this(null, name, nameEn, slug, parentId, iconUrl, false);
    }

    public CreateCategoryCommand(UUID id, String name, String nameEn, String slug, UUID parentId, String iconUrl) {
        this(id, name, nameEn, slug, parentId, iconUrl, false);
    }

    public CreateCategoryCommand(String name, String nameEn, String slug, UUID parentId, String iconUrl, Boolean requiresSafetyCert) {
        this(null, name, nameEn, slug, parentId, iconUrl, requiresSafetyCert);
    }
}

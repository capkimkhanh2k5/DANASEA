package com.danasea.backend.modules.service.presentation.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
    UUID id,
    String name,
    String nameEn,
    String slug,
    UUID parentId,
    String iconUrl,
    Boolean isActive,
    Boolean requiresSafetyCert,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public CategoryResponse(
            UUID id,
            String name,
            String nameEn,
            String slug,
            UUID parentId,
            String iconUrl,
            Boolean isActive,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this(id, name, nameEn, slug, parentId, iconUrl, isActive, false, createdAt, updatedAt);
    }
}

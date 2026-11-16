package com.danasea.backend.modules.service.domain.exceptions;

import java.util.UUID;

public class CategoryHasPublishedServicesWithoutSafetyDocumentsException extends RuntimeException {

    public CategoryHasPublishedServicesWithoutSafetyDocumentsException(UUID categoryId) {
        super("Category has published services without approved safety documents. Category ID: " + categoryId);
    }
}

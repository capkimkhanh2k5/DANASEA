package com.danasea.backend.modules.service.domain.exceptions;

import java.util.UUID;

public class CategoryHasChildrenException extends RuntimeException {
    public CategoryHasChildrenException(UUID categoryId) {
        super("Không thể xóa danh mục " + categoryId + " vì đang có danh mục con.");
    }
}

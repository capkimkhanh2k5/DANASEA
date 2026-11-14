package com.danasea.backend.modules.service.domain.exceptions;

import java.util.UUID;

public class CategoryHasLinkedServicesException extends RuntimeException {
    public CategoryHasLinkedServicesException(UUID categoryId) {
        super("Không thể xóa danh mục " + categoryId + " vì đang có dịch vụ liên kết.");
    }
}

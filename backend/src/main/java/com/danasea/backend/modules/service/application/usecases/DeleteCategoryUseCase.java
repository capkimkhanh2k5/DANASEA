package com.danasea.backend.modules.service.application.usecases;

import java.util.UUID;

import com.danasea.backend.modules.service.application.ports.output.CategoryRepositoryPort;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasChildrenException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasDependentRulesException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryHasLinkedServicesException;
import com.danasea.backend.modules.service.domain.exceptions.CategoryNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteCategoryUseCase {

    private final CategoryRepositoryPort categoryRepositoryPort;

    @Transactional
    public void execute(UUID id) {
        if (!categoryRepositoryPort.existsById(id)) {
            throw new CategoryNotFoundException(id);
        }

        if (categoryRepositoryPort.hasLinkedServices(id)) {
            throw new CategoryHasLinkedServicesException(id);
        }

        if (categoryRepositoryPort.hasChildren(id)) {
            throw new CategoryHasChildrenException(id);
        }

        if (categoryRepositoryPort.hasCommissionRules(id)) {
            throw new CategoryHasDependentRulesException("Không thể xóa danh mục đang liên kết với biểu phí hoa hồng.");
        }

        if (categoryRepositoryPort.hasSafetyRules(id)) {
            throw new CategoryHasDependentRulesException("Không thể xóa danh mục đang liên kết với quy tắc an toàn thời tiết.");
        }

        categoryRepositoryPort.deleteById(id);
    }
}

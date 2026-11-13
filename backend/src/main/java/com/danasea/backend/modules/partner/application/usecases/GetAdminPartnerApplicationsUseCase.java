package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAdminPartnerApplicationsUseCase {

    private final JpaVendorRoleApplicationRepository applicationRepository;
    private final PartnerApplicationMapper mapper;

    @Transactional(readOnly = true)
    public Page<PartnerApplicationResponse> execute(PartnerApplicationStatus status, String search, Pageable pageable) {
        return applicationRepository.searchApplications(status, toContainsPattern(search), pageable)
                .map(mapper::toDomain)
                .map(mapper::toResponse);
    }

    private static String toContainsPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}

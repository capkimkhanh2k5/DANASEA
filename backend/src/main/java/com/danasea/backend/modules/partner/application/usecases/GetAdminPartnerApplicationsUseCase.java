package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.partner.domain.models.PartnerApplicationStatus;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.entities.VendorRoleApplicationJpaEntity;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GetAdminPartnerApplicationsUseCase {

    private final JpaVendorRoleApplicationRepository applicationRepository;
    private final PartnerApplicationMapper mapper;

    @Transactional(readOnly = true)
    public Page<PartnerApplicationResponse> execute(PartnerApplicationStatus status, String search, Pageable pageable) {
        String pattern = toContainsPattern(search);
        Page<VendorRoleApplicationJpaEntity> page;
        if (pattern == null) {
            if (status == null) {
                page = applicationRepository.findAll(pageable);
            } else {
                page = applicationRepository.findByStatus(status, pageable);
            }
        } else {
            page = applicationRepository.searchApplications(status, pattern, pageable);
        }
        return page.map(mapper::toDomain).map(mapper::toResponse);
    }

    private static String toContainsPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String clean = search.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + clean + "%";
    }
}

package com.danasea.backend.modules.partner.application.usecases;

import com.danasea.backend.modules.partner.domain.exceptions.ApplicationNotFoundException;
import com.danasea.backend.modules.partner.infrastructure.mappers.PartnerApplicationMapper;
import com.danasea.backend.modules.partner.infrastructure.persistence.repositories.JpaVendorRoleApplicationRepository;
import com.danasea.backend.modules.partner.presentation.dtos.PartnerApplicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetAdminPartnerApplicationDetailUseCase {

    private final JpaVendorRoleApplicationRepository applicationRepository;
    private final PartnerApplicationMapper mapper;

    @Transactional(readOnly = true)
    public PartnerApplicationResponse execute(UUID id) {
        return applicationRepository.findById(id)
                .map(mapper::toDomain)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ApplicationNotFoundException("error.application_not_found"));
    }
}

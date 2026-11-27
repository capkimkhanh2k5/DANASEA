package com.danasea.backend.modules.service.application.usecases;

import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.service.application.dtos.ServiceResult;
import com.danasea.backend.modules.service.application.usecases.helpers.ServiceResultMapper;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceImage;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetServiceDetailUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final ServiceImageRepositoryPort serviceImageRepository;
    private final VendorPort vendorPort;

    public ServiceResult execute(UUID userId, UUID serviceId) {
        Vendor vendor = vendorPort.findByUserId(userId)
                .orElseThrow(() -> new VendorNotApprovedException("Vendor not found for user: " + userId));

        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        service.validateOwnership(vendor.getId());

        List<ServiceImage> images = serviceImageRepository.findByServiceId(serviceId);
        return ServiceResultMapper.toResult(service, images);
    }
}

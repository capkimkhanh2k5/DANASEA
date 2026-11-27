package com.danasea.backend.modules.service.application.usecases;

import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.service.application.dtos.ServiceResult;
import com.danasea.backend.modules.service.application.usecases.helpers.ServiceResultMapper;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceImage;
import com.danasea.backend.modules.service.domain.ports.ServiceImageRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetVendorServicesUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final ServiceImageRepositoryPort serviceImageRepository;
    private final VendorPort vendorPort;

    public List<ServiceResult> execute(UUID userId) {
        Vendor vendor = vendorPort.findByUserId(userId)
                .orElseThrow(() -> new VendorNotApprovedException("Vendor not found for user: " + userId));

        List<Service> services = serviceRepository.findByVendorId(vendor.getId());

        return services.stream()
                .map(svc -> {
                    List<ServiceImage> images = serviceImageRepository.findByServiceId(svc.getId());
                    return ServiceResultMapper.toResult(svc, images);
                })
                .toList();
    }
}

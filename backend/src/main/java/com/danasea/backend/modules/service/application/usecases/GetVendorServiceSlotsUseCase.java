package com.danasea.backend.modules.service.application.usecases;

import java.util.List;
import java.util.UUID;

import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotResponse;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotUnitResponse;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetVendorServiceSlotsUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final ServiceSlotRepositoryPort serviceSlotRepository;
    private final VendorPort vendorPort;

    @Transactional(readOnly = true)
    public List<ServiceSlotResponse> execute(UUID userId, UUID serviceId) {
        Vendor vendor = vendorPort.findByUserId(userId)
                .orElseThrow(() -> new VendorNotApprovedException("Vendor not found for user: " + userId));

        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        service.validateOwnership(vendor.getId());

        List<ServiceSlot> slots = serviceSlotRepository.findByServiceId(serviceId);
        return slots.stream()
                .map(this::toResponse)
                .toList();
    }

    private ServiceSlotResponse toResponse(ServiceSlot s) {
        List<ServiceSlotUnitResponse> unitResponses = s.getUnits() != null
                ? s.getUnits().stream().map(this::toUnitResponse).toList()
                : List.of();

        return ServiceSlotResponse.builder()
                .id(s.getId())
                .serviceId(s.getServiceId())
                .date(s.getDate())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .status(s.getStatus())
                .inventoryType(s.getInventoryType())
                .capacity(s.getCapacity())
                .bookedCount(s.getBookedCount())
                .availableCapacity(s.getAvailableCapacity())
                .units(unitResponses)
                .build();
    }

    private ServiceSlotUnitResponse toUnitResponse(ServiceSlotUnit u) {
        return ServiceSlotUnitResponse.builder()
                .id(u.getId())
                .unitNumber(u.getUnitNumber())
                .capacity(u.getCapacity())
                .bookedCount(u.getBookedCount())
                .availableCapacity(u.getAvailableCapacity())
                .equipmentId(u.getEquipmentId())
                .equipmentBlocked(u.isEquipmentBlocked())
                .build();
    }
}

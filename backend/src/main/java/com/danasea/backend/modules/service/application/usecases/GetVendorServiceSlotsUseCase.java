package com.danasea.backend.modules.service.application.usecases;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotResponse;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotUnitResponse;
import com.danasea.backend.modules.vendor.domain.models.Vendor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetVendorServiceSlotsUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final ServiceSlotRepositoryPort serviceSlotRepository;
    private final VendorPort vendorPort;
    private final ServiceOptionRepositoryPort serviceOptionRepository;

    @Autowired
    public GetVendorServiceSlotsUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceSlotRepositoryPort serviceSlotRepository,
            VendorPort vendorPort,
            ServiceOptionRepositoryPort serviceOptionRepository) {
        this.serviceRepository = serviceRepository;
        this.serviceSlotRepository = serviceSlotRepository;
        this.vendorPort = vendorPort;
        this.serviceOptionRepository = serviceOptionRepository;
    }

    public GetVendorServiceSlotsUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceSlotRepositoryPort serviceSlotRepository,
            VendorPort vendorPort) {
        this(serviceRepository, serviceSlotRepository, vendorPort, null);
    }

    @Transactional(readOnly = true)
    public List<ServiceSlotResponse> execute(UUID userId, UUID serviceId) {
        Vendor vendor = vendorPort.findByUserId(userId)
                .orElseThrow(() -> new VendorNotApprovedException("Vendor not found for user: " + userId));

        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        service.validateOwnership(vendor.getId());

        List<ServiceSlot> slots = serviceSlotRepository.findByServiceId(serviceId);
        Map<UUID, ServiceOption> optionMap = serviceOptionRepository != null
                ? serviceOptionRepository.findByServiceId(serviceId).stream()
                        .collect(Collectors.toMap(ServiceOption::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        return slots.stream()
                .map(s -> toResponse(s, s.getOptionId() != null ? optionMap.get(s.getOptionId()) : null))
                .toList();
    }

    private ServiceSlotResponse toResponse(ServiceSlot s, ServiceOption option) {
        List<ServiceSlotUnitResponse> unitResponses = s.getUnits() != null
                ? s.getUnits().stream().map(this::toUnitResponse).toList()
                : List.of();

        return ServiceSlotResponse.builder()
                .id(s.getId())
                .serviceId(s.getServiceId())
                .optionId(s.getOptionId())
                .optionType(option != null ? option.getOptionType() : null)
                .optionName(option != null ? option.getName() : null)
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

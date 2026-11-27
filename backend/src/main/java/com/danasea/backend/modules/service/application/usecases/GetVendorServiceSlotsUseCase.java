package com.danasea.backend.modules.service.application.usecases;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.danasea.backend.modules.service.application.services.SlotAvailabilityCalculator;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotOptionSummaryResponse;
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
                .map(s -> toResponse(s, optionMap))
                .toList();
    }

    private ServiceSlotResponse toResponse(ServiceSlot s, Map<UUID, ServiceOption> optionMap) {
        List<ServiceSlotUnitResponse> unitResponses = s.getUnits() != null
                ? s.getUnits().stream().map(this::toUnitResponse).toList()
                : List.of();

        List<UUID> optIds = (s.getOptionIds() != null && !s.getOptionIds().isEmpty())
                ? s.getOptionIds()
                : (s.getOptionId() != null ? List.of(s.getOptionId()) : List.of());

        List<ServiceSlotOptionSummaryResponse> optionSummaries = optIds.stream()
                .map(optionMap::get)
                .filter(Objects::nonNull)
                .map(this::toOptionSummary)
                .toList();

        String bindingMode = (s.getOptionIds() != null && !s.getOptionIds().isEmpty())
                ? "V61_MULTI"
                : (s.getOptionId() != null ? "LEGACY_OPTION_ID" : "LEGACY_UNBOUND");

        List<ServiceSlotOptionSummaryResponse> legacyCompatible = "LEGACY_UNBOUND".equals(bindingMode)
                ? optionMap.values().stream()
                        .filter(o -> OptionStatus.ACTIVE.equals(o.getStatus()))
                        .filter(o -> SlotAvailabilityCalculator.supportsOption(s, o))
                        .map(this::toOptionSummary)
                        .toList()
                : List.of();

        UUID singleOptionId = (optIds.size() == 1) ? optIds.get(0) : s.getOptionId();
        OptionType singleOptionType = (optionSummaries.size() == 1)
                ? optionSummaries.get(0).optionType()
                : (optionSummaries.stream().map(ServiceSlotOptionSummaryResponse::optionType).distinct().count() == 1 && !optionSummaries.isEmpty()
                        ? optionSummaries.get(0).optionType()
                        : null);
        String singleOptionName = (optionSummaries.size() == 1) ? optionSummaries.get(0).name() : null;

        return ServiceSlotResponse.builder()
                .id(s.getId())
                .serviceId(s.getServiceId())
                .optionId(singleOptionId)
                .optionType(singleOptionType)
                .optionName(singleOptionName)
                .options(optionSummaries)
                .optionBindingMode(bindingMode)
                .legacyCompatibleOptions(legacyCompatible)
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

    private ServiceSlotOptionSummaryResponse toOptionSummary(ServiceOption o) {
        return new ServiceSlotOptionSummaryResponse(
                o.getId(),
                o.getName(),
                o.getOptionType(),
                o.getPricingUnit(),
                o.getPrice(),
                o.getMaxPaxPerPackage()
        );
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

package com.danasea.backend.modules.service.application.usecases;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.shared.presentation.ApiConflictException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.adapters.OperatingScheduleStore;
import com.danasea.backend.modules.service.presentation.dtos.CreateSlotUnitRequest;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotOptionSummaryResponse;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotResponse;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotUnitResponse;
import com.danasea.backend.modules.service.presentation.dtos.UpdateServiceSlotRequest;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpdateServiceSlotUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final ServiceSlotRepositoryPort serviceSlotRepository;
    private final VendorPort vendorPort;
    private final OperatingScheduleStore scheduleStore;
    private final ServiceOptionRepositoryPort serviceOptionRepository;

    @Autowired
    public UpdateServiceSlotUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceSlotRepositoryPort serviceSlotRepository,
            VendorPort vendorPort,
            OperatingScheduleStore scheduleStore,
            ServiceOptionRepositoryPort serviceOptionRepository) {
        this.serviceRepository = serviceRepository;
        this.serviceSlotRepository = serviceSlotRepository;
        this.vendorPort = vendorPort;
        this.scheduleStore = scheduleStore;
        this.serviceOptionRepository = serviceOptionRepository;
    }

    public UpdateServiceSlotUseCase(
            ServiceRepositoryPort serviceRepository,
            ServiceSlotRepositoryPort serviceSlotRepository,
            VendorPort vendorPort,
            OperatingScheduleStore scheduleStore) {
        this(serviceRepository, serviceSlotRepository, vendorPort, scheduleStore, null);
    }

    @Transactional
    public ServiceSlotResponse execute(UUID userId, UUID serviceId, UUID slotId, UpdateServiceSlotRequest request) {
        return execute(userId, serviceId, slotId, request, LocalDate.now(Booking.VIETNAM_ZONE));
    }

    @Transactional
    public ServiceSlotResponse execute(UUID userId, UUID serviceId, UUID slotId, UpdateServiceSlotRequest request, LocalDate referenceDate) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        Vendor vendor = vendorPort.findByUserId(userId)
                .orElseThrow(() -> new VendorNotApprovedException("Vendor not found for user: " + userId));

        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));

        service.validateOwnership(vendor.getId());

        ServiceSlot slot = serviceSlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + slotId));

        if (!serviceId.equals(slot.getServiceId())) {
            throw new IllegalArgumentException("Slot does not belong to the specified service");
        }

        LocalDate today = referenceDate != null ? referenceDate : LocalDate.now(Booking.VIETNAM_ZONE);
        if (slot.getDate().isBefore(today)) {
            throw new IllegalArgumentException("Cannot modify a slot from a past date");
        }

        if (request.status() == SlotStatus.OPEN && scheduleStore.blocks(serviceId, slot.getDate(), slot.getStartTime())) {
            throw new ApiConflictException("OPERATING_SCHEDULE_CONFLICT", "An operating schedule exception blocks this slot");
        }

        if (request.status() != null) {
            if (slot.getStatus() == SlotStatus.CANCELLED
                    || request.status() == SlotStatus.CANCELLED) {
                throw new IllegalArgumentException("Use the departure cancellation endpoint for cancellations.");
            }
            slot.setStatus(request.status());
        }

        List<ServiceSlotUnit> currentUnits = serviceSlotRepository.findUnitsBySlotId(slotId);
        List<ServiceSlotUnit> updatedUnits = new ArrayList<>(currentUnits);

        if (InventoryType.SHARED_CAPACITY_UNITS.equals(slot.getInventoryType())) {
            if (request.units() != null && !request.units().isEmpty()) {
                Map<Integer, ServiceSlotUnit> currentUnitMap = currentUnits.stream()
                        .collect(Collectors.toMap(ServiceSlotUnit::getUnitNumber, Function.identity()));

                Set<Integer> newUnitNumbers = new HashSet<>();
                List<ServiceSlotUnit> nextUnits = new ArrayList<>();
                int sumCapacity = 0;

                for (CreateSlotUnitRequest uReq : request.units()) {
                    if (uReq.unitNumber() == null || uReq.unitNumber() <= 0) {
                        throw new IllegalArgumentException("Unit number must be greater than 0");
                    }
                    if (!newUnitNumbers.add(uReq.unitNumber())) {
                        throw new IllegalArgumentException("Duplicate unit number: " + uReq.unitNumber());
                    }
                    if (uReq.capacity() == null || uReq.capacity() <= 0) {
                        throw new IllegalArgumentException("Each unit capacity must be greater than 0");
                    }

                    ServiceSlotUnit existing = currentUnitMap.get(uReq.unitNumber());
                    if (existing != null && existing.getEquipmentId() != null
                            && !uReq.capacity().equals(existing.getCapacity())) {
                        throw new IllegalArgumentException("Cannot resize a unit linked to physical equipment");
                    }
                    if (existing != null && !uReq.capacity().equals(existing.getCapacity())
                            && serviceSlotRepository.hasPrivateCommitmentForUnit(slotId, uReq.unitNumber())) {
                        throw new IllegalArgumentException("Cannot resize a unit reserved for a private package");
                    }
                    int committed = serviceSlotRepository.getCommittedCountForUnit(slotId, uReq.unitNumber());
                    int booked = (existing != null && existing.getBookedCount() != null) ? existing.getBookedCount() : 0;
                    int occupied = Math.max(booked, committed);

                    if (uReq.capacity() < occupied) {
                        throw new IllegalArgumentException("Cannot reduce unit " + uReq.unitNumber() + " capacity below committed capacity (" + booked + ")");
                    }

                    nextUnits.add(ServiceSlotUnit.builder()
                            .id(existing != null ? existing.getId() : UUID.randomUUID())
                            .slotId(slotId)
                            .unitNumber(uReq.unitNumber())
                            .capacity(uReq.capacity())
                            .bookedCount(booked)
                            .equipmentId(existing == null ? null : existing.getEquipmentId())
                            .equipmentBlocked(existing != null && existing.isEquipmentBlocked())
                            .build());
                    sumCapacity = Math.addExact(sumCapacity, uReq.capacity());
                }

                // Check removed units: if any removed unit has booked > 0 or has active hold -> reject!
                for (ServiceSlotUnit oldU : currentUnits) {
                    if (!newUnitNumbers.contains(oldU.getUnitNumber())) {
                        if (oldU.getEquipmentId() != null) {
                            throw new IllegalArgumentException("A unit linked to physical equipment must be managed by equipment maintenance");
                        }
                        int committed = serviceSlotRepository.getCommittedCountForUnit(slotId, oldU.getUnitNumber());
                        int booked = Math.max(oldU.getBookedCount() != null ? oldU.getBookedCount() : 0, committed);
                        if (booked > 0) {
                            throw new IllegalArgumentException("Only completely empty units can be removed; unit " + oldU.getUnitNumber() + " has active bookings or holds");
                        }
                    }
                }

                for (ServiceSlotUnit oldUnit : currentUnits) {
                    if (!newUnitNumbers.contains(oldUnit.getUnitNumber())) {
                        serviceSlotRepository.deleteUnit(slotId, oldUnit.getUnitNumber());
                    }
                }
                serviceSlotRepository.saveUnits(slotId, nextUnits);
                slot.setCapacity(sumCapacity);
                updatedUnits = nextUnits;
            }
        } else {
            // PERSON_LIMIT
            if (request.capacity() != null) {
                int booked = slot.getBookedCount() != null ? slot.getBookedCount() : 0;
                if (request.capacity() <= 0 || request.capacity() < booked + serviceSlotRepository.getActiveHeldCount(slotId)) {
                    throw new IllegalArgumentException("Cannot reduce capacity below committed guests (" + booked + ")");
                }
                slot.setCapacity(request.capacity());
            }
        }

        ServiceSlot saved = serviceSlotRepository.save(slot);
        return toResponse(saved, updatedUnits);
    }

    private ServiceSlotResponse toResponse(ServiceSlot s, List<ServiceSlotUnit> units) {
        List<ServiceSlotUnitResponse> unitResponses = units != null
                ? units.stream().map(this::toUnitResponse).toList()
                : List.of();

        List<UUID> optIds = (s.getOptionIds() != null && !s.getOptionIds().isEmpty())
                ? s.getOptionIds()
                : (s.getOptionId() != null ? List.of(s.getOptionId()) : List.of());

        List<ServiceSlotOptionSummaryResponse> optionSummaries = serviceOptionRepository != null ? optIds.stream()
                .map(serviceOptionRepository::findById)
                .flatMap(Optional::stream)
                .map(o -> new ServiceSlotOptionSummaryResponse(
                        o.getId(),
                        o.getName(),
                        o.getOptionType(),
                        o.getPricingUnit(),
                        o.getPrice(),
                        o.getMaxPaxPerPackage()
                ))
                .toList() : List.of();

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

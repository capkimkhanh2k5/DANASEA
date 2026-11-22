package com.danasea.backend.modules.service.application.usecases;

import static com.danasea.backend.modules.service.domain.exceptions.ServiceSlotCreationException.conflict;
import static com.danasea.backend.modules.service.domain.exceptions.ServiceSlotCreationException.invalid;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.exceptions.VendorNotApprovedException;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.ServiceSlotUnit;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.VendorPort;
import com.danasea.backend.modules.service.infrastructure.persistence.adapters.OperatingScheduleStore;
import com.danasea.backend.modules.service.presentation.dtos.CreateServiceSlotRequest;
import com.danasea.backend.modules.service.presentation.dtos.CreateSlotUnitRequest;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotResponse;
import com.danasea.backend.modules.service.presentation.dtos.ServiceSlotUnitResponse;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CreateServiceSlotUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final ServiceSlotRepositoryPort serviceSlotRepository;
    private final VendorPort vendorPort;
    private final OperatingScheduleStore scheduleStore;

    @Transactional
    public ServiceSlotResponse execute(UUID userId, UUID serviceId, CreateServiceSlotRequest request) {
        if (request == null) {
            throw invalid("error.bad_request");
        }

        lockOwnedService(userId, serviceId);
        ServiceSlot slot = prepareSlot(serviceId, request);
        checkAvailability(slot, LocalDateTime.now(Booking.VIETNAM_ZONE), false);
        return persistSlot(slot);
    }

    List<ServiceSlotResponse> createBatch(UUID userId, UUID serviceId, List<CreateServiceSlotRequest> requests) {
        List<ServiceSlot> slots = requests.stream()
                .map(request -> prepareSlot(serviceId, request))
                .toList();

        lockOwnedService(userId, serviceId);
        LocalDateTime now = LocalDateTime.now(Booking.VIETNAM_ZONE);
        slots.forEach(slot -> checkAvailability(slot, now, true));
        return slots.stream().map(this::persistSlot).toList();
    }

    private void lockOwnedService(UUID userId, UUID serviceId) {
        Vendor vendor = vendorPort.findByUserId(userId)
                .orElseThrow(() -> new VendorNotApprovedException("Vendor not found for user: " + userId));
        Service service = serviceRepository.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException(serviceId));
        service.validateOwnership(vendor.getId());
        service.validateSlotCreatable();
    }

    private void checkAvailability(ServiceSlot slot, LocalDateTime now, boolean batch) {
        if (!LocalDateTime.of(slot.getDate(), slot.getStartTime()).isAfter(now)) {
            throw invalid("error.slot_past",
                    slot.getDate().toString(), slot.getStartTime().toString());
        }
        if (scheduleStore.blocks(slot.getServiceId(), slot.getDate(), slot.getStartTime())) {
            throw conflict("OPERATING_SCHEDULE_CONFLICT", "error.slot_schedule_conflict",
                    slot.getDate(), slot.getStartTime());
        }
        if (serviceSlotRepository.existsByServiceIdAndDateAndStartTime(
                slot.getServiceId(), slot.getDate(), slot.getStartTime())) {
            if (batch) {
                throw conflict("SLOT_ALREADY_EXISTS", "error.slot_already_exists",
                        slot.getDate(), slot.getStartTime());
            }
            throw invalid("error.slot_already_exists",
                    slot.getDate().toString(), slot.getStartTime().toString());
        }
    }

    private ServiceSlot prepareSlot(UUID serviceId, CreateServiceSlotRequest request) {
        if (request.date() == null || request.startTime() == null || request.endTime() == null) {
            throw invalid("error.slot_date_time_required");
        }
        if (request.startTime().getNano() != 0 || request.endTime().getNano() != 0) {
            throw invalid("error.slot_time_precision");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw invalid("error.slot_time_order",
                    request.startTime().toString(), request.endTime().toString());
        }
        if (request.inventoryType() == null) {
            throw invalid("validation.service.slot.inventory.required");
        }

        int totalCapacity;
        List<ServiceSlotUnit> units = new ArrayList<>();
        UUID slotId = UUID.randomUUID();

        if (InventoryType.SHARED_CAPACITY_UNITS.equals(request.inventoryType())) {
            if (request.units() == null || request.units().isEmpty()) {
                throw invalid("error.slot_units_required");
            }

            Set<Integer> unitNumbers = new HashSet<>();
            long sum = 0;
            for (CreateSlotUnitRequest unit : request.units()) {
                if (unit == null || unit.unitNumber() == null || unit.unitNumber() <= 0) {
                    throw invalid("error.slot_unit_number");
                }
                if (!unitNumbers.add(unit.unitNumber())) {
                    throw invalid("error.slot_duplicate_unit", unit.unitNumber());
                }
                if (unit.capacity() == null || unit.capacity() <= 0) {
                    throw invalid("error.slot_unit_capacity", unit.unitNumber());
                }
                sum += unit.capacity();
                if (sum > Integer.MAX_VALUE) {
                    throw invalid("error.slot_unit_capacity_overflow");
                }
                units.add(ServiceSlotUnit.builder()
                        .id(UUID.randomUUID())
                        .slotId(slotId)
                        .unitNumber(unit.unitNumber())
                        .capacity(unit.capacity())
                        .bookedCount(0)
                        .build());
            }
            totalCapacity = (int) sum;
        } else {
            if (request.capacity() == null || request.capacity() <= 0) {
                throw invalid("error.slot_capacity");
            }
            totalCapacity = request.capacity();
        }

        ServiceSlot slot = ServiceSlot.builder()
                .serviceId(serviceId)
                .date(request.date())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .capacity(totalCapacity)
                .bookedCount(0)
                .status(SlotStatus.OPEN)
                .inventoryType(request.inventoryType())
                .units(units)
                .build();
        slot.setId(slotId);
        return slot;
    }

    private ServiceSlotResponse persistSlot(ServiceSlot slot) {
        ServiceSlot saved = serviceSlotRepository.save(slot);
        List<ServiceSlotUnit> units = slot.getUnits();
        if (!units.isEmpty()) {
            serviceSlotRepository.saveUnits(slot.getId(), units);
        }
        return toResponse(saved, units);
    }

    private ServiceSlotResponse toResponse(ServiceSlot s, List<ServiceSlotUnit> units) {
        List<ServiceSlotUnitResponse> unitResponses = units != null
                ? units.stream().map(this::toUnitResponse).toList()
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

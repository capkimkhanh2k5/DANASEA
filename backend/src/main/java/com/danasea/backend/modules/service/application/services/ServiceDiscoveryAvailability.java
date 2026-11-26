package com.danasea.backend.modules.service.application.services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.service.application.dtos.SearchServicesCriteria;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.presentation.dtos.StructuredSlotResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ServiceDiscoveryAvailability {
    private final ServiceOptionRepositoryPort options;
    private final ServiceSlotRepositoryPort slots;
    private final SlotAvailabilityCalculator availability;

    @Transactional(readOnly = true)
    public boolean matches(UUID serviceId, SearchServicesCriteria criteria) {
        var activeOptions = options.findByServiceIdAndStatus(serviceId, OptionStatus.ACTIVE);
        int guests = criteria.getGuests() == null ? 1 : criteria.getGuests();
        return slots.findByServiceId(serviceId).stream().filter(SlotAvailabilityCalculator::isUpcoming)
                .filter(slot -> criteria.getDate() == null || criteria.getDate().equals(slot.getDate()))
                .filter(slot -> criteria.getTimeSlot() == null || criteria.getTimeSlot().equals(slot.getStartTime()))
                .anyMatch(slot -> activeOptions.stream()
                        .filter(option -> SlotAvailabilityCalculator.supportsOption(slot, option))
                        .anyMatch(option -> {
                    if (option.isPrivate()) {
                        int maxPax = option.getMaxPaxPerPackage() == null ? 0 : option.getMaxPaxPerPackage();
                        return maxPax > 0 && availability.canBook(slot, option, (guests - 1) / maxPax + 1, false);
                    }
                    return availability.canBook(slot, option, guests, false);
                }));
    }

    @Transactional(readOnly = true)
    public List<StructuredSlotResponse> describe(Service service) {
        var activeOptions = options.findByServiceIdAndStatus(service.getId(), OptionStatus.ACTIVE);
        List<StructuredSlotResponse> result = new ArrayList<>();
        for (var slot : slots.findByServiceId(service.getId())) {
            if (!SlotAvailabilityCalculator.isUpcoming(slot)) {
                continue;
            }
            for (var option : activeOptions) {
                if (!SlotAvailabilityCalculator.supportsOption(slot, option)) {
                    continue;
                }
                var snapshot = availability.evaluate(slot, option);
                result.add(StructuredSlotResponse.builder().slotId(slot.getId()).date(slot.getDate())
                        .startTime(slot.getStartTime()).endTime(slot.getEndTime()).capacity(slot.getCapacity())
                        .availableCapacity(snapshot.available()).price(option.getPrice())
                        .optionId(option.getId()).pricingUnit(option.getPricingUnit())
                        .maxPaxPerPackage(option.getMaxPaxPerPackage()).inventoryType(slot.getInventoryType())
                        .bookable(snapshot.canBook(1, option.isShared(), false, slot.getInventoryType())).build());
            }
        }
        return result;
    }
}

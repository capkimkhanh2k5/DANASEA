package com.danasea.backend.modules.service.application.usecases;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.service.application.services.SlotAvailabilityCalculator;
import com.danasea.backend.modules.service.domain.exceptions.ServiceNotFoundException;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.Service;
import com.danasea.backend.modules.service.domain.models.ServiceOption;
import com.danasea.backend.modules.service.domain.models.ServiceSlot;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.domain.ports.ServiceOptionRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceRepositoryPort;
import com.danasea.backend.modules.service.domain.ports.ServiceSlotRepositoryPort;
import com.danasea.backend.modules.service.presentation.dtos.PublicServiceSlotAvailabilityResponse;

@Component
public class GetServiceSlotsAvailabilityUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final ServiceOptionRepositoryPort serviceOptionRepository;
    private final ServiceSlotRepositoryPort serviceSlotRepository;
    private final SlotAvailabilityCalculator availability;

    @Autowired
    public GetServiceSlotsAvailabilityUseCase(ServiceRepositoryPort serviceRepository,
            ServiceOptionRepositoryPort serviceOptionRepository, ServiceSlotRepositoryPort serviceSlotRepository,
            SlotAvailabilityCalculator availability) {
        this.serviceRepository = serviceRepository;
        this.serviceOptionRepository = serviceOptionRepository;
        this.serviceSlotRepository = serviceSlotRepository;
        this.availability = availability;
    }

    public GetServiceSlotsAvailabilityUseCase(ServiceRepositoryPort serviceRepository,
            ServiceOptionRepositoryPort serviceOptionRepository, ServiceSlotRepositoryPort serviceSlotRepository,
            StringRedisTemplate redisTemplate) {
        this(serviceRepository, serviceOptionRepository, serviceSlotRepository,
                new SlotAvailabilityCalculator(serviceSlotRepository, redisTemplate));
    }

    @Transactional(readOnly = true)
    public List<PublicServiceSlotAvailabilityResponse> execute(
            UUID serviceId,
            UUID optionId,
            LocalDate from,
            LocalDate to,
            Integer quantity
    ) {
        return execute(serviceId, optionId, from, to, quantity, false);
    }

    @Transactional(readOnly = true)
    public List<PublicServiceSlotAvailabilityResponse> execute(UUID serviceId, UUID optionId,
            LocalDate from, LocalDate to, Integer quantity, boolean allowSplit) {
        if (quantity != null && quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("End date must not precede start date");
        }
        Service service = serviceRepository.findPublishedById(serviceId)
                .orElseThrow(() -> new ServiceNotFoundException("Service not found or not published: " + serviceId));

        ServiceOption option;
        if (optionId != null) {
            option = serviceOptionRepository.findById(optionId)
                    .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));
            if (!serviceId.equals(option.getServiceId()) || !OptionStatus.ACTIVE.equals(option.getStatus())) {
                throw new IllegalArgumentException("Option does not belong to service or is no longer active");
            }
        } else {
            List<ServiceOption> activeOptions = serviceOptionRepository.findByServiceIdAndStatus(serviceId, OptionStatus.ACTIVE);
            if (activeOptions.isEmpty()) {
                return Collections.emptyList();
            }
            if (activeOptions.size() > 1) {
                throw new IllegalArgumentException("An active optionId must be selected");
            }
            option = activeOptions.get(0);
        }

        LocalDate today = LocalDate.now(Booking.VIETNAM_ZONE);
        LocalTime nowTime = LocalTime.now(Booking.VIETNAM_ZONE);

        LocalDate queryFrom = (from != null && !from.isBefore(today)) ? from : today;
        LocalDate queryTo = (to != null && !to.isBefore(queryFrom)) ? to : queryFrom.plusDays(30);

        List<ServiceSlot> slots = serviceSlotRepository.findByServiceIdAndDateBetween(serviceId, queryFrom, queryTo);
        if (slots.isEmpty()) {
            return Collections.emptyList();
        }

        List<PublicServiceSlotAvailabilityResponse> responses = new ArrayList<>();

        for (ServiceSlot slot : slots) {
            if (!SlotStatus.OPEN.equals(slot.getStatus())) {
                continue;
            }
            if (slot.getOptionIds() != null && !slot.getOptionIds().isEmpty()) {
                if (!slot.getOptionIds().contains(option.getId())) {
                    continue;
                }
            } else if (slot.getOptionId() != null && !slot.getOptionId().equals(option.getId())) {
                continue;
            }
            if (slot.getDate().isBefore(today)) {
                continue;
            }
            if (slot.getDate().isEqual(today) && slot.getStartTime() != null && !slot.getStartTime().isAfter(nowTime)) {
                continue;
            }

            var snapshot = availability.evaluate(slot, option);
            int availablePaxOrPackages = snapshot.available();
            int reqQty = (quantity != null && quantity > 0) ? quantity : 1;
            boolean bookable = snapshot.canBook(reqQty, option.isShared(), allowSplit, slot.getInventoryType());

            responses.add(PublicServiceSlotAvailabilityResponse.builder()
                    .slotId(slot.getId())
                    .serviceId(serviceId)
                    .optionId(option.getId())
                    .date(slot.getDate())
                    .startTime(slot.getStartTime())
                    .endTime(slot.getEndTime())
                    .price(option.getPrice())
                    .pricingUnit(option.getPricingUnit())
                    .status(slot.getStatus())
                    .availablePaxOrPackages(availablePaxOrPackages)
                    .maxPaxPerPackage(option.getMaxPaxPerPackage())
                    .inventoryType(slot.getInventoryType())
                    .bookable(bookable)
                    .build());
        }

        return responses;
    }

}

package com.danasea.backend.modules.booking.infrastructure.adapters;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.booking.domain.exceptions.InsufficientInventoryException;
import com.danasea.backend.modules.booking.domain.models.BookingItem;
import com.danasea.backend.modules.booking.domain.models.BookingItemAllocation;
import com.danasea.backend.modules.booking.domain.models.SlotValidationDetails;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionStatus;
import com.danasea.backend.modules.service.domain.models.ServiceStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceOptionJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotUnitJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ServiceSlotAdapter implements ServiceSlotPort {

    private final JpaServiceSlotRepository jpaServiceSlotRepository;
    private final JpaServiceRepository jpaServiceRepository;
    private final JpaServiceSlotUnitRepository jpaServiceSlotUnitRepository;
    private final JpaServiceOptionRepository jpaServiceOptionRepository;

    @Override
    @Transactional
    public void lockSlotsForUpdate(List<UUID> slotIds) {
        slotIds.stream().distinct().sorted().forEach(jpaServiceSlotRepository::findByIdForUpdate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlotValidationDetails> findSlotDetails(UUID slotId) {
        if (slotId == null) {
            return Optional.empty();
        }
        List<SlotValidationDetails> list = findSlotDetailsBatch(List.of(slotId));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotValidationDetails> findSlotDetailsBatch(List<UUID> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ServiceSlotJpaEntity> slotEntities = jpaServiceSlotRepository.findAllById(slotIds);
        if (slotEntities.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> serviceIds = slotEntities.stream()
                .map(ServiceSlotJpaEntity::getServiceId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<UUID, ServiceJpaEntity> serviceMap = jpaServiceRepository.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(ServiceJpaEntity::getId, Function.identity()));

        List<ServiceSlotUnitJpaEntity> unitEntities = jpaServiceSlotUnitRepository.findBySlotIdInOrderBySlotIdAscUnitNumberAsc(slotIds);
        Map<UUID, List<ServiceSlotUnitJpaEntity>> unitsBySlotId = unitEntities.stream()
                .collect(Collectors.groupingBy(ServiceSlotUnitJpaEntity::getSlotId));

        Map<UUID, List<ServiceOptionJpaEntity>> optionsByServiceId = new HashMap<>();
        for (UUID serviceId : serviceIds) {
            List<ServiceOptionJpaEntity> opts = jpaServiceOptionRepository.findByServiceIdAndStatusOrderByCreatedAtAsc(serviceId, OptionStatus.ACTIVE);
            optionsByServiceId.put(serviceId, opts);
        }

        return slotEntities.stream()
                .map(slot -> {
                    ServiceJpaEntity service = serviceMap.get(slot.getServiceId());
                    boolean published = service != null && ServiceStatus.PUBLISHED.equals(service.getStatus());

                    List<ServiceSlotUnitJpaEntity> rawUnits = unitsBySlotId.getOrDefault(slot.getId(), Collections.emptyList());
                    List<SlotValidationDetails.SlotUnitValidationDetails> slotUnits = rawUnits.stream()
                            .filter(u -> !u.isEquipmentBlocked())
                            .map(u -> SlotValidationDetails.SlotUnitValidationDetails.builder()
                                    .id(u.getId())
                                    .unitNumber(u.getUnitNumber())
                                    .capacity(u.getCapacity())
                                    .bookedCount(u.getBookedCount() != null ? u.getBookedCount() : 0)
                                    .build())
                            .toList();

                    List<ServiceOptionJpaEntity> rawOpts = optionsByServiceId.getOrDefault(slot.getServiceId(), Collections.emptyList());
                    Map<UUID, SlotValidationDetails.ServiceOptionValidationDetails> optionMap = rawOpts.stream()
                            .collect(Collectors.toMap(
                                    ServiceOptionJpaEntity::getId,
                                    o -> SlotValidationDetails.ServiceOptionValidationDetails.builder()
                                            .id(o.getId())
                                            .name(o.getName())
                                            .optionType(o.getOptionType())
                                            .pricingUnit(o.getPricingUnit())
                                            .price(o.getPrice())
                                            .maxPaxPerPackage(o.getMaxPaxPerPackage())
                                            .status(o.getStatus())
                                            .build()
                            ));

                    return SlotValidationDetails.builder()
                            .slotId(slot.getId())
                            .serviceId(slot.getServiceId())
                            .vendorId(service != null ? service.getVendorId() : null)
                            .serviceName(service != null ? service.getName() : null)
                            .bookingDate(slot.getDate())
                            .bookingTime(slot.getStartTime())
                            .capacity(slot.getCapacity())
                            .bookedCount(slot.getBookedCount())
                            .status(slot.getStatus())
                            .inventoryType(slot.getInventoryType() != null ? slot.getInventoryType() : InventoryType.PERSON_LIMIT)
                            .optionsConfigured(jpaServiceOptionRepository.existsByServiceId(slot.getServiceId()))
                            .units(slotUnits)
                            .options(optionMap)
                            .price(service != null ? service.getPrice() : null)
                            .servicePublished(published)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public void commitCapacityBatch(List<BookingItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        lockSlotsForUpdate(items.stream().map(BookingItem::getSlotId).toList());

        for (BookingItem item : items) {
            if (item.getAllocations() != null && !item.getAllocations().isEmpty()) {
                int totalAllocated = 0;
                for (BookingItemAllocation alloc : item.getAllocations()) {
                    int updated = jpaServiceSlotUnitRepository.incrementUnitBookedCount(
                            item.getSlotId(), alloc.getUnitNumber(), alloc.getAllocatedSeats());
                    if (updated == 0) {
                        throw new InsufficientInventoryException(item.getSlotId(), alloc.getAllocatedSeats(), 0);
                    }
                    totalAllocated += alloc.getAllocatedSeats();
                }
                int slotUpdated = jpaServiceSlotRepository.incrementBookedCount(item.getSlotId(), totalAllocated);
                if (slotUpdated == 0) {
                    throw new InsufficientInventoryException(item.getSlotId(), totalAllocated, 0);
                }
            } else {
                int updated = jpaServiceSlotRepository.incrementBookedCount(item.getSlotId(), item.getQuantity());
                if (updated == 0) {
                    throw new InsufficientInventoryException(item.getSlotId(), item.getQuantity(), 0);
                }
            }
        }
    }

    @Override
    @Transactional
    public void releaseCapacityBatch(List<BookingItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        lockSlotsForUpdate(items.stream().map(BookingItem::getSlotId).filter(Objects::nonNull).toList());
        for (BookingItem item : items) {
            if (item.getId() != null) {
                jpaServiceSlotRepository.releaseBookingItemCapacity(item.getId());
                continue;
            }
            if (item.getSlotId() != null) {
                if (item.getAllocations() != null && !item.getAllocations().isEmpty()) {
                    int totalAllocated = 0;
                    for (BookingItemAllocation alloc : item.getAllocations()) {
                        jpaServiceSlotUnitRepository.decrementUnitBookedCount(
                                item.getSlotId(), alloc.getUnitNumber(), alloc.getAllocatedSeats());
                        totalAllocated += alloc.getAllocatedSeats();
                    }
                    jpaServiceSlotRepository.decrementBookedCount(item.getSlotId(), totalAllocated);
                } else if (item.getQuantity() != null && item.getQuantity() > 0) {
                    jpaServiceSlotRepository.decrementBookedCount(item.getSlotId(), item.getQuantity());
                }
            }
        }
    }
}

package com.danasea.backend.modules.booking.application.usecases;

import com.danasea.backend.modules.booking.application.dtos.BookingHoldItemDto;
import com.danasea.backend.modules.booking.application.dtos.BookingHoldItemResult;
import com.danasea.backend.modules.booking.application.dtos.BookingHoldResult;
import com.danasea.backend.modules.booking.application.dtos.CreateBookingHoldCommand;
import com.danasea.backend.modules.booking.domain.exceptions.SlotNotAvailableException;
import com.danasea.backend.modules.booking.domain.models.Booking;
import com.danasea.backend.modules.booking.domain.models.BookingItem;
import com.danasea.backend.modules.booking.domain.models.BookingItemAllocation;
import com.danasea.backend.modules.booking.domain.models.InventoryLockItem;
import com.danasea.backend.modules.booking.domain.models.SlotValidationDetails;
import com.danasea.backend.modules.booking.domain.ports.BookingRepositoryPort;
import com.danasea.backend.modules.booking.domain.ports.InventoryLockPort;
import com.danasea.backend.modules.booking.domain.ports.ServiceSlotPort;
import com.danasea.backend.modules.booking.domain.ports.FlashSaleBookingPort;
import com.danasea.backend.modules.flashsale.domain.FlashSaleException;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.PricingUnit;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RequiredArgsConstructor
public class CreateBookingHoldUseCase {

    public static final Duration HOLD_DURATION = Duration.ofMinutes(15);
    private static final int MAX_REQUEST_ITEMS = 20;
    private static final int MAX_TOTAL_QUANTITY = 100;

    private final BookingRepositoryPort bookingRepository;
    private final InventoryLockPort inventoryLockPort;
    private final ServiceSlotPort serviceSlotPort;
    private FlashSaleBookingPort flashSale;
    @org.springframework.beans.factory.annotation.Autowired
    public void setFlashSale(FlashSaleBookingPort flashSale) { this.flashSale=flashSale; }

    private record AggregationKey(UUID slotId, UUID optionId, Integer participantsCount, boolean allowSplit, UUID flashSaleId, int groupIndex) {}

    @Transactional
    public BookingHoldResult execute(CreateBookingHoldCommand command) {
        // 1. Validate basic input
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        if (command.customerId() == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("Booking must contain at least one item");
        }
        if (command.items().size() > MAX_REQUEST_ITEMS) {
            throw new IllegalArgumentException("Booking must contain at most " + MAX_REQUEST_ITEMS + " items");
        }
        boolean promotional=command.items().stream().anyMatch(i->i!=null && i.flashSaleId()!=null);
        String fingerprint=null;
        if(promotional) {
            if(flashSale==null) throw new FlashSaleException("FLASH_SALE_NOT_CONFIGURED");
            try { fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(command.items().toString().getBytes(StandardCharsets.UTF_8))); }
            catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            UUID replay=flashSale.replay(command.customerId(),command.idempotencyKey(),fingerprint);
            if(replay!=null) return mapToResult(bookingRepository.findByIdWithItems(replay).orElseThrow());
        }

        // 2. Aggregate quantities by (slotId, optionId, participantsCount)
        Map<AggregationKey, Integer> aggregatedQuantities = new LinkedHashMap<>();
        int totalQuantity = 0;
        int groupIndex = 0;
        for (BookingHoldItemDto item : command.items()) {
            if (item == null) {
                throw new IllegalArgumentException("Booking item cannot be null");
            }
            if (item.participantsCount() != null && item.participantsCount() <= 0) {
                throw new IllegalArgumentException("Participants per package must be positive");
            }
            if (item.slotId() == null) {
                throw new IllegalArgumentException("slotId cannot be null");
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            try {
                totalQuantity = Math.addExact(totalQuantity, item.quantity());
                AggregationKey key = new AggregationKey(item.slotId(), item.optionId(), item.participantsCount(), item.allowSplit(),item.flashSaleId(),
                        item.optionId() != null ? ++groupIndex : 0);
                aggregatedQuantities.merge(key, item.quantity(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Total booking quantity is too large", exception);
            }
            if (totalQuantity > MAX_TOTAL_QUANTITY) {
                throw new IllegalArgumentException(
                        "Total booking quantity must not exceed " + MAX_TOTAL_QUANTITY);
            }
        }

        // 3. Query slot and service details to validate business rules
        List<UUID> slotIds = aggregatedQuantities.keySet().stream()
                .map(AggregationKey::slotId)
                .distinct()
                .toList();

        if(promotional) flashSale.lockOffers(orderedOfferIds(command));
        serviceSlotPort.lockSlotsForUpdate(slotIds);
        List<SlotValidationDetails> slotDetailsList = serviceSlotPort.findSlotDetailsBatch(slotIds);
        Map<UUID, SlotValidationDetails> slotDetailsMap = slotDetailsList.stream()
                .collect(Collectors.toMap(SlotValidationDetails::getSlotId, Function.identity()));

        LocalDateTime nowAtVenue = LocalDateTime.now(Booking.VIETNAM_ZONE);
        for (Map.Entry<AggregationKey, Integer> entry : aggregatedQuantities.entrySet()) {
            UUID slotId = entry.getKey().slotId();
            SlotValidationDetails details = slotDetailsMap.get(slotId);
            if (details == null) {
                throw new SlotNotAvailableException(slotId, "Slot does not exist");
            }
            if (!details.isServicePublished()) {
                throw new SlotNotAvailableException(slotId, "Associated service is not published");
            }
            if (!details.isOpen()) {
                throw new SlotNotAvailableException(slotId, "Slot status is " + details.getStatus());
            }
            if (details.getBookingDate() == null || details.getBookingTime() == null
                    || !LocalDateTime.of(details.getBookingDate(), details.getBookingTime()).isAfter(nowAtVenue)) {
                throw new SlotNotAvailableException(slotId, "Cannot book slots in the past");
            }
        }

        // 4. Build lock items for Redis batch Lua script
        List<InventoryLockItem> lockItems = new ArrayList<>();
        List<AggregationKey> orderedKeys = new ArrayList<>(aggregatedQuantities.keySet());

        for (AggregationKey key : orderedKeys) {
            int qty = aggregatedQuantities.get(key);
            SlotValidationDetails details = slotDetailsMap.get(key.slotId());
            SlotValidationDetails.ServiceOptionValidationDetails option = resolveOption(details, key.optionId());
            if (option != null && option.isPrivate()) {
                if (!InventoryType.SHARED_CAPACITY_UNITS.equals(details.getInventoryType())) {
                    throw new IllegalArgumentException("Private packages require shared capacity units");
                }
                if (option.getMaxPaxPerPackage() == null || option.getMaxPaxPerPackage() <= 0) {
                    throw new IllegalArgumentException("Private package capacity is not configured");
                }
                if (key.participantsCount() != null && key.participantsCount() > option.getMaxPaxPerPackage()) {
                    throw new IllegalArgumentException("Participants exceed the private package limit");
                }
            }

            if (InventoryType.SHARED_CAPACITY_UNITS.equals(details.getInventoryType())) {
                List<InventoryLockItem.UnitLockInfo> unitInfos = details.getUnits().stream()
                        .map(u -> new InventoryLockItem.UnitLockInfo(u.getUnitNumber(), u.getCapacity(), u.getBookedCount()))
                        .toList();

                OptionType optType = option != null ? option.getOptionType() : OptionType.SHARED;
                Integer pax = option != null ? option.getMaxPaxPerPackage() : null;

                lockItems.add(InventoryLockItem.builder()
                        .slotId(key.slotId())
                        .quantity(qty)
                        .maxCapacity(details.getCapacity())
                        .inventoryType(InventoryType.SHARED_CAPACITY_UNITS)
                        .optionType(optType)
                        .paxPerPackage(pax != null ? pax : 1)
                        .allowSplit(key.allowSplit())
                        .units(unitInfos)
                        .allocations(new ArrayList<>())
                        .build());
            } else {
                // PERSON_LIMIT
                lockItems.add(InventoryLockItem.builder()
                        .slotId(key.slotId())
                        .quantity(qty)
                        .maxCapacity(details.getAvailableCapacity())
                        .inventoryType(InventoryType.PERSON_LIMIT)
                        .optionType(option != null ? option.getOptionType() : OptionType.SHARED)
                        .paxPerPackage(key.participantsCount())
                        .units(new ArrayList<>())
                        .allocations(new ArrayList<>())
                        .build());
            }
        }

        UUID bookingId = UUID.randomUUID();

        // 6. Build Booking & BookingItems and persist to DB with compensating rollback on failure
        try {
            List<BookingItem> bookingItems = new ArrayList<>();
            for (int i = 0; i < orderedKeys.size(); i++) {
                AggregationKey key = orderedKeys.get(i);
                int qty = aggregatedQuantities.get(key);
                InventoryLockItem lockItem = lockItems.get(i);
                SlotValidationDetails details = slotDetailsMap.get(key.slotId());
                SlotValidationDetails.ServiceOptionValidationDetails option = resolveOption(details, key.optionId());

                BigDecimal itemPrice = (option != null && option.getPrice() != null) ? option.getPrice() : details.getPrice();
                PricingUnit pricingUnit = option != null ? option.getPricingUnit() : PricingUnit.PER_PERSON;
                UUID optId = option != null ? option.getId() : null;

                UUID bookingItemId = UUID.randomUUID();
                if(key.flashSaleId()!=null) {
                    var snapshot=flashSale.reserve(key.flashSaleId(),details.getServiceId(),optId,itemPrice,
                            pricingUnit.name(),qty,bookingId,bookingItemId);
                    itemPrice=snapshot.originalUnitPrice().subtract(snapshot.unitSubsidy());
                }
                List<BookingItemAllocation> itemAllocations = new ArrayList<>();
                if (lockItem.getAllocations() != null) {
                    for (BookingItemAllocation alloc : lockItem.getAllocations()) {
                        alloc.setBookingItemId(bookingItemId);
                        itemAllocations.add(alloc);
                    }
                }

                bookingItems.add(BookingItem.builder()
                        .id(bookingItemId)
                        .bookingId(bookingId)
                        .slotId(key.slotId())
                        .serviceId(details.getServiceId())
                        .vendorId(details.getVendorId())
                        .optionId(optId)
                        .pricingUnit(pricingUnit)
                        .participantsCount(key.participantsCount())
                        .maxPaxPerPackage(option != null && option.isPrivate() ? option.getMaxPaxPerPackage() : null)
                        .allowSplit(key.allowSplit())
                        .quantity(qty)
                        .bookingDate(details.getBookingDate())
                        .bookingTime(details.getBookingTime())
                        .price(itemPrice)
                        .allocations(itemAllocations)
                        .build());
            }

            // 5. Atomic Redis inventory lock (All-or-Nothing)
            inventoryLockPort.acquireHolds(bookingId, lockItems, HOLD_DURATION);
            for(int i=0;i<bookingItems.size();i++) {
                var item=bookingItems.get(i);
                var allocations=lockItems.get(i).getAllocations();
                if(allocations!=null) {
                    allocations.forEach(a->a.setBookingItemId(item.getId()));
                    item.setAllocations(new ArrayList<>(allocations));
                }
            }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            inventoryLockPort.releaseHolds(bookingId, lockItems);
                        }
                    }
                });
            }

            OffsetDateTime now = OffsetDateTime.now();
            Booking booking = Booking.createHold(bookingId, command.customerId(), bookingItems, HOLD_DURATION, now);
            Booking savedBooking = bookingRepository.save(booking);
            if(promotional) flashSale.remember(command.customerId(),command.idempotencyKey(),fingerprint,bookingId);

            return mapToResult(savedBooking);
        } catch (Exception ex) {
            // Compensating action: Release Redis holds immediately if database persistence fails
            inventoryLockPort.releaseHolds(bookingId, lockItems);
            throw ex;
        }
    }

    private List<UUID> orderedOfferIds(CreateBookingHoldCommand command) {
        return command.items().stream().map(BookingHoldItemDto::flashSaleId).filter(java.util.Objects::nonNull).distinct().sorted().toList();
    }
    private SlotValidationDetails.ServiceOptionValidationDetails resolveOption(SlotValidationDetails details, UUID optionId) {
        if (optionId != null) {
            var option = details.getOptions().get(optionId);
            if (option == null || !option.isActive()) {
                throw new IllegalArgumentException("Option is invalid or inactive");
            }
            return option;
        }
        if (details.getOptions().size() == 1) {
            return details.getOptions().values().iterator().next();
        }
        if (details.isOptionsConfigured() || !details.getOptions().isEmpty()) {
            throw new IllegalArgumentException("An active optionId must be selected");
        }
        return null;
    }

    private BookingHoldResult mapToResult(Booking booking) {
        List<BookingHoldItemResult> itemResults = booking.getItems().stream()
                .map(item -> new BookingHoldItemResult(
                        item.getId(),
                        item.getServiceId(),
                        item.getVendorId(),
                        item.getSlotId(),
                        item.getQuantity(),
                        item.getBookingDate(),
                        item.getBookingTime(),
                        item.getPrice(),
                        item.calculateSubtotal()
                ))
                .toList();

        return new BookingHoldResult(
                booking.getId(),
                booking.getCustomerId(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getHoldExpiresAt(),
                itemResults,
                booking.getCreatedAt()
        );
    }
}

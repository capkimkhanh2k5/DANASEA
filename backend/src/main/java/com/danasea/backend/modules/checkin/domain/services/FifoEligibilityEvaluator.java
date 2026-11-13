package com.danasea.backend.modules.checkin.domain.services;

import com.danasea.backend.modules.booking.infrastructure.persistence.entities.BookingItemAllocationJpaEntity;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemAllocationRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.service.domain.models.InventoryType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotUnitJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class FifoEligibilityEvaluator {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private JpaServiceSlotUnitRepository slotUnitRepository;

    @Autowired(required = false)
    private JpaBookingItemAllocationRepository bookingItemAllocationRepository;

    @Autowired(required = false)
    private JpaBookingItemRepository bookingItemRepository;

    @Autowired(required = false)
    private JpaServiceOptionRepository serviceOptionRepository;

    @Autowired(required = false)
    private JpaServiceRepository serviceRepository;

    public record FifoEligibilityResult(
            boolean isEligible,
            int availableCapacity,
            String rejectionReason
    ) {
        public static FifoEligibilityResult eligible(int availableCapacity) {
            return new FifoEligibilityResult(true, availableCapacity, null);
        }

        public static FifoEligibilityResult rejected(String reason) {
            return new FifoEligibilityResult(false, 0, reason);
        }
    }

    public record FifoEvaluationContext(
            StringRedisTemplate redisTemplate,
            JpaBookingItemAllocationRepository bookingItemAllocationRepository,
            JpaServiceSlotUnitRepository slotUnitRepository,
            JpaBookingItemRepository bookingItemRepository,
            JpaServiceOptionRepository serviceOptionRepository,
            JpaServiceRepository serviceRepository
    ) {
        public static FifoEvaluationContext of(
                StringRedisTemplate redisTemplate,
                JpaBookingItemAllocationRepository bookingItemAllocationRepository,
                JpaServiceSlotUnitRepository slotUnitRepository,
                JpaBookingItemRepository bookingItemRepository,
                JpaServiceOptionRepository serviceOptionRepository,
                JpaServiceRepository serviceRepository) {
            return new FifoEvaluationContext(
                    redisTemplate,
                    bookingItemAllocationRepository,
                    slotUnitRepository,
                    bookingItemRepository,
                    serviceOptionRepository,
                    serviceRepository);
        }
    }

    public record RedisHoldSummary(
            int totalHeldSeats,
            Map<Integer, Integer> heldSeatsByUnit,
            Set<Integer> privateHeldUnits
    ) {}

    public FifoEligibilityResult evaluate(
            SubOrderJpaEntity subOrder,
            ServiceSlotJpaEntity currentSlot,
            ServiceSlotJpaEntity candidateSlot,
            ServiceSlotJpaEntity originalRegisteredSlot,
            OffsetDateTime now) {
        return evaluate(subOrder, currentSlot, candidateSlot, originalRegisteredSlot, now,
                new FifoEvaluationContext(redisTemplate, bookingItemAllocationRepository,
                        slotUnitRepository, bookingItemRepository, serviceOptionRepository, serviceRepository));
    }

    public FifoEligibilityResult evaluate(
            SubOrderJpaEntity subOrder,
            ServiceSlotJpaEntity currentSlot,
            ServiceSlotJpaEntity candidateSlot,
            ServiceSlotJpaEntity originalRegisteredSlot,
            OffsetDateTime now,
            FifoEvaluationContext context) {

        if (subOrder == null || currentSlot == null || candidateSlot == null || now == null) {
            return FifoEligibilityResult.rejected("Missing required arguments for FIFO evaluation");
        }

        // 0. BẤT BIẾN NGHIỆP VỤ: FIFO Early Dispatch CHỈ áp dụng cho SHARED/PUBLIC.
        // Khách đặt PRIVATE không tham gia FIFO dưới bất kỳ hình thức nào.
        if (isPrivateSubOrder(subOrder, context)) {
            return FifoEligibilityResult.rejected("FIFO early dispatch is strictly prohibited for PRIVATE bookings");
        }

        // 1. Dịch vụ phải trùng khớp
        if (!Objects.equals(candidateSlot.getServiceId(), subOrder.getServiceId())) {
            return FifoEligibilityResult.rejected("Target slot belongs to a different service");
        }

        // 2. Trạng thái ca đích phải là OPEN
        if (candidateSlot.getStatus() != SlotStatus.OPEN) {
            return FifoEligibilityResult.rejected("Target slot is not open. Current status: " + candidateSlot.getStatus());
        }

        // 3. Chỉ đề xuất trong cùng ngày vận hành
        if (!Objects.equals(candidateSlot.getDate(), currentSlot.getDate())) {
            return FifoEligibilityResult.rejected("Early dispatch is only permitted for slots on the same operating day");
        }

        // 4. Ca đích phải còn nhận check-in (chưa quá hạn chót T-15m của SHARED)
        // Theo PO: Ca đích chỉ đủ điều kiện khi now <= targetStart - 15 phút.
        // Hạn chót T-15m vẫn cho nhận; sau mốc đó bị từ chối tuyệt đối.
        LocalDate candDate = candidateSlot.getDate();
        LocalTime candStartTime = candidateSlot.getStartTime();
        if (candDate == null || candStartTime == null) {
            return FifoEligibilityResult.rejected("Target slot date or start time is missing");
        }
        OffsetDateTime targetStart = LocalDateTime.of(candDate, candStartTime).atZone(VIETNAM_ZONE).toOffsetDateTime();
        OffsetDateTime targetCutoff = targetStart.minusMinutes(15);
        if (now.isAfter(targetCutoff)) {
            return FifoEligibilityResult.rejected(
                    String.format("Target slot check-in cutoff has passed at %s (15 minutes before target start %s)",
                            targetCutoff, targetStart));
        }

        // 5. Ca đích phải sớm hơn ca đang chiếm giữ
        if (candidateSlot.getStartTime() == null || currentSlot.getStartTime() == null ||
                !candidateSlot.getStartTime().isBefore(currentSlot.getStartTime())) {
            return FifoEligibilityResult.rejected("Target slot start time must be strictly earlier than current slot");
        }

        // 6. Ca đích phải sớm hơn ca gốc đăng ký ban đầu
        if (originalRegisteredSlot != null && originalRegisteredSlot.getStartTime() != null) {
            if (!candidateSlot.getStartTime().isBefore(originalRegisteredSlot.getStartTime())) {
                return FifoEligibilityResult.rejected("Target slot is not earlier than original registered slot");
            }
        }

        // 7. Kiểm tra tương thích thời lượng dịch vụ (Duration equivalence)
        Duration currentDuration = calculateSlotDuration(currentSlot.getStartTime(), currentSlot.getEndTime());
        Duration candidateDuration = calculateSlotDuration(candidateSlot.getStartTime(), candidateSlot.getEndTime());
        if (!Objects.equals(currentDuration, candidateDuration)) {
            return FifoEligibilityResult.rejected(String.format(
                    "Target slot duration (%d mins) does not match current slot duration (%d mins). Different duration slots cannot be dispatched.",
                    candidateDuration.toMinutes(), currentDuration.toMinutes()));
        }

        // 8. Xác định OptionType và cấu hình Private/Shared
        OptionType optionType = OptionType.SHARED;
        if (context.bookingItemRepository() != null && subOrder.getBookingItemId() != null) {
            var biOpt = context.bookingItemRepository().findById(subOrder.getBookingItemId());
            if (biOpt.isPresent() && biOpt.get().getOptionId() != null && context.serviceOptionRepository() != null) {
                var opt = context.serviceOptionRepository().findById(biOpt.get().getOptionId());
                if (opt.isPresent() && opt.get().getOptionType() != null) {
                    optionType = opt.get().getOptionType();
                }
            }
        }
        if (optionType == OptionType.PRIVATE) {
            return FifoEligibilityResult.rejected("FIFO early dispatch is strictly prohibited for PRIVATE bookings");
        }

        // 9. Kiểm tra tồn kho khả dụng tổng thể bao gồm Redis Holds
        RedisHoldSummary holdSummary = parseRedisHolds(context.redisTemplate(), candidateSlot.getId(), System.currentTimeMillis());
        int targetBooked = candidateSlot.getBookedCount() != null ? candidateSlot.getBookedCount() : 0;
        int targetCapacity = candidateSlot.getCapacity() != null ? candidateSlot.getCapacity() : 0;
        int quantity = subOrder.getQuantity() != null ? subOrder.getQuantity() : 1;
        int availableCapacity = Math.max(0, targetCapacity - targetBooked - holdSummary.totalHeldSeats());

        if (availableCapacity < quantity) {
            return FifoEligibilityResult.rejected(String.format(
                    "Target slot does not have sufficient capacity. Available: %d (Booked: %d, Holds: %d), Required: %d",
                    availableCapacity, targetBooked, holdSummary.totalHeldSeats(), quantity));
        }

        // 10. Phân bổ đơn vị phương tiện (Unit Allocation) cho SHARED/PUBLIC
        List<BookingItemAllocationJpaEntity> allocations = null;
        if (context.bookingItemAllocationRepository() != null && subOrder.getBookingItemId() != null) {
            allocations = context.bookingItemAllocationRepository().findByBookingItemId(subOrder.getBookingItemId());
        }

        if (allocations != null && !allocations.isEmpty() && context.slotUnitRepository() != null) {
            UUID currentOccupiedSlotId = subOrder.getActualSlotId() != null ? subOrder.getActualSlotId() : subOrder.getSlotId();
            List<BookingItemAllocationJpaEntity> currentAllocations = allocations.stream()
                    .filter(a -> Objects.equals(a.getSlotId(), currentOccupiedSlotId))
                    .toList();

            if (!currentAllocations.isEmpty()) {
                List<ServiceSlotUnitJpaEntity> candidateUnits = context.slotUnitRepository().findBySlotIdOrderByUnitNumberAsc(candidateSlot.getId());
                if (candidateUnits == null || candidateUnits.isEmpty()) {
                    return FifoEligibilityResult.rejected("Target slot has no configured units for capacity allocation.");
                }

                // Mô phỏng phân bổ trên bộ nhớ để kiểm tra tính khả thi mà không tạo side effect vào DB
                Map<Integer, UnitSimulationState> simUnits = new HashMap<>();
                for (var u : candidateUnits) {
                    int uCap = u.getCapacity() != null ? u.getCapacity() : 0;
                    int uBooked = u.getBookedCount() != null ? u.getBookedCount() : 0;
                    int uHeld = holdSummary.heldSeatsByUnit().getOrDefault(u.getUnitNumber(), 0);
                    boolean uPrivHeld = holdSummary.privateHeldUnits().contains(u.getUnitNumber());
                    simUnits.put(u.getUnitNumber(), new UnitSimulationState(
                            u.getUnitNumber(), uCap, uBooked + uHeld, u.isEquipmentBlocked() || uPrivHeld, uPrivHeld));
                }

                for (var alloc : currentAllocations) {
                    if (Boolean.TRUE.equals(alloc.getIsPrivateLock())) {
                        return FifoEligibilityResult.rejected("FIFO early dispatch is strictly prohibited for private locked units");
                    }
                    int seatsNeeded = alloc.getAllocatedSeats() != null ? alloc.getAllocatedSeats() : quantity;
                    boolean reallocated = false;

                    for (var u : candidateUnits) {
                        UnitSimulationState state = simUnits.get(u.getUnitNumber());
                        if (state == null || state.isBlocked() || state.isPrivateLocked()) {
                            continue;
                        }
                        // SHARED: Phương tiện không bị khóa riêng và còn đủ chỗ trống
                        if ((state.getCapacity() - state.getBookedCount()) >= seatsNeeded) {
                            state.setBookedCount(state.getBookedCount() + seatsNeeded);
                            reallocated = true;
                            break;
                        }
                    }

                    if (!reallocated) {
                        return FifoEligibilityResult.rejected("Target slot does not have an available unit for allocation.");
                    }
                }
            }
        }

        return FifoEligibilityResult.eligible(availableCapacity);
    }

    public boolean isPrivateSubOrder(SubOrderJpaEntity subOrder, FifoEvaluationContext context) {
        if (subOrder == null) {
            return false;
        }
        if (context.bookingItemRepository() != null && subOrder.getBookingItemId() != null) {
            var biOpt = context.bookingItemRepository().findById(subOrder.getBookingItemId());
            if (biOpt.isPresent()) {
                var bi = biOpt.get();
                if (bi.getOptionId() != null && context.serviceOptionRepository() != null) {
                    var opt = context.serviceOptionRepository().findById(bi.getOptionId());
                    if (opt.isPresent() && opt.get().getOptionType() == OptionType.PRIVATE) {
                        return true;
                    }
                }
            }
        }
        if (context.bookingItemAllocationRepository() != null && subOrder.getBookingItemId() != null) {
            var allocations = context.bookingItemAllocationRepository().findByBookingItemId(subOrder.getBookingItemId());
            if (allocations != null) {
                for (var alloc : allocations) {
                    if (Boolean.TRUE.equals(alloc.getIsPrivateLock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static Duration calculateSlotDuration(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return Duration.ZERO;
        }
        if (end.isBefore(start)) {
            long minutesToEndOfDay = ChronoUnit.MINUTES.between(start, LocalTime.MAX) + 1;
            long minutesFromStartOfDay = ChronoUnit.MINUTES.between(LocalTime.MIN, end);
            return Duration.ofMinutes(minutesToEndOfDay + minutesFromStartOfDay);
        }
        return Duration.between(start, end);
    }

    public RedisHoldSummary parseRedisHolds(StringRedisTemplate template, UUID slotId, long nowMillis) {
        if (template == null || slotId == null) {
            return new RedisHoldSummary(0, Map.of(), Set.of());
        }
        try {
            Map<Object, Object> rawHolds = template.opsForHash().entries("inventory:slot:" + slotId + ":holds");
            if (rawHolds == null || rawHolds.isEmpty()) {
                return new RedisHoldSummary(0, Map.of(), Set.of());
            }

            int totalHeld = 0;
            Map<Integer, Integer> heldByUnit = new HashMap<>();
            Set<Integer> privateHeldUnits = new HashSet<>();

            for (Object valObj : rawHolds.values()) {
                String val = String.valueOf(valObj);
                int barPos = val.indexOf('|');
                if (barPos > 0) {
                    long exp = Long.parseLong(val.substring(0, barPos));
                    if (exp <= nowMillis) {
                        continue;
                    }
                    String details = val.substring(barPos + 1);
                    if (details.startsWith("PERSON_LIMIT:")) {
                        int qty = Integer.parseInt(details.substring("PERSON_LIMIT:".length()));
                        totalHeld += qty;
                    } else if (details.startsWith("SHARED_CAPACITY_UNITS|")) {
                        String[] parts = details.split("\\|");
                        if (parts.length >= 3) {
                            totalHeld += Integer.parseInt(parts[2]);
                        }
                    } else if (details.startsWith("UNITS:")) {
                        String unitsStr = details.substring("UNITS:".length());
                        for (String allocStr : unitsStr.split(";")) {
                            if (!allocStr.isBlank()) {
                                String[] parts = allocStr.split(":");
                                if (parts.length >= 2) {
                                    int uNum = Integer.parseInt(parts[0]);
                                    int uSeats = Integer.parseInt(parts[1]);
                                    boolean isPriv = parts.length >= 3 && "1".equals(parts[2]);
                                    totalHeld += uSeats;
                                    heldByUnit.merge(uNum, uSeats, Integer::sum);
                                    if (isPriv) {
                                        privateHeldUnits.add(uNum);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    int colonPos = val.indexOf(':');
                    if (colonPos > 0) {
                        int qty = Integer.parseInt(val.substring(0, colonPos));
                        long exp = Long.parseLong(val.substring(colonPos + 1));
                        if (exp > nowMillis) {
                            totalHeld += qty;
                        }
                    }
                }
            }

            return new RedisHoldSummary(totalHeld, heldByUnit, privateHeldUnits);
        } catch (Exception e) {
            log.warn("Failed to parse Redis holds for slot {}", slotId, e);
            return new RedisHoldSummary(0, Map.of(), Set.of());
        }
    }

    private static class UnitSimulationState {
        private final int unitNumber;
        private final int capacity;
        private int bookedCount;
        private boolean blocked;
        private boolean privateLocked;

        public UnitSimulationState(int unitNumber, int capacity, int bookedCount, boolean blocked, boolean privateLocked) {
            this.unitNumber = unitNumber;
            this.capacity = capacity;
            this.bookedCount = bookedCount;
            this.blocked = blocked;
            this.privateLocked = privateLocked;
        }

        public int getUnitNumber() { return unitNumber; }
        public int getCapacity() { return capacity; }
        public int getBookedCount() { return bookedCount; }
        public void setBookedCount(int bookedCount) { this.bookedCount = bookedCount; }
        public boolean isBlocked() { return blocked; }
        public void setBlocked(boolean blocked) { this.blocked = blocked; }
        public boolean isPrivateLocked() { return privateLocked; }
        public void setPrivateLocked(boolean privateLocked) { this.privateLocked = privateLocked; }
    }
}

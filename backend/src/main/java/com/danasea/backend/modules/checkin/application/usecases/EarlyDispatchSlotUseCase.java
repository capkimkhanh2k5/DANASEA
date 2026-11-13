package com.danasea.backend.modules.checkin.application.usecases;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemAllocationRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedVendorCheckinException;
import com.danasea.backend.modules.checkin.domain.services.FifoEligibilityEvaluator;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EarlyDispatchSlotUseCase {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JpaSubOrderRepository subOrderRepository;
    private final JpaServiceSlotRepository slotRepository;
    private final VendorLookupPort vendorLookupPort;

    @Autowired(required = false)
    private AuditLogInternalApi auditLogInternalApi;

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

    @Autowired(required = false)
    private FifoEligibilityEvaluator fifoEligibilityEvaluator = new FifoEligibilityEvaluator();

    public record EarlyDispatchRequest(
            UUID subOrderId,
            UUID targetSlotId,
            String dispatchReason
    ) {}

    public record EarlyDispatchResponse(
            UUID subOrderId,
            UUID originalSlotId,
            UUID actualSlotId,
            LocalDate slotDate,
            LocalTime startTime,
            LocalTime endTime,
            String message
    ) {}

    @Transactional
    public EarlyDispatchResponse execute(EarlyDispatchRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedVendorCheckinException("User is not authenticated"));
        return execute(request, currentUserId);
    }

    @Transactional
    public EarlyDispatchResponse execute(EarlyDispatchRequest request, UUID staffId) {
        return execute(request, staffId, OffsetDateTime.now(VIETNAM_ZONE));
    }

    @Transactional
    public EarlyDispatchResponse execute(EarlyDispatchRequest request, UUID staffId, OffsetDateTime evaluationTime) {
        if (request == null || request.subOrderId() == null || request.targetSlotId() == null) {
            throw new IllegalArgumentException("SubOrderId and targetSlotId are required");
        }

        // 1. Khóa SubOrder
        SubOrderJpaEntity subOrder = subOrderRepository.findByIdForUpdate(request.subOrderId())
                .orElseThrow(() -> new OrderNotFoundException("SubOrder not found: " + request.subOrderId()));

        // 2. Kiểm tra quyền Vendor
        boolean isAdmin = checkIsAdmin();
        if (!isAdmin) {
            UUID staffVendorId = resolveStaffVendorId(staffId);
            if (!Objects.equals(subOrder.getVendorId(), staffVendorId)) {
                throw new UnauthorizedVendorCheckinException("Staff does not belong to vendor owning this subOrder");
            }
        }

        // 3. Phải ở trạng thái CHECKED_IN
        if (subOrder.getStatus() != SubOrderStatus.CHECKED_IN) {
            throw new InvalidCheckinStateException(
                    "Only checked-in orders can be dispatched to an early slot. Current status: " + subOrder.getStatus());
        }

        UUID originalRegisteredSlotId = subOrder.getSlotId();
        // Slot hiện tại khách đang chiếm giữ: nếu đã từng chuyển ca thì là actualSlotId, chưa thì là slotId
        UUID currentOccupiedSlotId = subOrder.getActualSlotId() != null ? subOrder.getActualSlotId() : originalRegisteredSlotId;
        UUID targetSlotId = request.targetSlotId();

        if (Objects.equals(currentOccupiedSlotId, targetSlotId)) {
            throw new InvalidCheckinStateException("Target slot is the same as current occupied slot");
        }

        // 4. Khóa cả 2 slots (slot đang chiếm giữ và target slot) theo thứ tự ID để chống Deadlock
        UUID firstId = currentOccupiedSlotId.compareTo(targetSlotId) < 0 ? currentOccupiedSlotId : targetSlotId;
        UUID secondId = currentOccupiedSlotId.compareTo(targetSlotId) < 0 ? targetSlotId : currentOccupiedSlotId;

        ServiceSlotJpaEntity firstSlot = slotRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new InvalidCheckinStateException("Slot not found: " + firstId));
        ServiceSlotJpaEntity secondSlot = slotRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new InvalidCheckinStateException("Slot not found: " + secondId));

        ServiceSlotJpaEntity currentSlot = Objects.equals(firstId, currentOccupiedSlotId) ? firstSlot : secondSlot;
        ServiceSlotJpaEntity targetSlot = Objects.equals(firstId, targetSlotId) ? firstSlot : secondSlot;

        // Nếu ca đăng ký gốc khác ca đang chiếm giữ, đọc ca gốc để kiểm tra mốc thời gian
        ServiceSlotJpaEntity originalRegisteredSlot = null;
        if (!Objects.equals(originalRegisteredSlotId, currentOccupiedSlotId)) {
            originalRegisteredSlot = slotRepository.findById(originalRegisteredSlotId).orElse(null);
        } else {
            originalRegisteredSlot = currentSlot;
        }

        // 5. Kiểm tra toàn bộ điều kiện FIFO dùng chung thông qua FifoEligibilityEvaluator (ISSUE-FIFO-01)
        OffsetDateTime now = evaluationTime != null ? evaluationTime : OffsetDateTime.now(VIETNAM_ZONE);
        var evalContext = FifoEligibilityEvaluator.FifoEvaluationContext.of(
                redisTemplate,
                bookingItemAllocationRepository,
                slotUnitRepository,
                bookingItemRepository,
                serviceOptionRepository,
                serviceRepository);

        if (fifoEligibilityEvaluator.isPrivateSubOrder(subOrder, evalContext)) {
            throw new InvalidCheckinStateException(
                    "FIFO early dispatch is strictly prohibited for PRIVATE bookings. Customer must attend the originally booked slot.");
        }

        var evalResult = fifoEligibilityEvaluator.evaluate(
                subOrder, currentSlot, targetSlot, originalRegisteredSlot, now, evalContext);

        if (!evalResult.isEligible()) {
            throw new InvalidCheckinStateException(evalResult.rejectionReason());
        }

        // 6. Xử lý phân bổ khoang / unit nếu dịch vụ dùng SHARED_CAPACITY_UNITS (SHARED-only)
        if (bookingItemAllocationRepository != null && slotUnitRepository != null && subOrder.getBookingItemId() != null) {
            var allocations = bookingItemAllocationRepository.findByBookingItemId(subOrder.getBookingItemId());
            if (allocations != null && !allocations.isEmpty()) {
                var targetUnits = slotUnitRepository.findBySlotIdOrderByUnitNumberAsc(targetSlotId);
                for (var alloc : allocations) {
                    if (Objects.equals(alloc.getSlotId(), currentOccupiedSlotId)) {
                        boolean isPrivate = Boolean.TRUE.equals(alloc.getIsPrivateLock());
                        if (isPrivate) {
                            throw new InvalidCheckinStateException(
                                    "Private locked allocations cannot be dispatched early via FIFO.");
                        }

                        slotUnitRepository.decrementUnitBookedCount(currentOccupiedSlotId, alloc.getUnitNumber(), alloc.getAllocatedSeats());
                        // Gán vào unit khả dụng đầu tiên của ca mới (SHARED)
                        boolean reallocated = false;
                        for (var u : targetUnits) {
                            int uCap = u.getCapacity() != null ? u.getCapacity() : 0;
                            int uBooked = u.getBookedCount() != null ? u.getBookedCount() : 0;
                            if (u.isEquipmentBlocked()) {
                                continue;
                            }
                            if ((uCap - uBooked) >= alloc.getAllocatedSeats()) {
                                slotUnitRepository.incrementUnitBookedCount(targetSlotId, u.getUnitNumber(), alloc.getAllocatedSeats());
                                u.setBookedCount(uBooked + alloc.getAllocatedSeats());
                                alloc.setSlotId(targetSlotId);
                                alloc.setUnitNumber(u.getUnitNumber());
                                bookingItemAllocationRepository.save(alloc);
                                reallocated = true;
                                break;
                            }
                        }
                        if (!reallocated) {
                            throw new InvalidCheckinStateException(
                                    "Target slot does not have an available unit for allocation.");
                        }
                    }
                }
            }
        }

        // 7. Chuyển giao tồn kho nguyên tử giữa ca đang chiếm giữ và ca mới
        int currentBooked = currentSlot.getBookedCount() != null ? currentSlot.getBookedCount() : 0;
        int targetBooked = targetSlot.getBookedCount() != null ? targetSlot.getBookedCount() : 0;
        int quantity = subOrder.getQuantity() != null ? subOrder.getQuantity() : 1;

        if (currentBooked < quantity) {
            throw new InvalidCheckinStateException(String.format(
                    "Invariant violation: current slot booked count (%d) is less than required quantity (%d). Cannot safely release inventory.",
                    currentBooked, quantity));
        }
        currentSlot.setBookedCount(currentBooked - quantity);
        targetSlot.setBookedCount(targetBooked + quantity);

        slotRepository.save(currentSlot);
        slotRepository.save(targetSlot);

        // 8. Cập nhật SubOrder: Ghi nhận ca thực tế nhưng GIỮ NGUYÊN slotId gốc để tính Completion
        subOrder.setActualSlotId(targetSlotId);
        subOrderRepository.save(subOrder);

        // 9. Ghi Audit Log
        if (auditLogInternalApi != null) {
            auditLogInternalApi.recordTransactionalAuditLog(
                    staffId,
                    "FIFO_EARLY_DISPATCH",
                    "SUB_ORDER",
                    subOrder.getId(),
                    String.format("{\"originalSlotId\":\"%s\",\"previousOccupiedSlotId\":\"%s\",\"actualSlotId\":\"%s\",\"quantity\":%d,\"staff\":\"%s\"}",
                            originalRegisteredSlotId, currentOccupiedSlotId, targetSlotId, quantity, staffId)
            );
        }

        log.info("FIFO_DISPATCH_SUCCESS: SubOrder {} moved to early slot {} (previous: {}, original: {})",
                subOrder.getId(), targetSlotId, currentOccupiedSlotId, originalRegisteredSlotId);

        return new EarlyDispatchResponse(
                subOrder.getId(),
                originalRegisteredSlotId,
                targetSlotId,
                targetSlot.getDate(),
                targetSlot.getStartTime(),
                targetSlot.getEndTime(),
                "Customer successfully dispatched to earlier slot via FIFO agreement"
        );
    }

    private UUID resolveStaffVendorId(UUID staffId) {
        if (vendorLookupPort == null) {
            return staffId;
        }
        Optional<UUID> vendorIdOpt = vendorLookupPort.findVendorIdByUserId(staffId);
        if (vendorIdOpt.isPresent()) {
            return vendorIdOpt.get();
        }
        throw new UnauthorizedVendorCheckinException("No valid vendor profile is associated with account: " + staffId);
    }

    private boolean checkIsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}

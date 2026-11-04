package com.danasea.backend.modules.checkin.application.usecases;

import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidSubOrderStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedCheckinAccessException;
import com.danasea.backend.modules.checkin.domain.services.QrTokenSigner;
import com.danasea.backend.modules.checkin.infrastructure.persistence.entities.CheckinTokenJpaEntity;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.checkin.presentation.dtos.GenerateQrResponse;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateCheckinQrUseCase {

    private final JpaCheckinTokenRepository checkinTokenRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaServiceSlotRepository slotRepository;
    private final QrTokenSigner qrTokenSigner;

    @Transactional
    public GenerateQrResponse execute(UUID identifier) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedCheckinAccessException("User is not authenticated"));
        return execute(identifier, currentUserId);
    }

    @Transactional
    public GenerateQrResponse execute(UUID identifier, UUID customerId) {
        log.info("Generating check-in QR for identifier: {}, customerId: {}", identifier, customerId);

        // 1. Phân giải đa tầng (Dual-resolution: SubOrder hoặc MasterOrder)
        SubOrderJpaEntity subOrder = resolveSubOrder(identifier);

        // 2. Kiểm tra quyền sở hữu đơn hàng (IDOR Protection)
        MasterOrderJpaEntity masterOrder = masterOrderRepository.findById(subOrder.getMasterOrderId())
                .orElseThrow(() -> new OrderNotFoundException("Master order not found with id: " + subOrder.getMasterOrderId()));

        boolean isAdmin = checkIsAdmin();
        if (!isAdmin && (masterOrder.getCustomerId() == null || !Objects.equals(masterOrder.getCustomerId(), customerId))) {
            log.warn("IDOR_ATTEMPT: User {} attempted to retrieve a QR code owned by user {}", customerId, masterOrder.getCustomerId());
            throw new UnauthorizedCheckinAccessException("User does not have permission to view or generate QR for this booking");
        }

        // 3. Kiểm tra trạng thái đơn hàng
        if (subOrder.getStatus() == SubOrderStatus.PENDING) {
            throw new InvalidSubOrderStateException("Cannot generate a QR code for a pending, unpaid sub-order");
        }
        if (subOrder.getStatus() == SubOrderStatus.CANCELLED || subOrder.getStatus() == SubOrderStatus.REFUNDED) {
            throw new InvalidSubOrderStateException("Cannot generate a QR code for a cancelled or refunded sub-order in status: " + subOrder.getStatus());
        }
        if (subOrder.getStatus() != SubOrderStatus.CONFIRMED) {
            throw new InvalidSubOrderStateException("Only confirmed sub-orders can generate QR codes. Current status: " + subOrder.getStatus());
        }

        // 4. Tính toán thời gian hết hạn (expiresAt) và kiểm tra slot đã kết thúc chưa
        OffsetDateTime expiresAt = calculateExpirationAndValidateSlot(subOrder);

        // 5. Sinh chuỗi token ký số HMAC-SHA256 tất định
        String rawToken = qrTokenSigner.generateToken(subOrder.getId(), expiresAt);
        String tokenHash = QrTokenSigner.hashToken(rawToken);

        // 6. Lưu trữ hoặc cập nhật bản ghi CheckinToken (Tuyệt đối KHÔNG lưu plaintext token)
        Optional<CheckinTokenJpaEntity> existingOpt = checkinTokenRepository
                .findFirstBySubOrderIdAndUsedAtIsNullOrderByCreatedAtDesc(subOrder.getId());
        CheckinTokenJpaEntity tokenEntity = existingOpt.orElseGet(() -> CheckinTokenJpaEntity.builder()
                .subOrderId(subOrder.getId())
                .usedAt(null)
                .usedByVendorStaffId(null)
                .build());

        if (tokenEntity.getUsedAt() != null) {
            throw new InvalidCheckinStateException("The check-in ticket for this order was used at " + tokenEntity.getUsedAt());
        }

        tokenEntity.setQrTokenHash(tokenHash);
        tokenEntity.setExpiresAt(expiresAt);
        checkinTokenRepository.save(tokenEntity);

        log.info("Generated QR code for sub-order: {}, expiresAt: {}", subOrder.getId(), expiresAt);
        return new GenerateQrResponse(subOrder.getId(), rawToken, expiresAt);
    }

    private SubOrderJpaEntity resolveSubOrder(UUID identifier) {
        // Tầng 1: Tra cứu trực tiếp sub_orders
        Optional<SubOrderJpaEntity> subOpt = subOrderRepository.findById(identifier);
        if (subOpt.isPresent()) {
            return subOpt.get();
        }

        // Tầng 2: Tra cứu master_orders
        Optional<MasterOrderJpaEntity> masterOpt = masterOrderRepository.findById(identifier);
        if (masterOpt.isPresent()) {
            List<SubOrderJpaEntity> subOrders = subOrderRepository.findByMasterOrderId(identifier);
            if (subOrders.size() == 1) {
                return subOrders.get(0);
            }
            if (subOrders.size() > 1) {
                throw new IllegalArgumentException(
                        "This order contains multiple service items. Generate a QR code with a sub-order ID.");
            }
        }

        throw new OrderNotFoundException("Order or service item not found: " + identifier);
    }

    private OffsetDateTime calculateExpirationAndValidateSlot(SubOrderJpaEntity subOrder) {
        ZoneOffset vnOffset = ZoneOffset.ofHours(7);
        OffsetDateTime now = OffsetDateTime.now();

        if (subOrder.getSlotId() != null) {
            Optional<ServiceSlotJpaEntity> slotOpt = slotRepository.findById(subOrder.getSlotId());
            if (slotOpt.isPresent()) {
                ServiceSlotJpaEntity slot = slotOpt.get();
                if (slot.getStatus() == SlotStatus.CANCELLED
                        || (subOrder.getBookingItemId() != null
                        && slotRepository.hasBlockedEquipmentForBookingItem(subOrder.getBookingItemId()))) {
                    throw new InvalidCheckinStateException("The departure or assigned equipment is unavailable.");
                }
                if (slot.getDate() != null) {
                    LocalTime endTime = slot.getEndTime() != null
                            ? slot.getEndTime()
                            : (slot.getStartTime() != null ? slot.getStartTime().plusHours(4) : LocalTime.of(23, 59, 59));

                    OffsetDateTime slotEnd = LocalDateTime.of(slot.getDate(), endTime).atOffset(vnOffset);

                    // Kiểm tra khung giờ dịch vụ đã kết thúc trong quá khứ hay chưa
                    if (slotEnd.isBefore(now)) {
                        throw new InvalidCheckinStateException(
                                String.format("The service slot ended at %s (current time: %s). A QR code cannot be generated.",
                                        slotEnd, now));
                    }
                    return slotEnd;
                }
            }
        }

        // Fallback: 24h sau khi tạo đơn hoặc 24h kể từ hiện tại
        return subOrder.getCreatedAt() != null
                ? subOrder.getCreatedAt().plusDays(1)
                : now.plusDays(1);
    }

    private boolean checkIsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}

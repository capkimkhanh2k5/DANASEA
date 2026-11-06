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

        // 1. Phân giải đa tầng: SubOrder trước (tương thích ngược Mockito tests), sau đó MasterOrder
        Optional<SubOrderJpaEntity> subOpt = subOrderRepository.findById(identifier);
        if (subOpt.isPresent()) {
            return generateForSubOrder(subOpt.get(), customerId);
        }

        Optional<MasterOrderJpaEntity> masterOpt = masterOrderRepository.findById(identifier);
        if (masterOpt.isPresent()) {
            return generateForMasterOrder(masterOpt.get(), customerId);
        }

        throw new OrderNotFoundException("Order or service item not found: " + identifier);
    }

    private GenerateQrResponse generateForMasterOrder(MasterOrderJpaEntity masterOrder, UUID customerId) {
        boolean isAdmin = checkIsAdmin();
        if (!isAdmin && (masterOrder.getCustomerId() == null || !Objects.equals(masterOrder.getCustomerId(), customerId))) {
            log.warn("IDOR_ATTEMPT: User {} attempted to retrieve a QR code owned by user {}", customerId, masterOrder.getCustomerId());
            throw new UnauthorizedCheckinAccessException("User does not have permission to view or generate QR for this booking");
        }

        List<SubOrderJpaEntity> subOrders = subOrderRepository.findByMasterOrderId(masterOrder.getId());
        if (subOrders.isEmpty()) {
            throw new InvalidSubOrderStateException("Order contains no activities");
        }

        List<SubOrderJpaEntity> activeSubOrders = subOrders.stream()
                .filter(s -> s.getStatus() == SubOrderStatus.CONFIRMED || s.getStatus() == SubOrderStatus.CHECKED_IN)
                .toList();

        if (activeSubOrders.isEmpty()) {
            throw new InvalidSubOrderStateException("No confirmed activities in this order ready for check-in");
        }

        OffsetDateTime maxExpiresAt = activeSubOrders.stream()
                .map(this::calculateExpirationAndValidateSlot)
                .max(OffsetDateTime::compareTo)
                .orElse(OffsetDateTime.now().plusDays(1));

        String rawToken = qrTokenSigner.generateToken(masterOrder.getId(), maxExpiresAt);
        String tokenHash = QrTokenSigner.hashToken(rawToken);

        Optional<CheckinTokenJpaEntity> existingOpt = checkinTokenRepository
                .findFirstBySubOrderIdAndUsedAtIsNullOrderByCreatedAtDesc(activeSubOrders.get(0).getId());
        CheckinTokenJpaEntity tokenEntity = existingOpt.orElseGet(() -> CheckinTokenJpaEntity.builder()
                .subOrderId(activeSubOrders.get(0).getId())
                .usedAt(null)
                .usedByVendorStaffId(null)
                .build());

        tokenEntity.setQrTokenHash(tokenHash);
        tokenEntity.setExpiresAt(maxExpiresAt);
        checkinTokenRepository.save(tokenEntity);

        log.info("Generated Master QR code for order: {}, expiresAt: {}", masterOrder.getId(), maxExpiresAt);
        return new GenerateQrResponse(masterOrder.getId(), rawToken, maxExpiresAt);
    }

    private GenerateQrResponse generateForSubOrder(SubOrderJpaEntity subOrder, UUID customerId) {
        MasterOrderJpaEntity masterOrder = masterOrderRepository.findById(subOrder.getMasterOrderId())
                .orElseThrow(() -> new OrderNotFoundException("Master order not found with id: " + subOrder.getMasterOrderId()));

        boolean isAdmin = checkIsAdmin();
        if (!isAdmin && (masterOrder.getCustomerId() == null || !Objects.equals(masterOrder.getCustomerId(), customerId))) {
            log.warn("IDOR_ATTEMPT: User {} attempted to retrieve a QR code owned by user {}", customerId, masterOrder.getCustomerId());
            throw new UnauthorizedCheckinAccessException("User does not have permission to view or generate QR for this booking");
        }

        if (subOrder.getStatus() == SubOrderStatus.PENDING) {
            throw new InvalidSubOrderStateException("Cannot generate a QR code for a pending, unpaid sub-order");
        }
        if (subOrder.getStatus() == SubOrderStatus.CANCELLED || subOrder.getStatus() == SubOrderStatus.REFUNDED) {
            throw new InvalidSubOrderStateException("Cannot generate a QR code for a cancelled or refunded sub-order in status: " + subOrder.getStatus());
        }
        if (subOrder.getStatus() != SubOrderStatus.CONFIRMED) {
            throw new InvalidSubOrderStateException("Only confirmed sub-orders can generate QR codes. Current status: " + subOrder.getStatus());
        }

        OffsetDateTime expiresAt = calculateExpirationAndValidateSlot(subOrder);
        String rawToken = qrTokenSigner.generateToken(subOrder.getId(), expiresAt);
        String tokenHash = QrTokenSigner.hashToken(rawToken);

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

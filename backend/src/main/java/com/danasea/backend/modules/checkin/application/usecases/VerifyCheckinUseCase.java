package com.danasea.backend.modules.checkin.application.usecases;

import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.checkin.domain.exceptions.CheckinTokenNotFoundException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidQrSignatureException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidQrTokenException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidSubOrderStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.QrTokenAlreadyUsedException;
import com.danasea.backend.modules.checkin.domain.exceptions.QrTokenExpiredException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedVendorCheckinException;
import com.danasea.backend.modules.checkin.domain.models.QrTokenPayload;
import com.danasea.backend.modules.checkin.domain.services.QrTokenSigner;
import com.danasea.backend.modules.checkin.domain.services.CheckinWindowPolicy;
import com.danasea.backend.modules.checkin.infrastructure.persistence.entities.CheckinTokenJpaEntity;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinRequest;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinResponse;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class VerifyCheckinUseCase {

    private final JpaCheckinTokenRepository checkinTokenRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final VendorLookupPort vendorLookupPort;
    private final QrTokenSigner qrTokenSigner;
    private final JpaServiceSlotRepository slotRepository;
    private final CheckinWindowPolicy checkinWindowPolicy;
    private final JpaMasterOrderRepository masterOrderRepository;

    @Autowired
    public VerifyCheckinUseCase(
            JpaCheckinTokenRepository checkinTokenRepository,
            JpaSubOrderRepository subOrderRepository,
            VendorLookupPort vendorLookupPort,
            QrTokenSigner qrTokenSigner,
            JpaServiceSlotRepository slotRepository,
            CheckinWindowPolicy checkinWindowPolicy,
            @Autowired(required = false) JpaMasterOrderRepository masterOrderRepository) {
        this.checkinTokenRepository = checkinTokenRepository;
        this.subOrderRepository = subOrderRepository;
        this.vendorLookupPort = vendorLookupPort;
        this.qrTokenSigner = qrTokenSigner;
        this.slotRepository = slotRepository;
        this.checkinWindowPolicy = checkinWindowPolicy;
        this.masterOrderRepository = masterOrderRepository;
    }

    public VerifyCheckinUseCase(
            JpaCheckinTokenRepository checkinTokenRepository,
            JpaSubOrderRepository subOrderRepository,
            VendorLookupPort vendorLookupPort,
            QrTokenSigner qrTokenSigner,
            JpaServiceSlotRepository slotRepository,
            CheckinWindowPolicy checkinWindowPolicy) {
        this(checkinTokenRepository, subOrderRepository, vendorLookupPort, qrTokenSigner, slotRepository, checkinWindowPolicy, null);
    }

    public VerifyCheckinUseCase(
            JpaCheckinTokenRepository checkinTokenRepository,
            JpaSubOrderRepository subOrderRepository,
            VendorLookupPort vendorLookupPort,
            QrTokenSigner qrTokenSigner) {
        this(checkinTokenRepository, subOrderRepository, vendorLookupPort, qrTokenSigner, null, null, null);
    }

    @Transactional
    public VerifyCheckinResponse execute(VerifyCheckinRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedVendorCheckinException("User is not authenticated"));
        return execute(request, currentUserId);
    }

    @Transactional
    public VerifyCheckinResponse execute(VerifyCheckinRequest request, UUID staffId) {
        // 1. Kiểm tra đầu vào (Null/Blank check)
        if (request == null || request.qrToken() == null || request.qrToken().isBlank()) {
            throw new InvalidQrTokenException("QR token must not be blank");
        }

        String rawToken = request.qrToken().trim();

        // 2. Kiểm tra định dạng cú pháp (Malformed check)
        int dotIdx = rawToken.lastIndexOf('.');
        if (dotIdx <= 0 || dotIdx >= rawToken.length() - 1) {
            throw new InvalidQrTokenException("Malformed QR token structure");
        }

        String payloadStr = rawToken.substring(0, dotIdx);
        if (!payloadStr.contains(":")) {
            throw new InvalidQrTokenException("Malformed QR token structure: missing colon separator");
        }

        // 3. Xác thực chữ ký HMAC-SHA256 bằng thời gian hằng số (Constant-Time Verification)
        if (!qrTokenSigner.verifySignature(rawToken)) {
            throw new InvalidQrSignatureException("Invalid QR signature or tampered ticket data");
        }

        // 4. Giải mã Payload
        QrTokenPayload payload = qrTokenSigner.parsePayload(rawToken);

        // 5. Kiểm tra thời hạn vé (Expiration check)
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isAfter(payload.getExpiresAt())) {
            throw new QrTokenExpiredException(
                    String.format("The QR ticket expired at %s (scan time: %s)", payload.getExpiresAt(), now));
        }

        // 6. Tính Hash và tra cứu CheckinToken trong DB
        String tokenHash = QrTokenSigner.hashToken(rawToken);
        CheckinTokenJpaEntity tokenEntity = checkinTokenRepository.findByQrTokenHash(tokenHash)
                .orElseThrow(() -> new CheckinTokenNotFoundException("Check-in ticket not found"));

        // 7. Kiểm tra trạng thái đã sử dụng trước đó (Fast check)
        if (tokenEntity.getUsedAt() != null) {
            throw new QrTokenAlreadyUsedException(
                    String.format("The ticket was used at %s by staff member %s",
                            tokenEntity.getUsedAt(), tokenEntity.getUsedByVendorStaffId()),
                    tokenEntity.getUsedAt(),
                    tokenEntity.getUsedByVendorStaffId()
            );
        }

        // 8. Tải thông tin SubOrder hoặc bóc tách từ MasterOrder
        UUID identifier = payload.getSubOrderId();
        SubOrderJpaEntity subOrder = null;
        boolean isMasterOrderScan = false;
        List<SubOrderJpaEntity> allMasterSubOrders = null;

        Optional<SubOrderJpaEntity> subOpt = subOrderRepository.findByIdForUpdate(identifier);
        if (subOpt.isPresent()) {
            subOrder = subOpt.get();
        } else if (masterOrderRepository != null) {
            Optional<MasterOrderJpaEntity> masterOpt = masterOrderRepository.findById(identifier);
            if (masterOpt.isPresent()) {
                isMasterOrderScan = true;
                UUID staffVendorId = resolveStaffVendorId(staffId);
                boolean isAdmin = checkIsAdmin();

                allMasterSubOrders = subOrderRepository.findByMasterOrderIdForUpdate(identifier);

                // Lọc các hoạt động thuộc vendor của nhân viên đang quét
                List<SubOrderJpaEntity> vendorSubOrders = isAdmin ? allMasterSubOrders : allMasterSubOrders.stream()
                        .filter(s -> Objects.equals(s.getVendorId(), staffVendorId))
                        .toList();

                if (vendorSubOrders.isEmpty()) {
                    log.warn("VENDOR_ISOLATION_VIOLATION: Staff {} (vendor {}) attempted to scan master order {} with no items for vendor",
                            staffId, staffVendorId, identifier);
                    throw new UnauthorizedVendorCheckinException(
                            String.format("Check-in denied: This order does not contain any activities for vendor %s", staffVendorId));
                }

                // Tìm hoạt động CONFIRMED của vendor này
                Optional<SubOrderJpaEntity> readySubOrder = vendorSubOrders.stream()
                        .filter(s -> s.getStatus() == SubOrderStatus.CONFIRMED)
                        .findFirst();

                if (readySubOrder.isEmpty()) {
                    boolean alreadyDone = vendorSubOrders.stream()
                            .anyMatch(s -> s.getStatus() == SubOrderStatus.CHECKED_IN || s.getStatus() == SubOrderStatus.COMPLETED);
                    if (alreadyDone) {
                        throw new QrTokenAlreadyUsedException(
                                "Hoạt động của nhà tàu bạn trong đơn này đã được quét check-in trước đó.",
                                now, staffId);
                    }
                    throw new InvalidSubOrderStateException("Hoạt động của nhà tàu trong đơn này không ở trạng thái sẵn sàng check-in.");
                }

                subOrder = readySubOrder.get();
            }
        }

        if (subOrder == null) {
            throw new OrderNotFoundException("Sub-order not found: " + identifier);
        }

        if (subOrder.getStatus() == SubOrderStatus.CANCELLED || subOrder.getStatus() == SubOrderStatus.REFUNDED) {
            throw new InvalidSubOrderStateException("Cancelled or refunded orders cannot be checked in");
        }
        if (subOrder.getStatus() == SubOrderStatus.PENDING) {
            throw new InvalidSubOrderStateException("The order has not been paid and cannot be checked in");
        }

        if (slotRepository != null && checkinWindowPolicy != null && subOrder.getSlotId() != null) {
            var slot = slotRepository.findByIdForUpdate(subOrder.getSlotId())
                    .orElseThrow(() -> new InvalidCheckinStateException("Service slot not found for this order."));
            checkinWindowPolicy.validate(slot, now);
            if (subOrder.getBookingItemId() != null
                    && slotRepository.hasBlockedEquipmentForBookingItem(subOrder.getBookingItemId())) {
                throw new InvalidCheckinStateException("The assigned equipment is unavailable.");
            }
        }

        // 9. Kiểm soát cô lập đa Vendor (Multi-tenant Vendor Isolation)
        if (!isMasterOrderScan) {
            boolean isAdmin = checkIsAdmin();
            if (!isAdmin) {
                UUID staffVendorId = resolveStaffVendorId(staffId);
                if (!Objects.equals(subOrder.getVendorId(), staffVendorId)) {
                    log.warn("VENDOR_ISOLATION_VIOLATION: Staff {} (vendor {}) attempted to check in order {} owned by vendor {}",
                            staffId, staffVendorId, subOrder.getId(), subOrder.getVendorId());
                    throw new UnauthorizedVendorCheckinException(
                            String.format("Check-in denied: the order belongs to vendor %s, but the account belongs to vendor %s",
                                    subOrder.getVendorId(), staffVendorId));
                }
            }
        }

        // 10. Cập nhật điều kiện nguyên tử (Atomic Conditional Update)
        if (!isMasterOrderScan) {
            int rowsUpdated = 0;
            if (tokenEntity.getId() != null) {
                rowsUpdated = checkinTokenRepository.markAsUsedAtomic(tokenEntity.getId(), now, staffId);
            } else {
                rowsUpdated = checkinTokenRepository.markAsUsedIfUnused(tokenHash, staffId, now, now);
            }

            if (rowsUpdated == 0) {
                CheckinTokenJpaEntity used = checkinTokenRepository.findByQrTokenHash(tokenHash).orElse(tokenEntity);
                throw new QrTokenAlreadyUsedException(
                        String.format("The ticket was used at %s by staff member %s",
                                used.getUsedAt(), used.getUsedByVendorStaffId()),
                        used.getUsedAt(),
                        used.getUsedByVendorStaffId()
                );
            }
        } else {
            // Khi quét Master QR: Nếu tất cả các sub_orders khác trong đơn đã được check-in/hoàn tất thì khoá token
            UUID checkedInSubOrderId = subOrder.getId();
            boolean anyRemaining = allMasterSubOrders != null && allMasterSubOrders.stream()
                    .anyMatch(s -> !s.getId().equals(checkedInSubOrderId) && s.getStatus() == SubOrderStatus.CONFIRMED);
            if (!anyRemaining) {
                tokenEntity.setUsedAt(now);
                tokenEntity.setUsedByVendorStaffId(staffId);
                checkinTokenRepository.save(tokenEntity);
            }
        }

        // 11. Cập nhật trạng thái SubOrder sang CHECKED_IN
        subOrder.setStatus(SubOrderStatus.CHECKED_IN);
        subOrder.setCheckedInAt(now);
        subOrderRepository.save(subOrder);

        log.info("CHECKIN_SUCCESS: Sub-order {} checked in by staff member {}", subOrder.getId(), staffId);

        return VerifyCheckinResponse.builder()
                .subOrderId(subOrder.getId())
                .vendorId(subOrder.getVendorId())
                .serviceId(subOrder.getServiceId())
                .slotId(subOrder.getSlotId())
                .quantity(subOrder.getQuantity())
                .status(SubOrderStatus.CHECKED_IN)
                .checkedInAt(now)
                .verifiedByStaffId(staffId)
                .message("Check-in verified successfully")
                .build();
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

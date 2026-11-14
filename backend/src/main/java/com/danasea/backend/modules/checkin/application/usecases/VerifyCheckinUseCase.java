package com.danasea.backend.modules.checkin.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.booking.domain.ports.VendorLookupPort;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemAllocationRepository;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.checkin.domain.exceptions.AmbiguousCheckinTargetException;
import com.danasea.backend.modules.checkin.domain.exceptions.CheckinTokenNotFoundException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidCheckinStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidQrSignatureException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidQrTokenException;
import com.danasea.backend.modules.checkin.domain.exceptions.InvalidSubOrderStateException;
import com.danasea.backend.modules.checkin.domain.exceptions.QrTokenAlreadyUsedException;
import com.danasea.backend.modules.checkin.domain.exceptions.QrTokenExpiredException;
import com.danasea.backend.modules.checkin.domain.exceptions.UnauthorizedVendorCheckinException;
import com.danasea.backend.modules.checkin.domain.models.QrTokenPayload;
import com.danasea.backend.modules.checkin.domain.services.CheckinWindowPolicy;
import com.danasea.backend.modules.checkin.domain.services.FifoEligibilityEvaluator;
import com.danasea.backend.modules.checkin.domain.services.QrTokenSigner;
import com.danasea.backend.modules.checkin.infrastructure.persistence.entities.CheckinTokenJpaEntity;
import com.danasea.backend.modules.checkin.infrastructure.persistence.repositories.JpaCheckinTokenRepository;
import com.danasea.backend.modules.checkin.presentation.dtos.EligibleSubOrderCandidate;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinRequest;
import com.danasea.backend.modules.checkin.presentation.dtos.VerifyCheckinResponse;
import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.communication.domain.ports.PushNotificationPort;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.OperationType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotUnitRepository;
import com.danasea.backend.modules.vendor.infrastructure.persistence.repositories.JpaVendorRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import com.danasea.backend.shared.i18n.LocalizedMessageRef;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final TransactionalEmailPort transactionalEmailPort;

    @Autowired(required = false)
    private JpaServiceRepository serviceRepository;

    @Autowired(required = false)
    private JpaVendorRepository vendorRepository;

    @Autowired(required = false)
    private AccountInternalApi accountInternalApi;

    @Autowired(required = false)
    private SendNotificationUseCase sendNotificationUseCase;

    @Autowired(required = false)
    private PushNotificationPort pushNotificationPort;

    @Autowired(required = false)
    private JpaSubOrderDisruptionRepository disruptionRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private JpaBookingItemRepository bookingItemRepository;

    @Autowired(required = false)
    private JpaServiceOptionRepository serviceOptionRepository;

    @Autowired(required = false)
    private JpaServiceSlotUnitRepository slotUnitRepository;

    @Autowired(required = false)
    private JpaBookingItemAllocationRepository bookingItemAllocationRepository;

    @Autowired(required = false)
    private FifoEligibilityEvaluator fifoEligibilityEvaluator = new FifoEligibilityEvaluator();

    @Autowired
    public VerifyCheckinUseCase(
            JpaCheckinTokenRepository checkinTokenRepository,
            JpaSubOrderRepository subOrderRepository,
            VendorLookupPort vendorLookupPort,
            QrTokenSigner qrTokenSigner,
            JpaServiceSlotRepository slotRepository,
            CheckinWindowPolicy checkinWindowPolicy,
            @Autowired(required = false) JpaMasterOrderRepository masterOrderRepository,
            @Autowired(required = false) TransactionalEmailPort transactionalEmailPort) {
        this.checkinTokenRepository = checkinTokenRepository;
        this.subOrderRepository = subOrderRepository;
        this.vendorLookupPort = vendorLookupPort;
        this.qrTokenSigner = qrTokenSigner;
        this.slotRepository = slotRepository;
        this.checkinWindowPolicy = checkinWindowPolicy;
        this.masterOrderRepository = masterOrderRepository;
        this.transactionalEmailPort = transactionalEmailPort;
    }

    public VerifyCheckinUseCase(
            JpaCheckinTokenRepository checkinTokenRepository,
            JpaSubOrderRepository subOrderRepository,
            VendorLookupPort vendorLookupPort,
            QrTokenSigner qrTokenSigner,
            JpaServiceSlotRepository slotRepository,
            CheckinWindowPolicy checkinWindowPolicy,
            JpaMasterOrderRepository masterOrderRepository) {
        this(checkinTokenRepository, subOrderRepository, vendorLookupPort, qrTokenSigner, slotRepository,
                checkinWindowPolicy, masterOrderRepository, null);
    }

    public VerifyCheckinUseCase(
            JpaCheckinTokenRepository checkinTokenRepository,
            JpaSubOrderRepository subOrderRepository,
            VendorLookupPort vendorLookupPort,
            QrTokenSigner qrTokenSigner,
            JpaServiceSlotRepository slotRepository,
            CheckinWindowPolicy checkinWindowPolicy) {
        this(checkinTokenRepository, subOrderRepository, vendorLookupPort, qrTokenSigner, slotRepository,
                checkinWindowPolicy, null, null);
    }

    public VerifyCheckinUseCase(
            JpaCheckinTokenRepository checkinTokenRepository,
            JpaSubOrderRepository subOrderRepository,
            VendorLookupPort vendorLookupPort,
            QrTokenSigner qrTokenSigner) {
        this(checkinTokenRepository, subOrderRepository, vendorLookupPort, qrTokenSigner, null, null, null, null);
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
                    tokenEntity.getUsedByVendorStaffId());
        }

        // 8. Tải thông tin SubOrder hoặc bóc tách từ MasterOrder
        UUID identifier = payload.getSubOrderId();
        SubOrderJpaEntity subOrder = null;
        boolean isMasterOrderScan = false;
        List<SubOrderJpaEntity> allMasterSubOrders = null;

        Optional<SubOrderJpaEntity> subOpt = subOrderRepository.findByIdForUpdate(identifier);
        if (subOpt.isPresent()) {
            subOrder = subOpt.get();
            if (request.selectedSubOrderId() != null && !Objects.equals(request.selectedSubOrderId(), subOrder.getId())) {
                throw new InvalidCheckinStateException("Mã QR không khớp với dịch vụ đã chọn.");
            }
        } else if (masterOrderRepository != null) {
            Optional<MasterOrderJpaEntity> masterOpt = masterOrderRepository.findById(identifier);
            if (masterOpt.isPresent()) {
                isMasterOrderScan = true;
                UUID staffVendorId = resolveStaffVendorId(staffId);
                boolean isAdmin = checkIsAdmin();

                allMasterSubOrders = subOrderRepository.findByMasterOrderIdForUpdate(identifier);

                // Lọc các hoạt động thuộc vendor của nhân viên đang quét
                List<SubOrderJpaEntity> vendorSubOrders = isAdmin ? allMasterSubOrders
                        : allMasterSubOrders.stream()
                                .filter(s -> Objects.equals(s.getVendorId(), staffVendorId))
                                .toList();

                if (vendorSubOrders.isEmpty()) {
                    log.warn(
                            "VENDOR_ISOLATION_VIOLATION: Staff {} (vendor {}) attempted to scan master order {} with no items for vendor",
                            staffId, staffVendorId, identifier);
                    throw new UnauthorizedVendorCheckinException(
                            String.format("Check-in denied: This order does not contain any activities for vendor %s",
                                    staffVendorId));
                }

                // Lọc danh sách các hoạt động CONFIRMED của vendor này
                List<SubOrderJpaEntity> confirmedSubOrders = vendorSubOrders.stream()
                        .filter(s -> s.getStatus() == SubOrderStatus.CONFIRMED)
                        .toList();

                if (confirmedSubOrders.isEmpty()) {
                    boolean alreadyDone = vendorSubOrders.stream()
                            .anyMatch(s -> s.getStatus() == SubOrderStatus.CHECKED_IN
                                    || s.getStatus() == SubOrderStatus.COMPLETED);
                    if (alreadyDone) {
                        throw new QrTokenAlreadyUsedException(
                                "Hoạt động của nhà cung cấp trong đơn này đã được quét check-in trước đó.",
                                now, staffId);
                    }
                    throw new InvalidSubOrderStateException(
                            "Hoạt động của nhà cung cấp trong đơn này không ở trạng thái sẵn sàng check-in.");
                }

                if (request.selectedSubOrderId() != null) {
                    UUID targetId = request.selectedSubOrderId();
                    SubOrderJpaEntity targetSubOrder = allMasterSubOrders.stream()
                            .filter(s -> Objects.equals(s.getId(), targetId))
                            .findFirst()
                            .orElseThrow(() -> new OrderNotFoundException("Hoạt động đã chọn không thuộc đơn hàng này: " + targetId));

                    if (!isAdmin && !Objects.equals(targetSubOrder.getVendorId(), staffVendorId)) {
                        log.warn("VENDOR_ISOLATION_VIOLATION: Staff {} (vendor {}) attempted to select sub-order {} owned by vendor {}",
                                staffId, staffVendorId, targetSubOrder.getId(), targetSubOrder.getVendorId());
                        throw new UnauthorizedVendorCheckinException("Hoạt động đã chọn thuộc về nhà cung cấp khác.");
                    }

                    if (targetSubOrder.getStatus() == SubOrderStatus.CHECKED_IN || targetSubOrder.getStatus() == SubOrderStatus.COMPLETED) {
                        throw new QrTokenAlreadyUsedException(
                                "Hoạt động đã chọn đã được check-in trước đó.", now, staffId);
                    }
                    if (targetSubOrder.getStatus() != SubOrderStatus.CONFIRMED) {
                        throw new InvalidSubOrderStateException("Hoạt động đã chọn không ở trạng thái sẵn sàng check-in.");
                    }

                    subOrder = targetSubOrder;
                } else {
                    // ISSUE-QR-01: Chỉ xét các SubOrder thực sự hợp lệ để check-in tại thời điểm quét
                    List<SubOrderJpaEntity> eligibleSubOrders = new ArrayList<>();
                    for (SubOrderJpaEntity cand : confirmedSubOrders) {
                        if (isSubOrderEligibleForCheckin(cand, now, request.isAllowLateOverride())) {
                            eligibleSubOrders.add(cand);
                        }
                    }

                    if (eligibleSubOrders.size() == 1) {
                        subOrder = eligibleSubOrders.get(0);
                    } else if (eligibleSubOrders.size() > 1) {
                        log.warn("AMBIGUOUS_MASTER_QR: Master order {} has {} eligible sub-orders for vendor {}",
                                identifier, eligibleSubOrders.size(), staffVendorId);
                        List<EligibleSubOrderCandidate> candidates = new ArrayList<>();
                        for (SubOrderJpaEntity s : eligibleSubOrders) {
                            String sName = "Dịch vụ Danasea";
                            if (serviceRepository != null && s.getServiceId() != null) {
                                sName = serviceRepository.findById(s.getServiceId())
                                        .map(com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity::getName)
                                        .orElse(sName);
                            }
                            LocalDate slotDate = null;
                            LocalTime startTime = null;
                            LocalTime endTime = null;
                            if (slotRepository != null && s.getSlotId() != null) {
                                var slOpt = slotRepository.findById(s.getSlotId());
                                if (slOpt.isPresent()) {
                                    slotDate = slOpt.get().getDate();
                                    startTime = slOpt.get().getStartTime();
                                    endTime = slOpt.get().getEndTime();
                                }
                            }
                            candidates.add(new EligibleSubOrderCandidate(
                                    s.getId(),
                                    s.getServiceId(),
                                    sName,
                                    s.getSlotId(),
                                    slotDate,
                                    startTime,
                                    endTime,
                                    s.getQuantity() != null ? s.getQuantity() : 1));
                        }
                        throw new AmbiguousCheckinTargetException(
                                String.format("Đơn hàng chứa %d hoạt động của nhà cung cấp đang trong khung giờ check-in hợp lệ. Vui lòng chọn hoạt động cần check-in.",
                                        eligibleSubOrders.size()),
                                identifier,
                                candidates);
                    } else {
                        // Không có sub-order nào đang trong khung giờ check-in hợp lệ
                        if (confirmedSubOrders.size() == 1) {
                            // Nếu chỉ có đúng 1 sub-order CONFIRMED, chọn nó để tiếp tục chuyển xuống tầng validation chi tiết
                            // (phát sinh thông báo quá giờ / chưa mở check-in và gửi notification nếu quá hạn)
                            subOrder = confirmedSubOrders.get(0);
                        } else {
                            throw new InvalidCheckinStateException(
                                    "Không có hoạt động nào của nhà cung cấp đang trong khung giờ check-in hợp lệ.");
                        }
                    }
                }
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
        if (disruptionRepository != null && subOrder.getId() != null
                && disruptionRepository.findBySubOrderIdAndStatus(subOrder.getId(), "AWAITING_DECISION").isPresent()) {
            throw new InvalidCheckinStateException("The service is currently disrupted and awaiting operational decision.");
        }

        boolean isLate = false;
        String lateNote = null;
        ServiceSlotJpaEntity currentSlot = null;

        if (slotRepository != null && subOrder.getSlotId() != null) {
            var slotOpt = slotRepository.findByIdForUpdate(subOrder.getSlotId());
            if (slotOpt.isPresent()) {
                currentSlot = slotOpt.get();
            } else if (checkinWindowPolicy != null) {
                throw new InvalidCheckinStateException("Service slot not found for this order.");
            }

            if (currentSlot != null && checkinWindowPolicy != null) {
                OperationType opType = OperationType.FIXED_DEPARTURE;
                Integer durationMinutes = null;
                String serviceName = "Dịch vụ Danasea";

                if (serviceRepository != null && subOrder.getServiceId() != null) {
                    var sOpt = serviceRepository.findById(subOrder.getServiceId());
                    if (sOpt.isPresent()) {
                        var s = sOpt.get();
                        if (s.getOperationType() != null)
                            opType = s.getOperationType();
                        durationMinutes = s.getDurationMinutes();
                        if (s.getName() != null)
                            serviceName = s.getName();
                    }
                }

                OptionType optionType = OptionType.SHARED;
                if (bookingItemRepository != null && subOrder.getBookingItemId() != null) {
                    var biOpt = bookingItemRepository.findById(subOrder.getBookingItemId());
                    if (biOpt.isPresent()) {
                        var bi = biOpt.get();
                        if (bi.getOptionId() != null && serviceOptionRepository != null) {
                            var optEntity = serviceOptionRepository.findById(bi.getOptionId());
                            if (optEntity.isPresent() && optEntity.get().getOptionType() != null) {
                                optionType = optEntity.get().getOptionType();
                            }
                        }
                    }
                }

                var validationResult = checkinWindowPolicy.validate(
                        currentSlot,
                        opType,
                        optionType,
                        durationMinutes,
                        now,
                        request.isAllowLateOverride());
                isLate = validationResult.isLate();
                lateNote = validationResult.note();
            }

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
                    log.warn(
                            "VENDOR_ISOLATION_VIOLATION: Staff {} (vendor {}) attempted to check in order {} owned by vendor {}",
                            staffId, staffVendorId, subOrder.getId(), subOrder.getVendorId());
                    throw new UnauthorizedVendorCheckinException(
                            String.format(
                                    "Check-in denied: the order belongs to vendor %s, but the account belongs to vendor %s",
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
                        used.getUsedByVendorStaffId());
            }
        } else {
            // Khi quét Master QR: Nếu tất cả các sub_orders khác trong đơn đã được
            // check-in/hoàn tất thì khoá token
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
        subOrder.setIsLate(isLate);
        subOrder.setLateCheckinReason(request.overrideReason() != null ? request.overrideReason() : lateNote);
        subOrderRepository.save(subOrder);

        // 12. Gửi email giao dịch mời đánh giá sau 24h
        enqueueReviewInvitationEmail(subOrder, now);

        // 13. Tính toán gợi ý FIFO đi ca sớm nếu có ca sớm hơn còn chỗ (CHỈ ÁP DỤNG CHO SHARED/PUBLIC)
        VerifyCheckinResponse.EarlyDispatchSuggestion earlyDispatch = null;
        List<VerifyCheckinResponse.EarlyDispatchSuggestion> earlyCandidates = new ArrayList<>();
        if (currentSlot != null && slotRepository != null && subOrder.getServiceId() != null) {
            try {
                var evalContext = FifoEligibilityEvaluator.FifoEvaluationContext.of(
                        redisTemplate,
                        bookingItemAllocationRepository,
                        slotUnitRepository,
                        bookingItemRepository,
                        serviceOptionRepository,
                        serviceRepository);

                // BẤT BIẾN NGHIỆP VỤ: PRIVATE không tham gia FIFO
                boolean isPrivate = fifoEligibilityEvaluator.isPrivateSubOrder(subOrder, evalContext);
                if (!isPrivate) {
                    var candidateSlots = slotRepository.findByServiceIdAndDateAndStatus(
                            subOrder.getServiceId(), currentSlot.getDate(), SlotStatus.OPEN);

                    for (ServiceSlotJpaEntity s : candidateSlots) {
                        var evalResult = fifoEligibilityEvaluator.evaluate(
                                subOrder, currentSlot, s, currentSlot, now, evalContext);
                        if (evalResult.isEligible()) {
                            earlyCandidates.add(new VerifyCheckinResponse.EarlyDispatchSuggestion(
                                    true, s.getId(), s.getDate(), s.getStartTime(), s.getEndTime(), evalResult.availableCapacity()));
                        }
                    }
                    if (!earlyCandidates.isEmpty()) {
                        earlyCandidates.sort(Comparator.comparing(VerifyCheckinResponse.EarlyDispatchSuggestion::startTime));
                        earlyDispatch = earlyCandidates.get(0);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to calculate early dispatch suggestion for subOrder {}", subOrder.getId(), e);
            }
        }

        log.info("CHECKIN_SUCCESS: Sub-order {} checked in by staff member {} (isLate: {})",
                subOrder.getId(), staffId, isLate);

        return VerifyCheckinResponse.builder()
                .subOrderId(subOrder.getId())
                .vendorId(subOrder.getVendorId())
                .serviceId(subOrder.getServiceId())
                .slotId(subOrder.getSlotId())
                .quantity(subOrder.getQuantity())
                .status(SubOrderStatus.CHECKED_IN)
                .checkedInAt(now)
                .verifiedByStaffId(staffId)
                .message("Check-in verified successfully" + (isLate ? " (LATE EXCEPTION APPROVED)" : ""))
                .isLate(isLate)
                .lateReason(subOrder.getLateCheckinReason())
                .earlyDispatchSuggestion(earlyDispatch)
                .availableEarlySlots(earlyCandidates.isEmpty() ? null : earlyCandidates)
                .build();
    }

    private boolean isSubOrderEligibleForCheckin(SubOrderJpaEntity cand, OffsetDateTime now, boolean allowLateOverride) {
        if (cand == null || cand.getStatus() != SubOrderStatus.CONFIRMED || cand.getSlotId() == null) {
            return false;
        }
        if (slotRepository == null) {
            return true;
        }
        var slotOpt = slotRepository.findById(cand.getSlotId());
        if (slotOpt.isEmpty()) {
            return false;
        }
        ServiceSlotJpaEntity slot = slotOpt.get();
        if (slot.getStatus() == SlotStatus.CANCELLED) {
            return false;
        }
        if (cand.getBookingItemId() != null && slotRepository.hasBlockedEquipmentForBookingItem(cand.getBookingItemId())) {
            return false;
        }
        if (disruptionRepository != null && cand.getId() != null
                && disruptionRepository.findBySubOrderIdAndStatus(cand.getId(), "AWAITING_DECISION").isPresent()) {
            return false;
        }
        if (checkinWindowPolicy == null) {
            return true;
        }

        OperationType opType = OperationType.FIXED_DEPARTURE;
        Integer durationMinutes = null;
        if (serviceRepository != null && cand.getServiceId() != null) {
            var sOpt = serviceRepository.findById(cand.getServiceId());
            if (sOpt.isPresent()) {
                if (sOpt.get().getOperationType() != null) {
                    opType = sOpt.get().getOperationType();
                }
                durationMinutes = sOpt.get().getDurationMinutes();
            }
        }

        OptionType optType = OptionType.SHARED;
        if (bookingItemRepository != null && cand.getBookingItemId() != null) {
            var biOpt = bookingItemRepository.findById(cand.getBookingItemId());
            if (biOpt.isPresent() && biOpt.get().getOptionId() != null && serviceOptionRepository != null) {
                var optEntity = serviceOptionRepository.findById(biOpt.get().getOptionId());
                if (optEntity.isPresent() && optEntity.get().getOptionType() != null) {
                    optType = optEntity.get().getOptionType();
                }
            }
        }

        try {
            checkinWindowPolicy.validate(slot, opType, optType, durationMinutes, now, allowLateOverride);
            return true;
        } catch (InvalidCheckinStateException ex) {
            return false;
        }
    }

    private int calculateRedisHolds(UUID slotId, long nowMillis) {
        if (redisTemplate == null || slotId == null) {
            return 0;
        }
        try {
            Map<Object, Object> rawHolds = redisTemplate.opsForHash().entries("inventory:slot:" + slotId + ":holds");
            if (rawHolds == null || rawHolds.isEmpty()) {
                return 0;
            }
            int held = 0;
            for (Object valObj : rawHolds.values()) {
                String val = String.valueOf(valObj);
                int barPos = val.indexOf('|');
                if (barPos > 0) {
                    long exp = Long.parseLong(val.substring(0, barPos));
                    if (exp <= nowMillis) continue;
                    String details = val.substring(barPos + 1);
                    if (details.startsWith("PERSON_LIMIT:")) {
                        held += Integer.parseInt(details.substring("PERSON_LIMIT:".length()));
                    } else if (details.startsWith("SHARED_CAPACITY_UNITS|")) {
                        String[] parts = details.split("\\|");
                        if (parts.length >= 3) {
                            held += Integer.parseInt(parts[2]);
                        }
                    } else if (details.startsWith("UNITS:")) {
                        String unitsStr = details.substring("UNITS:".length());
                        for (String allocStr : unitsStr.split(";")) {
                            if (!allocStr.isBlank()) {
                                String[] parts = allocStr.split(":");
                                if (parts.length >= 2) {
                                    held += Integer.parseInt(parts[1]);
                                }
                            }
                        }
                    }
                } else {
                    int colonPos = val.indexOf(':');
                    if (colonPos > 0) {
                        int qty = Integer.parseInt(val.substring(0, colonPos));
                        long exp = Long.parseLong(val.substring(colonPos + 1));
                        if (exp > nowMillis) held += qty;
                    }
                }
            }
            return held;
        } catch (Exception e) {
            log.warn("Failed to check Redis holds for slot {}", slotId, e);
            return 0;
        }
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

    private void enqueueReviewInvitationEmail(SubOrderJpaEntity subOrder, OffsetDateTime checkedInAt) {
        if (transactionalEmailPort == null || masterOrderRepository == null) {
            return;
        }
        try {
            masterOrderRepository.findById(subOrder.getMasterOrderId()).ifPresent(masterOrder -> {
                if (masterOrder.getCustomerId() == null) {
                    return;
                }
                String businessKey = "review:suborder:" + subOrder.getId() + ":invite";
                String recipientKey = "USER:" + masterOrder.getCustomerId();
                OffsetDateTime dueAt = checkedInAt.plusHours(24);
                OffsetDateTime expiresAt = checkedInAt.plusDays(14);

                Map<String, Object> payload = Map.of(
                        "subOrderId", subOrder.getId().toString(),
                        "masterOrderId", masterOrder.getId().toString(),
                        "serviceId", subOrder.getServiceId().toString(),
                        "vendorId", subOrder.getVendorId().toString(),
                        "checkedInAt", checkedInAt.toString());

                transactionalEmailPort.enqueue(
                        EmailRequest.builder()
                                .type(TransactionalEmailType.TRIP_REVIEW_INVITATION)
                                .businessKey(businessKey)
                                .recipientKey(recipientKey)
                                .occurredAt(checkedInAt)
                                .dueAt(dueAt)
                                .expiresAt(expiresAt)
                                .payload(payload)
                                .build());
            });
        } catch (Exception e) {
            log.warn("Failed to enqueue trip review invitation email for sub-order {}", subOrder.getId(), e);
        }
    }
}

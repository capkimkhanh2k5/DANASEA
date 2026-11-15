package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.modules.account.application.api.AccountInternalApi;
import com.danasea.backend.modules.account.domain.models.User;
import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.booking.infrastructure.persistence.repositories.JpaBookingItemRepository;
import com.danasea.backend.modules.communication.application.dtos.NotificationCommand;
import com.danasea.backend.modules.communication.application.usecases.SendNotificationUseCase;
import com.danasea.backend.modules.communication.domain.models.NotificationChannel;
import com.danasea.backend.modules.communication.domain.ports.PushNotificationPort;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.domain.models.MasterOrderStatus;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.domain.models.OperationType;
import com.danasea.backend.modules.service.domain.models.OptionType;
import com.danasea.backend.modules.service.domain.models.SlotStatus;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceOptionRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.shared.i18n.LocalizedMessageRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoCompleteSubOrdersUseCase {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JpaSubOrderRepository subOrderRepository;
    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaServiceSlotRepository slotRepository;
    private final JpaDisputeRepository disputeRepository;
    private final JpaRefundRepository refundRepository;
    private final JpaSubOrderDisruptionRepository disruptionRepository;

    @Autowired(required = false)
    private AuditLogInternalApi auditLogInternalApi;

    @Autowired(required = false)
    private SendNotificationUseCase sendNotificationUseCase;

    @Autowired(required = false)
    private PushNotificationPort pushNotificationPort;

    @Autowired(required = false)
    private VendorInternalApi vendorInternalApi;

    @Autowired(required = false)
    private AccountInternalApi accountInternalApi;

    @Autowired(required = false)
    private JpaServiceRepository serviceRepository;

    @Autowired(required = false)
    private JpaBookingItemRepository bookingItemRepository;

    @Autowired(required = false)
    private JpaServiceOptionRepository serviceOptionRepository;

    public List<UUID> findCandidateIds() {
        return subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNotNull(SubOrderStatus.CHECKED_IN);
    }

    public List<UUID> findNoShowCandidateIds() {
        return subOrderRepository.findCandidateIdsByStatusAndCheckedInAtNull(SubOrderStatus.CONFIRMED);
    }

    public int executeAll() {
        return executeAll(OffsetDateTime.now(VIETNAM_ZONE));
    }

    public int executeAll(OffsetDateTime now) {
        int completedCount = 0;
        List<UUID> candidateIds = findCandidateIds();
        if (candidateIds != null && !candidateIds.isEmpty()) {
            for (UUID subOrderId : candidateIds) {
                try {
                    boolean completed = completeSingleSubOrder(subOrderId, now);
                    if (completed) {
                        completedCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to auto-complete sub-order {}", subOrderId, e);
                }
            }
        }

        // Tự động gửi thông báo quá hạn check-in cho các đơn CONFIRMED quá hạn chót (start - 15m)
        try {
            int overdueNotified = notifyOverdueCheckins(now);
            if (overdueNotified > 0) {
                log.info("OVERDUE_CHECKIN: Sent overdue notification to {} customer(s)", overdueNotified);
            }
        } catch (Exception e) {
            log.error("Failed to execute notifyOverdueCheckins", e);
        }

        // Tự động gửi nhắc nhở cho các đơn PRIVATE đến trễ nhưng ca vẫn còn (start < now < end)
        try {
            int remindedCount = notifyPrivateLateReminders(now);
            if (remindedCount > 0) {
                log.info("PRIVATE_LATE_REMINDER: Sent late reminder to {} customer(s)", remindedCount);
            }
        } catch (Exception e) {
            log.error("Failed to execute notifyPrivateLateReminders", e);
        }

        // Tự động xử lý No-show cho các đơn CONFIRMED không check-in đã quá giờ
        try {
            int noShowCount = markNoShows(now);
            if (noShowCount > 0) {
                log.info("AUTO_NO_SHOW: Marked {} un-checked-in sub-orders as NO_SHOW", noShowCount);
            }
        } catch (Exception e) {
            log.error("Failed to execute markNoShows", e);
        }

        return completedCount;
    }

    @Transactional
    public boolean completeSingleSubOrder(UUID subOrderId, OffsetDateTime now) {
        var subOrderOpt = subOrderRepository.findByIdForUpdate(subOrderId);
        if (subOrderOpt.isEmpty()) {
            return false;
        }

        SubOrderJpaEntity subOrder = subOrderOpt.get();

        // 1. Kiểm tra trạng thái và điều kiện check-in
        if (subOrder.getStatus() != SubOrderStatus.CHECKED_IN || subOrder.getCheckedInAt() == null) {
            return false;
        }

        // Căn cứ vào slot đã đăng ký gốc (slotId)
        UUID registeredSlotId = subOrder.getSlotId();
        if (registeredSlotId == null) {
            log.warn("SubOrder {} has null slotId, skipping auto-completion", subOrderId);
            return false;
        }

        // 2. Tra cứu Slot gốc để xác định mốc kết thúc ca đã đăng ký
        var slotOpt = slotRepository.findById(registeredSlotId);
        if (slotOpt.isEmpty()) {
            log.warn("Service slot {} not found for SubOrder {}", registeredSlotId, subOrderId);
            return false;
        }

        ServiceSlotJpaEntity slot = slotOpt.get();
        if (slot.getDate() == null || slot.getStartTime() == null || slot.getEndTime() == null) {
            log.warn("Service slot {} has incomplete timing data for SubOrder {}", slot.getId(), subOrderId);
            return false;
        }

        // 3. Tính toán chính xác thời điểm kết thúc ca theo Asia/Ho_Chi_Minh (hỗ trợ ca
        // qua đêm)
        LocalDate slotDate = slot.getDate();
        LocalTime startTime = slot.getStartTime();
        LocalTime endTime = slot.getEndTime();
        LocalDate endDate = endTime.isBefore(startTime) ? slotDate.plusDays(1) : slotDate;
        OffsetDateTime slotEndDateTime = LocalDateTime.of(endDate, endTime).atZone(VIETNAM_ZONE).toOffsetDateTime();

        if (now.isBefore(slotEndDateTime)) {
            // Chưa đến giờ kết thúc ca đăng ký
            return false;
        }

        // 3.1. Late Check-in Guard: Không hoàn thành nếu thời điểm quét check-in bị
        // quét sau khi ca đã kết thúc
        if (subOrder.getCheckedInAt().isAfter(slotEndDateTime)) {
            log.warn(
                    "SubOrder {} checkedInAt ({}) is after slotEndDateTime ({})! Suspicious post-closure scan, skipping auto-completion.",
                    subOrderId, subOrder.getCheckedInAt(), slotEndDateTime);
            return false;
        }

        // 4. Kiểm tra Dispute đang mở hoặc đang xem xét
        boolean hasActiveDispute = disputeRepository.existsBySubOrderIdAndStatusIn(
                subOrderId,
                List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW));
        if (hasActiveDispute) {
            log.info("SubOrder {} has active dispute, skipping auto-completion", subOrderId);
            return false;
        }

        // 5. Kiểm tra Refund toàn diện (Pending & Processed Full Refund)
        boolean hasPendingRefund = refundRepository.existsBySubOrderIdAndStatusIn(
                subOrderId,
                List.of(RefundStatus.PENDING));
        if (hasPendingRefund) {
            log.info("SubOrder {} has pending refund, skipping auto-completion", subOrderId);
            return false;
        }

        List<RefundJpaEntity> subOrderRefunds = refundRepository.findBySubOrderId(subOrderId);
        if (subOrderRefunds != null && !subOrderRefunds.isEmpty()) {
            BigDecimal totalProcessedRefund = subOrderRefunds.stream()
                    .filter(r -> r.getStatus() == RefundStatus.PROCESSED)
                    .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal finalAmount = subOrder.getFinalAmount() != null ? subOrder.getFinalAmount() : BigDecimal.ZERO;
            if (finalAmount.compareTo(BigDecimal.ZERO) > 0 && totalProcessedRefund.compareTo(finalAmount) >= 0) {
                log.info(
                        "SubOrder {} has 100% processed refund ({}), skipping auto-completion and ensuring REFUNDED status",
                        subOrderId, totalProcessedRefund);
                if (subOrder.getStatus() != SubOrderStatus.REFUNDED) {
                    subOrder.setStatus(SubOrderStatus.REFUNDED);
                    subOrderRepository.save(subOrder);
                }
                return false;
            }
        }

        // 6. Kiểm tra Disruption đang chờ quyết định
        boolean hasActiveDisruption = disruptionRepository.findBySubOrderIdAndStatus(subOrderId, "AWAITING_DECISION")
                .isPresent();
        if (hasActiveDisruption) {
            log.info("SubOrder {} has active disruption awaiting decision, skipping auto-completion", subOrderId);
            return false;
        }

        // 7. Cập nhật trạng thái SubOrder sang COMPLETED
        subOrder.setStatus(SubOrderStatus.COMPLETED);
        subOrderRepository.save(subOrder);

        // 8. Cập nhật MasterOrder
        updateMasterOrderStatus(subOrder.getMasterOrderId());

        // 9. Ghi Audit Log
        if (auditLogInternalApi != null) {
            try {
                auditLogInternalApi.recordTransactionalAuditLog(
                        null,
                        "AUTO_COMPLETE_SUB_ORDER",
                        "SUB_ORDER",
                        subOrder.getId(),
                        String.format("{\"slotId\":\"%s\",\"slotEnd\":\"%s\",\"completedAt\":\"%s\"}",
                                slot.getId(), slotEndDateTime, now));
            } catch (Exception e) {
                log.warn("Failed to record audit log for auto-complete sub-order {}", subOrderId, e);
            }
        }

        log.info("AUTO_COMPLETE_SUCCESS: SubOrder {} successfully completed at {}", subOrderId, now);
        return true;
    }

    @Transactional
    public int markNoShows(OffsetDateTime now) {
        List<UUID> noShowCandidateIds = findNoShowCandidateIds();
        if (noShowCandidateIds == null || noShowCandidateIds.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (UUID subOrderId : noShowCandidateIds) {
            try {
                var subOrderOpt = subOrderRepository.findByIdForUpdate(subOrderId);
                if (subOrderOpt.isEmpty()) {
                    continue;
                }
                SubOrderJpaEntity subOrder = subOrderOpt.get();
                if (subOrder.getStatus() != SubOrderStatus.CONFIRMED || subOrder.getCheckedInAt() != null) {
                    continue;
                }
                if (subOrder.getSlotId() == null) {
                    continue;
                }

                // Nếu đơn con đã có lý do hủy (hủy do vendor, thời tiết, sự cố) -> Tuyệt đối không đánh dấu NO_SHOW
                if (subOrder.getCancellationReason() != null) {
                    continue;
                }

                var slotOpt = slotRepository.findById(subOrder.getSlotId());
                if (slotOpt.isEmpty()) {
                    continue;
                }
                ServiceSlotJpaEntity slot = slotOpt.get();
                if (slot.getDate() == null || slot.getStartTime() == null || slot.getEndTime() == null) {
                    continue;
                }

                LocalDate slotDate = slot.getDate();
                LocalTime startTime = slot.getStartTime();
                LocalTime endTime = slot.getEndTime();
                LocalDate endDate = endTime.isBefore(startTime) ? slotDate.plusDays(1) : slotDate;
                OffsetDateTime slotEndDateTime = LocalDateTime.of(endDate, endTime).atZone(VIETNAM_ZONE)
                        .toOffsetDateTime();

                if (now.isBefore(slotEndDateTime)) {
                    // Ca chưa kết thúc, chưa xác định NO_SHOW
                    continue;
                }

                if (slot.getStatus() == com.danasea.backend.modules.service.domain.models.SlotStatus.CANCELLED) {
                    // Ca bị hủy bởi nhà cung cấp/hệ thống (thời tiết xấu, sự cố) -> Tuyệt đối không đánh dấu NO_SHOW
                    continue;
                }

                // Không đánh dấu NO_SHOW nếu đơn đã có yêu cầu hoàn tiền hoặc hoàn tiền đã xử lý
                if (refundRepository.existsBySubOrderId(subOrderId)) {
                    continue;
                }

                // Kiểm tra không có Dispute hay Disruption đang chờ
                boolean hasActiveDispute = disputeRepository.existsBySubOrderIdAndStatusIn(
                        subOrderId, List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW));
                boolean hasActiveDisruption = disruptionRepository
                        .findBySubOrderIdAndStatus(subOrderId, "AWAITING_DECISION").isPresent();
                if (hasActiveDispute || hasActiveDisruption) {
                    continue;
                }

                subOrder.setStatus(SubOrderStatus.NO_SHOW);
                subOrderRepository.save(subOrder);
                updateMasterOrderStatus(subOrder.getMasterOrderId());

                if (auditLogInternalApi != null) {
                    auditLogInternalApi.recordTransactionalAuditLog(
                            null,
                            "MARK_SUB_ORDER_NO_SHOW",
                            "SUB_ORDER",
                            subOrderId,
                            String.format(
                                    "{\"slotId\":\"%s\",\"reason\":\"Customer did not check in\",\"markedAt\":\"%s\"}",
                                    slot.getId(), now));
                }
                count++;
                log.info("NO_SHOW_MARKED: SubOrder {} marked as NO_SHOW (slotEnd: {})", subOrderId, slotEndDateTime);
            } catch (Exception e) {
                log.error("Failed to mark NO_SHOW for sub-order {}", subOrderId, e);
            }
        }
        return count;
    }

    @Transactional
    public int notifyOverdueCheckins(OffsetDateTime now) {
        if (sendNotificationUseCase == null) {
            return 0;
        }

        List<UUID> candidateIds = findNoShowCandidateIds();
        if (candidateIds == null || candidateIds.isEmpty()) {
            return 0;
        }

        int notifiedCount = 0;
        for (UUID subOrderId : candidateIds) {
            try {
                var subOrderOpt = subOrderRepository.findById(subOrderId);
                if (subOrderOpt.isEmpty()) {
                    continue;
                }
                SubOrderJpaEntity subOrder = subOrderOpt.get();
                if (subOrder.getStatus() != SubOrderStatus.CONFIRMED || subOrder.getCheckedInAt() != null) {
                    continue;
                }
                if (subOrder.getSlotId() == null) {
                    continue;
                }

                var slotOpt = slotRepository.findById(subOrder.getSlotId());
                if (slotOpt.isEmpty()) {
                    continue;
                }
                ServiceSlotJpaEntity slot = slotOpt.get();
                if (slot.getStatus() == SlotStatus.CANCELLED) {
                    continue;
                }
                if (slot.getDate() == null || slot.getStartTime() == null || slot.getEndTime() == null) {
                    continue;
                }

                LocalDate slotDate = slot.getDate();
                LocalTime startTime = slot.getStartTime();
                LocalTime endTime = slot.getEndTime();
                LocalDate endDate = endTime.isBefore(startTime) ? slotDate.plusDays(1) : slotDate;
                OffsetDateTime startDateTime = LocalDateTime.of(slotDate, startTime).atZone(VIETNAM_ZONE).toOffsetDateTime();
                OffsetDateTime endDateTime = LocalDateTime.of(endDate, endTime).atZone(VIETNAM_ZONE).toOffsetDateTime();

                // Xác định OperationType của dịch vụ
                OperationType opType = OperationType.FIXED_DEPARTURE;
                String serviceName = "Dịch vụ";
                Integer durationMinutes = null;
                if (serviceRepository != null && subOrder.getServiceId() != null) {
                    var serviceOpt = serviceRepository.findById(subOrder.getServiceId());
                    if (serviceOpt.isPresent()) {
                        ServiceJpaEntity serviceEntity = serviceOpt.get();
                        if (serviceEntity.getOperationType() != null) {
                            opType = serviceEntity.getOperationType();
                        }
                        if (serviceEntity.getName() != null && !serviceEntity.getName().isBlank()) {
                            serviceName = serviceEntity.getName();
                        }
                        durationMinutes = serviceEntity.getDurationMinutes();
                    }
                }

                // Xác định OptionType (SHARED hay PRIVATE)
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

                // Hạn chót check-in theo quyết định PO:
                // OVERDUE_CHECKIN CHỈ áp dụng cho SHARED (cả FIXED_DEPARTURE lẫn DURATION_BASED).
                // PRIVATE đã có PRIVATE_LATE_REMINDER nhắc nhở trong ca, và khi hết ca thì chuyển NO_SHOW (không gửi quá hạn thừa).
                if (optionType != OptionType.SHARED) {
                    continue;
                }
                OffsetDateTime absoluteClosesAt = startDateTime.minusMinutes(15);

                // Chỉ gửi nếu đã vượt quá hạn check-in cứng và trong khoảng hợp lý (không quá 2 giờ sau khi ca kết thúc)
                if (now.isBefore(absoluteClosesAt) || now.isAfter(endDateTime.plusHours(2))) {
                    continue;
                }

                // Không gửi nếu đơn đã có yêu cầu hoàn tiền hoặc có tranh chấp/sự cố
                if (refundRepository.existsBySubOrderId(subOrderId)
                        || disputeRepository.existsBySubOrderIdAndStatusIn(subOrderId, List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW))
                        || disruptionRepository.findBySubOrderIdAndStatus(subOrderId, "AWAITING_DECISION").isPresent()) {
                    continue;
                }

                // Lấy thông tin khách hàng từ MasterOrder
                var masterOrderOpt = masterOrderRepository.findById(subOrder.getMasterOrderId());
                if (masterOrderOpt.isEmpty() || masterOrderOpt.get().getCustomerId() == null) {
                    continue;
                }
                UUID customerId = masterOrderOpt.get().getCustomerId();

                // Lấy thông tin liên hệ chính thức của nhà cung cấp (không dùng hotline giả)
                String vendorName = "Nhà cung cấp";
                String vendorContact = "mục Tin nhắn / Hỗ trợ trong đơn hàng";
                if (vendorInternalApi != null && subOrder.getVendorId() != null) {
                    var vendorOpt = vendorInternalApi.findById(subOrder.getVendorId());
                    if (vendorOpt.isPresent()) {
                        var v = vendorOpt.get();
                        if (v.getBusinessName() != null && !v.getBusinessName().isBlank()) {
                            vendorName = v.getBusinessName();
                        }
                        if (accountInternalApi != null && v.getUserId() != null) {
                            var userOpt = accountInternalApi.findUserById(v.getUserId());
                            if (userOpt.isPresent()) {
                                var u = userOpt.get();
                                if (u.getPhone() != null && !u.getPhone().isBlank()) {
                                    vendorContact = "SĐT: " + u.getPhone();
                                } else if (u.getEmail() != null && !u.getEmail().isBlank()) {
                                    vendorContact = "Email: " + u.getEmail();
                                }
                            }
                        }
                    }
                }

                String tripTimeStr = String.format("%s %s", slot.getStartTime(), slot.getDate());
                String idempotencyKey = "overdue_checkin:" + subOrder.getId();

                NotificationCommand command = new NotificationCommand(
                        customerId,
                        "OVERDUE_CHECKIN",
                        NotificationChannel.IN_APP,
                        LocalizedMessageRef.of("notification.checkin.overdue.title"),
                        LocalizedMessageRef.of("notification.checkin.overdue.body", serviceName, tripTimeStr, vendorName, vendorContact),
                        "SUB_ORDER",
                        subOrder.getId(),
                        null
                );

                var notificationOpt = sendNotificationUseCase.executeOnce(command, idempotencyKey);
                if (notificationOpt.isPresent()) {
                    notifiedCount++;
                    var notification = notificationOpt.get();
                    if (pushNotificationPort != null) {
                        try {
                            pushNotificationPort.sendPush(
                                    notification.getUserId(),
                                    notification.getTitle(),
                                    notification.getBody(),
                                    java.util.Map.of("type", "OVERDUE_CHECKIN", "subOrderId", subOrderId.toString(), "vendorContact", vendorContact)
                            );
                        } catch (Exception pushEx) {
                            log.warn("Failed to deliver push for overdue checkin notification {}", notification.getId(), pushEx);
                        }
                    }

                    if (auditLogInternalApi != null) {
                        auditLogInternalApi.recordTransactionalAuditLog(
                                null,
                                "NOTIFY_OVERDUE_CHECKIN",
                                "SUB_ORDER",
                                subOrderId,
                                String.format("{\"vendorContact\":\"%s\",\"serviceName\":\"%s\",\"cutoff\":\"%s\"}",
                                        vendorContact, serviceName, absoluteClosesAt));
                    }
                    log.info("NOTIFY_OVERDUE_SUCCESS: Sent overdue notification to customer {} for subOrder {}", customerId, subOrderId);
                }
            } catch (Exception e) {
                log.error("Failed to process overdue checkin notification for subOrder {}", subOrderId, e);
            }
        }
        return notifiedCount;
    }

    @Transactional
    public int notifyPrivateLateReminders(OffsetDateTime now) {
        if (sendNotificationUseCase == null) {
            return 0;
        }

        List<UUID> candidateIds = findNoShowCandidateIds();
        if (candidateIds == null || candidateIds.isEmpty()) {
            return 0;
        }

        int remindedCount = 0;
        for (UUID subOrderId : candidateIds) {
            try {
                var subOrderOpt = subOrderRepository.findById(subOrderId);
                if (subOrderOpt.isEmpty()) {
                    continue;
                }
                SubOrderJpaEntity subOrder = subOrderOpt.get();
                if (subOrder.getStatus() != SubOrderStatus.CONFIRMED || subOrder.getCheckedInAt() != null) {
                    continue;
                }
                if (subOrder.getSlotId() == null) {
                    continue;
                }

                // Xác định OptionType (chỉ áp dụng cho PRIVATE)
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
                if (optionType != OptionType.PRIVATE) {
                    continue;
                }

                var slotOpt = slotRepository.findById(subOrder.getSlotId());
                if (slotOpt.isEmpty()) {
                    continue;
                }
                ServiceSlotJpaEntity slot = slotOpt.get();
                if (slot.getStatus() == SlotStatus.CANCELLED) {
                    continue;
                }
                if (slot.getDate() == null || slot.getStartTime() == null || slot.getEndTime() == null) {
                    continue;
                }

                LocalDate slotDate = slot.getDate();
                LocalTime startTime = slot.getStartTime();
                LocalTime endTime = slot.getEndTime();
                LocalDate endDate = endTime.isBefore(startTime) ? slotDate.plusDays(1) : slotDate;
                OffsetDateTime startDateTime = LocalDateTime.of(slotDate, startTime).atZone(VIETNAM_ZONE).toOffsetDateTime();
                OffsetDateTime endDateTime = LocalDateTime.of(endDate, endTime).atZone(VIETNAM_ZONE).toOffsetDateTime();

                // Chỉ gửi khi đã qua giờ khởi hành startDateTime và ca vẫn còn thời gian (now < endDateTime)
                if (now.isBefore(startDateTime) || !now.isBefore(endDateTime)) {
                    continue;
                }

                // Không gửi nếu đơn đã có yêu cầu hoàn tiền hoặc có tranh chấp/sự cố
                if (refundRepository.existsBySubOrderId(subOrderId)
                        || disputeRepository.existsBySubOrderIdAndStatusIn(subOrderId, List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW))
                        || disruptionRepository.findBySubOrderIdAndStatus(subOrderId, "AWAITING_DECISION").isPresent()) {
                    continue;
                }

                var masterOrderOpt = masterOrderRepository.findById(subOrder.getMasterOrderId());
                if (masterOrderOpt.isEmpty() || masterOrderOpt.get().getCustomerId() == null) {
                    continue;
                }
                UUID customerId = masterOrderOpt.get().getCustomerId();

                String serviceName = "Dịch vụ thuê riêng";
                if (serviceRepository != null && subOrder.getServiceId() != null) {
                    var sOpt = serviceRepository.findById(subOrder.getServiceId());
                    if (sOpt.isPresent() && sOpt.get().getName() != null && !sOpt.get().getName().isBlank()) {
                        serviceName = sOpt.get().getName();
                    }
                }

                String vendorName = "Nhà cung cấp";
                String vendorContact = "mục Tin nhắn / Hỗ trợ trong đơn hàng";
                if (vendorInternalApi != null && subOrder.getVendorId() != null) {
                    var vendorOpt = vendorInternalApi.findById(subOrder.getVendorId());
                    if (vendorOpt.isPresent()) {
                        var v = vendorOpt.get();
                        if (v.getBusinessName() != null && !v.getBusinessName().isBlank()) {
                            vendorName = v.getBusinessName();
                        }
                        if (accountInternalApi != null && v.getUserId() != null) {
                            var userOpt = accountInternalApi.findUserById(v.getUserId());
                            if (userOpt.isPresent()) {
                                var u = userOpt.get();
                                if (u.getPhone() != null && !u.getPhone().isBlank()) {
                                    vendorContact = "SĐT: " + u.getPhone();
                                } else if (u.getEmail() != null && !u.getEmail().isBlank()) {
                                    vendorContact = "Email: " + u.getEmail();
                                }
                            }
                        }
                    }
                }

                String tripTimeStr = String.format("%s %s", slot.getStartTime(), slot.getDate());
                String idempotencyKey = "private_late_reminder:" + subOrder.getId();

                NotificationCommand command = new NotificationCommand(
                        customerId,
                        "PRIVATE_LATE_REMINDER",
                        NotificationChannel.IN_APP,
                        LocalizedMessageRef.of("notification.checkin.private_late_reminder.title"),
                        LocalizedMessageRef.of("notification.checkin.private_late_reminder.body", serviceName, tripTimeStr, vendorName, vendorContact),
                        "SUB_ORDER",
                        subOrder.getId(),
                        null
                );

                var notificationOpt = sendNotificationUseCase.executeOnce(command, idempotencyKey);
                if (notificationOpt.isPresent()) {
                    remindedCount++;
                    var notification = notificationOpt.get();
                    if (pushNotificationPort != null) {
                        try {
                            pushNotificationPort.sendPush(
                                    notification.getUserId(),
                                    notification.getTitle(),
                                    notification.getBody(),
                                    java.util.Map.of("type", "PRIVATE_LATE_REMINDER", "subOrderId", subOrderId.toString(), "vendorContact", vendorContact)
                            );
                        } catch (Exception pushEx) {
                            log.warn("Failed to deliver push for private late reminder notification {}", notification.getId(), pushEx);
                        }
                    }

                    if (auditLogInternalApi != null) {
                        auditLogInternalApi.recordTransactionalAuditLog(
                                null,
                                "NOTIFY_PRIVATE_LATE_REMINDER",
                                "SUB_ORDER",
                                subOrderId,
                                String.format("{\"vendorContact\":\"%s\",\"serviceName\":\"%s\",\"slotEnd\":\"%s\"}",
                                        vendorContact, serviceName, endDateTime));
                    }
                    log.info("NOTIFY_PRIVATE_LATE_SUCCESS: Sent private late reminder to customer {} for subOrder {}", customerId, subOrderId);
                }
            } catch (Exception e) {
                log.error("Failed to process private late reminder notification for subOrder {}", subOrderId, e);
            }
        }
        return remindedCount;
    }

    void updateMasterOrderStatus(UUID masterOrderId) {
        if (masterOrderId == null) {
            return;
        }

        var masterOrderOpt = masterOrderRepository.findByIdForUpdate(masterOrderId);
        if (masterOrderOpt.isEmpty()) {
            return;
        }

        MasterOrderJpaEntity masterOrder = masterOrderOpt.get();
        if (masterOrder.getStatus() == MasterOrderStatus.CANCELLED) {
            // Không ghi đè trạng thái CANCELLED
            return;
        }

        List<SubOrderJpaEntity> allSubs = subOrderRepository.findByMasterOrderId(masterOrderId);
        if (allSubs.isEmpty()) {
            return;
        }

        long completedCount = allSubs.stream().filter(s -> s.getStatus() == SubOrderStatus.COMPLETED).count();
        long partiallyRefundedCount = allSubs.stream().filter(s -> s.getStatus() == SubOrderStatus.PARTIALLY_REFUNDED).count();
        boolean allTerminal = allSubs.stream().allMatch(s -> s.getStatus() != null && s.getStatus().isTerminal());

        // Phân biệt hoàn tất vòng đời đơn mẹ với việc từng dịch vụ có được cung cấp thành công hay không:
        // 1. Nếu toàn bộ SubOrder đều COMPLETED -> MasterOrder: COMPLETED (tất cả dịch vụ đã diễn ra trọn vẹn)
        // 2. Nếu có dịch vụ COMPLETED hoặc PARTIALLY_REFUNDED nhưng có SubOrder khác NO_SHOW / REFUNDED / CANCELLED:
        //    -> MasterOrder: PARTIALLY_COMPLETED (chỉ một phần dịch vụ trong đơn được cung cấp thành công)
        // 3. Nếu KHÔNG CÓ dịch vụ nào được cung cấp (tất cả SubOrder đều NO_SHOW, hoặc NO_SHOW + REFUNDED, hoặc CANCELLED):
        //    -> MasterOrder: CANCELLED (vòng đời đơn kết thúc mà không có dịch vụ nào được trải nghiệm thành công)
        if (allTerminal) {
            if (completedCount == allSubs.size()) {
                masterOrder.setStatus(MasterOrderStatus.COMPLETED);
            } else if (completedCount > 0 || partiallyRefundedCount > 0) {
                masterOrder.setStatus(MasterOrderStatus.PARTIALLY_COMPLETED);
            } else {
                masterOrder.setStatus(MasterOrderStatus.CANCELLED);
            }
        } else if (completedCount > 0 || partiallyRefundedCount > 0) {
            masterOrder.setStatus(MasterOrderStatus.PARTIALLY_COMPLETED);
        }

        masterOrderRepository.save(masterOrder);
        log.info("MasterOrder {} status updated to {}", masterOrderId, masterOrder.getStatus());
    }
}

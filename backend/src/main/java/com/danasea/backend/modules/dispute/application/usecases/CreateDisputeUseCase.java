package com.danasea.backend.modules.dispute.application.usecases;

import com.danasea.backend.modules.audit.application.api.AuditLogInternalApi;
import com.danasea.backend.modules.dispute.domain.exceptions.DisputePeriodExpiredException;
import com.danasea.backend.modules.dispute.domain.exceptions.DuplicateDisputeException;
import com.danasea.backend.modules.dispute.domain.exceptions.InvalidSubOrderStateException;
import com.danasea.backend.modules.dispute.domain.exceptions.UnauthorizedDisputeAccessException;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.dispute.presentation.dtos.CreateDisputeRequest;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.MasterOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaMasterOrderRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateDisputeUseCase {

    private final JpaDisputeRepository disputeRepository;
    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaServiceSlotRepository slotRepository;

    @Autowired(required = false)
    private AuditLogInternalApi auditLogInternalApi;

    @Transactional
    public DisputeResponse execute(UUID orderId, CreateDisputeRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedDisputeAccessException("User is not authenticated"));
        return execute(orderId, request, currentUserId);
    }

    @Transactional
    public DisputeResponse execute(UUID orderId, CreateDisputeRequest request, UUID customerId) {
        log.info("Creating dispute for orderId: {}, subOrderId: {} by customer: {}",
                orderId, request.subOrderId(), customerId);

        // 1. Kiểm tra tồn tại MasterOrder
        MasterOrderJpaEntity masterOrder = masterOrderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        // 2. Kiểm tra quyền sở hữu (chỉ chính chủ khách hàng tạo đơn mới được mở khiếu
        // nại)
        if (masterOrder.getCustomerId() == null || !masterOrder.getCustomerId().equals(customerId)) {
            throw new UnauthorizedDisputeAccessException("User does not have permission to dispute this order");
        }

        // 3. Kiểm tra tồn tại SubOrder (sử dụng findByIdForUpdate để chống race
        // condition với Auto-Completion)
        SubOrderJpaEntity subOrder = subOrderRepository.findByIdForUpdate(request.subOrderId())
                .or(() -> subOrderRepository.findById(request.subOrderId()))
                .orElseThrow(() -> new OrderNotFoundException("Sub-order not found with id: " + request.subOrderId()));

        // 4. Kiểm tra tính trực thuộc của SubOrder với MasterOrder
        if (subOrder.getMasterOrderId() == null || !subOrder.getMasterOrderId().equals(orderId)) {
            throw new OrderNotFoundException(
                    "Sub-order " + request.subOrderId() + " does not belong to order " + orderId);
        }

        // 5. Kiểm tra trạng thái SubOrder (cho phép COMPLETED, CONFIRMED, CHECKED_IN
        // hoặc NO_SHOW)
        if (subOrder.getStatus() != SubOrderStatus.COMPLETED
                && subOrder.getStatus() != SubOrderStatus.CONFIRMED
                && subOrder.getStatus() != SubOrderStatus.CHECKED_IN
                && subOrder.getStatus() != SubOrderStatus.NO_SHOW) {
            throw new InvalidSubOrderStateException(
                    "Disputes can only be opened for COMPLETED or CONFIRMED bookings. Current status: "
                            + subOrder.getStatus());
        }

        // 6. Kiểm tra giới hạn thời gian 7 ngày kể từ ngày trải nghiệm
        LocalDate experienceDate = null;
        LocalDateTime experienceStart = null;
        if (subOrder.getSlotId() != null) {
            var slotOpt = slotRepository.findById(subOrder.getSlotId());
            if (slotOpt.isPresent()) {
                ServiceSlotJpaEntity slot = slotOpt.get();
                experienceDate = slot.getDate();
                if (slot.getDate() != null && slot.getStartTime() != null) {
                    experienceStart = LocalDateTime.of(slot.getDate(), slot.getStartTime());
                }
            }
        }
        if (experienceDate == null) {
            experienceDate = subOrder.getCreatedAt() != null ? subOrder.getCreatedAt().toLocalDate() : LocalDate.now();
        }

        // Đối với đơn chưa check-in và không phải NO_SHOW, chặn mở khiếu nại trước giờ
        // khởi hành
        // Đối với đơn ĐÃ CHECK-IN (khách đã có mặt tại bến) hoặc NO_SHOW, cho phép
        // khiếu nại
        if (subOrder.getStatus() != SubOrderStatus.CHECKED_IN && subOrder.getStatus() != SubOrderStatus.NO_SHOW) {
            if (experienceStart != null && experienceStart.isAfter(LocalDateTime.now())) {
                throw new InvalidSubOrderStateException(
                        "A dispute cannot be opened before the scheduled experience has started.");
            }
            if (experienceStart == null && experienceDate.isAfter(LocalDate.now())) {
                throw new InvalidSubOrderStateException(
                        "A dispute cannot be opened before the scheduled experience date.");
            }
        }

        if (LocalDate.now().isAfter(experienceDate.plusDays(7))) {
            throw new DisputePeriodExpiredException(
                    "Dispute must be filed within 7 days of the experience date (" + experienceDate + ")");
        }

        // 7. Chống tạo dispute trùng lặp (OPEN hoặc UNDER_REVIEW)
        boolean hasActiveDispute = disputeRepository.existsBySubOrderIdAndStatusIn(
                request.subOrderId(),
                List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW));
        if (hasActiveDispute) {
            throw new DuplicateDisputeException(
                    "An active dispute already exists for sub-order: " + request.subOrderId());
        }

        // 8. Tạo mới DisputeJpaEntity
        DisputeJpaEntity entity = new DisputeJpaEntity();
        entity.setOrderId(orderId);
        entity.setSubOrderId(request.subOrderId());
        entity.setCustomerId(customerId);
        entity.setReason(request.reason());
        entity.setDescription(request.description());
        entity.setEvidenceUrls(request.evidenceUrls() != null ? request.evidenceUrls() : List.of());
        entity.setStatus(DisputeStatus.OPEN);

        DisputeJpaEntity saved = disputeRepository.save(entity);

        // 9. Ghi Audit Log
        if (auditLogInternalApi != null) {
            try {
                auditLogInternalApi.recordTransactionalAuditLog(
                        customerId,
                        "CREATE_DISPUTE",
                        "DISPUTE",
                        saved.getId(),
                        String.format("{\"orderId\":\"%s\",\"subOrderId\":\"%s\",\"reason\":\"%s\"}",
                                orderId, request.subOrderId(), request.reason()));
            } catch (Exception e) {
                log.warn("Failed to record audit log for create dispute {}", saved.getId(), e);
            }
        }

        log.info("Dispute successfully created with ID: {} for subOrderId: {} by customer: {}",
                saved.getId(), request.subOrderId(), customerId);

        return DisputeResponse.fromEntity(saved);
    }
}

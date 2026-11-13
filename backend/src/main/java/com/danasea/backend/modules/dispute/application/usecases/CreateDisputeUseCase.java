package com.danasea.backend.modules.dispute.application.usecases;

import com.danasea.backend.modules.dispute.application.services.DisputeWindowService;
import com.danasea.backend.modules.dispute.application.services.DisputeWindowService.DisputeWindow;
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
import com.danasea.backend.security.infrastructure.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateDisputeUseCase {

    private final JpaDisputeRepository disputeRepository;
    private final JpaMasterOrderRepository masterOrderRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final DisputeWindowService disputeWindowService;

    @Transactional
    public DisputeResponse execute(UUID orderId, CreateDisputeRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedDisputeAccessException("User is not authenticated"));
        return execute(orderId, request, currentUserId);
    }

    @Transactional
    public DisputeResponse execute(UUID orderId, CreateDisputeRequest request, UUID customerId) {
        // 1. Kiểm tra tồn tại MasterOrder
        MasterOrderJpaEntity masterOrder = masterOrderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        // 2. IDOR / Ownership Check
        if (masterOrder.getCustomerId() == null || !masterOrder.getCustomerId().equals(customerId)) {
            throw new UnauthorizedDisputeAccessException("User does not have permission to dispute this order");
        }

        // 3. Kiểm tra tồn tại SubOrder
        SubOrderJpaEntity subOrder = subOrderRepository.findById(request.subOrderId())
                .orElseThrow(() -> new OrderNotFoundException("Sub-order not found with id: " + request.subOrderId()));

        // 4. Kiểm tra tính trực thuộc của SubOrder với MasterOrder
        if (subOrder.getMasterOrderId() == null || !subOrder.getMasterOrderId().equals(orderId)) {
            throw new OrderNotFoundException("Sub-order " + request.subOrderId() + " does not belong to order " + orderId);
        }

        // 5. Kiểm tra trạng thái SubOrder (chỉ cho phép COMPLETED hoặc CONFIRMED)
        if (subOrder.getStatus() != SubOrderStatus.COMPLETED && subOrder.getStatus() != SubOrderStatus.CONFIRMED) {
            throw new InvalidSubOrderStateException(
                    "Disputes can only be opened for COMPLETED or CONFIRMED bookings. Current status: " + subOrder.getStatus()
            );
        }

        // 6. Kiểm tra giới hạn thời gian 7 ngày kể từ ngày trải nghiệm
        DisputeWindow window = disputeWindowService.resolve(subOrder);
        if (!window.hasStarted()) {
            throw new InvalidSubOrderStateException(window.scheduledStart() != null
                    ? "A dispute cannot be opened before the scheduled experience has started."
                    : "A dispute cannot be opened before the scheduled experience date.");
        }
        if (window.isExpired()) {
            throw new DisputePeriodExpiredException(
                    "Dispute must be filed within 7 days of the experience date (" + window.experienceDate() + ")"
            );
        }

        // 7. Chống tạo dispute trùng lặp (OPEN hoặc UNDER_REVIEW)
        boolean hasActiveDispute = disputeRepository.existsBySubOrderIdAndStatusIn(
                subOrder.getId(),
                List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW)
        );
        if (hasActiveDispute) {
            throw new DuplicateDisputeException(
                    "An active dispute already exists for sub-order: " + subOrder.getId()
            );
        }

        // 8. Tạo và lưu bản ghi Dispute
        DisputeJpaEntity dispute = new DisputeJpaEntity();
        dispute.setOrderId(orderId);
        dispute.setSubOrderId(subOrder.getId());
        dispute.setCustomerId(customerId);
        dispute.setReason(request.reason());
        dispute.setDescription(request.description());
        dispute.setEvidenceUrls(request.evidenceUrls() != null ? request.evidenceUrls() : List.of());
        dispute.setStatus(DisputeStatus.OPEN);

        DisputeJpaEntity saved = disputeRepository.save(dispute);
        log.info("Dispute successfully created with ID: {} for subOrderId: {} by customer: {}",
                saved.getId(), saved.getSubOrderId(), customerId);

        return new DisputeResponse(
                saved.getId(),
                saved.getOrderId(),
                saved.getSubOrderId(),
                saved.getCustomerId(),
                saved.getReason(),
                saved.getDescription(),
                saved.getEvidenceUrls(),
                saved.getStatus(),
                saved.getRefundPercentage(),
                saved.getAdminNote(),
                saved.getResolvedBy(),
                saved.getResolvedAt(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }
}

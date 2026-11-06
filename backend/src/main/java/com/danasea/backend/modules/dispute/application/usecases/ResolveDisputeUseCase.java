package com.danasea.backend.modules.dispute.application.usecases;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.danasea.backend.modules.communication.application.email.EmailRequest;
import com.danasea.backend.modules.communication.application.email.TransactionalEmailPort;
import com.danasea.backend.modules.communication.domain.models.TransactionalEmailType;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;
import com.danasea.backend.modules.vendor.domain.models.Vendor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.dispute.domain.exceptions.DisputeAlreadyResolvedException;
import com.danasea.backend.modules.dispute.domain.exceptions.DisputeNotFoundException;
import com.danasea.backend.modules.dispute.domain.exceptions.InvalidDisputeResolutionException;
import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.dispute.presentation.dtos.DisputeResponse;
import com.danasea.backend.modules.dispute.presentation.dtos.ResolveDisputeRequest;
import com.danasea.backend.modules.order.application.RefundProcessingService;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.security.infrastructure.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;

@Slf4j
@Service
public class ResolveDisputeUseCase {

    private final JpaDisputeRepository disputeRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaRefundRepository refundRepository;
    private final RefundProcessingService refundProcessingService;

    @Autowired(required = false)
    private TransactionalEmailPort transactionalEmailPort;

    @Autowired(required = false)
    private VendorInternalApi vendorInternalApi;

    public void setTransactionalEmailPort(TransactionalEmailPort transactionalEmailPort) {
        this.transactionalEmailPort = transactionalEmailPort;
    }

    public void setVendorInternalApi(VendorInternalApi vendorInternalApi) {
        this.vendorInternalApi = vendorInternalApi;
    }

    @Autowired
    public ResolveDisputeUseCase(
            JpaDisputeRepository disputeRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaRefundRepository refundRepository,
            @Autowired(required = false) RefundProcessingService refundProcessingService) {
        this.disputeRepository = disputeRepository;
        this.subOrderRepository = subOrderRepository;
        this.refundRepository = refundRepository;
        this.refundProcessingService = refundProcessingService;
    }

    public ResolveDisputeUseCase(
            JpaDisputeRepository disputeRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaRefundRepository refundRepository) {
        this(disputeRepository, subOrderRepository, refundRepository, null);
    }

    @Transactional
    public DisputeResponse execute(UUID disputeId, ResolveDisputeRequest request) {
        UUID adminUserId = SecurityUtils.getCurrentUserId().orElse(null);
        return execute(disputeId, request, adminUserId);
    }

    @Transactional
    public DisputeResponse execute(UUID disputeId, ResolveDisputeRequest request, UUID adminUserId) {
        if (request == null || request.resolution() == null) {
            throw new InvalidDisputeResolutionException("A resolution action is required.");
        }
        if (adminUserId == null) {
            throw new AccessDeniedException("Administrator authentication is required.");
        }

        DisputeJpaEntity dispute = disputeRepository.findByIdForUpdate(disputeId)
                .or(() -> disputeRepository.findById(disputeId))
                .orElseThrow(() -> new DisputeNotFoundException("Dispute not found with id: " + disputeId));

        if (dispute.getStatus() != null && dispute.getStatus().isResolved()) {
            throw new DisputeAlreadyResolvedException(
                    "Dispute with id " + disputeId + " is already resolved with status " + dispute.getStatus());
        }

        // 3. Validate Resolution Action
        DisputeStatus resolution = request.resolution();
        if (resolution != DisputeStatus.RESOLVED_REFUND
                && resolution != DisputeStatus.RESOLVED_PARTIAL
                && resolution != DisputeStatus.RESOLVED_REJECTED) {
            throw new InvalidDisputeResolutionException(
                    "Resolution status must be one of: RESOLVED_REFUND, RESOLVED_PARTIAL, RESOLVED_REJECTED");
        }

        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal appliedRefundPercentage = null;
        RefundJpaEntity refund = null;

        // 4. Phân xử tài chính
        if (resolution == DisputeStatus.RESOLVED_REFUND || resolution == DisputeStatus.RESOLVED_PARTIAL) {
            SubOrderJpaEntity subOrder = subOrderRepository.findById(dispute.getSubOrderId())
                    .orElseThrow(() -> new OrderNotFoundException(
                            "Sub-order not found with id: " + dispute.getSubOrderId()));

            appliedRefundPercentage = request.refundPercentage();
            if (resolution == DisputeStatus.RESOLVED_REFUND && appliedRefundPercentage == null) {
                appliedRefundPercentage = BigDecimal.valueOf(100.0);
            }

            if (appliedRefundPercentage == null
                    || appliedRefundPercentage.compareTo(BigDecimal.ZERO) <= 0
                    || appliedRefundPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException(
                        "Refund percentage must be greater than 0 and less than or equal to 100");
            }

            // Refund only the customer-paid snapshot, excluding voucher funding.
            BigDecimal subtotal = subOrder.getFinalAmount() != null ? subOrder.getFinalAmount() : BigDecimal.ZERO;
            BigDecimal refundAmount = subtotal.multiply(appliedRefundPercentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            String idempotencyKey = "dispute-" + disputeId;
            var existingRefund = refundRepository.findBySubOrderIdAndIdempotencyKey(subOrder.getId(), idempotencyKey);
            if (existingRefund.isEmpty() && refundAmount.signum() > 0) {
                RefundJpaEntity newRefund = new RefundJpaEntity();
                newRefund.setSubOrderId(subOrder.getId());
                newRefund.setAmount(refundAmount);
                newRefund.setRefundPercentage(appliedRefundPercentage);
                newRefund.setReason(RefundReason.ADMIN_OVERRIDE);
                newRefund.setStatus(RefundStatus.PENDING);
                newRefund.setRequestedBy(adminUserId);
                newRefund.setIdempotencyKey(idempotencyKey);
                refund = refundRepository.save(newRefund);
                if (transactionalEmailPort != null && refund != null && refund.getId() != null) {
                    transactionalEmailPort.enqueue(new EmailRequest(
                            TransactionalEmailType.REFUND_REQUESTED,
                            "refund:" + refund.getId() + ":requested",
                            "USER:" + dispute.getCustomerId(),
                            now,
                            now,
                            null,
                            1,
                            Map.of(
                                    "refundId", refund.getId().toString(),
                                    "subOrderId", subOrder.getId().toString(),
                                    "amount", refund.getAmount() != null ? refund.getAmount().doubleValue() : 0.0
                            )
                    ));
                }
            } else {
                refund = existingRefund.orElse(null);
            }

            log.info("Dispute {} approved refund amount: {} ({}%) for sub-order {}. Positive cash refunds are queued for provider processing.",
                    disputeId, refundAmount, appliedRefundPercentage, subOrder.getId());
        } else {
            // RESOLVED_REJECTED: Không tạo refund, không đổi SubOrder
            log.info("Dispute {} rejected by admin {}. No refund generated.", disputeId, adminUserId);
        }

        // 5. Cập nhật bản ghi Dispute
        dispute.setStatus(resolution);
        dispute.setRefundPercentage(appliedRefundPercentage);
        dispute.setAdminNote(request.adminNote());
        dispute.setResolvedBy(adminUserId);
        dispute.setResolvedAt(now);
        DisputeJpaEntity savedDispute = disputeRepository.save(dispute);

        if (transactionalEmailPort != null) {
            // 1. Email cho Khách hàng
            Map<String, Object> customerPayload = new HashMap<>();
            customerPayload.put("disputeId", savedDispute.getId().toString());
            customerPayload.put("recipientRole", "CUSTOMER");
            customerPayload.put("resolution", savedDispute.getStatus().name());
            customerPayload.put("refundPercentage", savedDispute.getRefundPercentage() != null ? savedDispute.getRefundPercentage().doubleValue() : 0.0);
            customerPayload.put("refundAmount", refund != null && refund.getAmount() != null ? refund.getAmount().doubleValue() : 0.0);
            customerPayload.put("refundId", refund != null ? refund.getId().toString() : "");
            customerPayload.put("reason", savedDispute.getReason() != null ? savedDispute.getReason().name() : "");
            customerPayload.put("adminNote", savedDispute.getAdminNote() != null ? savedDispute.getAdminNote() : "");
            customerPayload.put("orderId", savedDispute.getOrderId() != null ? savedDispute.getOrderId().toString() : "");

            transactionalEmailPort.enqueue(new EmailRequest(
                    TransactionalEmailType.DISPUTE_RESOLVED,
                    "dispute:" + savedDispute.getId() + ":customer:resolved",
                    "USER:" + savedDispute.getCustomerId(),
                    now,
                    now,
                    null,
                    1,
                    customerPayload
            ));

            // 2. Email cho Đối tác
            UUID vendorId = null;
            var subOrderOpt = subOrderRepository.findById(savedDispute.getSubOrderId());
            if (subOrderOpt.isPresent()) {
                vendorId = subOrderOpt.get().getVendorId();
            }
            if (vendorId != null && vendorInternalApi != null) {
                UUID vendorOwnerUserId = vendorInternalApi.findById(vendorId)
                        .map(Vendor::getUserId)
                        .orElse(null);
                if (vendorOwnerUserId != null) {
                    Map<String, Object> vendorPayload = new HashMap<>();
                    vendorPayload.put("disputeId", savedDispute.getId().toString());
                    vendorPayload.put("recipientRole", "VENDOR");
                    vendorPayload.put("resolution", savedDispute.getStatus().name());
                    vendorPayload.put("refundPercentage", savedDispute.getRefundPercentage() != null ? savedDispute.getRefundPercentage().doubleValue() : 0.0);
                    vendorPayload.put("refundAmount", refund != null && refund.getAmount() != null ? refund.getAmount().doubleValue() : 0.0);
                    vendorPayload.put("subOrderId", savedDispute.getSubOrderId().toString());

                    transactionalEmailPort.enqueue(new EmailRequest(
                            TransactionalEmailType.DISPUTE_RESOLVED,
                            "dispute:" + savedDispute.getId() + ":vendor:resolved",
                            "USER:" + vendorOwnerUserId,
                            now,
                            now,
                            null,
                            1,
                            vendorPayload
                    ));
                }
            }
        }

        return DisputeResponse.fromEntity(savedDispute);
    }

}

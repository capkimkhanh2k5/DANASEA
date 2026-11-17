package com.danasea.backend.modules.order.application.usecases;

import com.danasea.backend.modules.order.application.dtos.CancellationPreviewResult;
import com.danasea.backend.modules.order.application.dtos.GetCancellationPreviewQuery;
import com.danasea.backend.modules.order.application.services.DisruptionDecisionService;
import com.danasea.backend.modules.order.domain.exceptions.OrderNotFoundException;
import com.danasea.backend.modules.order.domain.models.MasterOrder;
import com.danasea.backend.modules.order.domain.models.RefundEvaluationResult;
import com.danasea.backend.modules.order.domain.models.RefundReason;
import com.danasea.backend.modules.order.domain.models.SubOrder;
import com.danasea.backend.modules.order.domain.ports.MasterOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.ports.ServiceSlotDepartureLookupPort;
import com.danasea.backend.modules.order.domain.ports.SubOrderRepositoryPort;
import com.danasea.backend.modules.order.domain.services.RefundPolicyEngine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GetCancellationPreviewUseCase {

    private DisruptionDecisionService disruptionDecisions;

    @Autowired
    public void setDisruptionDecisions(DisruptionDecisionService disruptionDecisions) {
        this.disruptionDecisions = disruptionDecisions;
    }

    private final MasterOrderRepositoryPort masterOrderRepository;
    private final SubOrderRepositoryPort subOrderRepository;
    private final ServiceSlotDepartureLookupPort serviceSlotDepartureLookupPort;
    private final RefundPolicyEngine refundPolicyEngine;

    public GetCancellationPreviewUseCase(
            MasterOrderRepositoryPort masterOrderRepository,
            SubOrderRepositoryPort subOrderRepository,
            ServiceSlotDepartureLookupPort serviceSlotDepartureLookupPort,
            RefundPolicyEngine refundPolicyEngine) {
        this.masterOrderRepository = masterOrderRepository;
        this.subOrderRepository = subOrderRepository;
        this.serviceSlotDepartureLookupPort = serviceSlotDepartureLookupPort;
        this.refundPolicyEngine = refundPolicyEngine;
    }

    @Transactional(readOnly = true)
    public CancellationPreviewResult execute(GetCancellationPreviewQuery query) {
        if (query == null || query.orderOrSubOrderId() == null) {
            throw new IllegalArgumentException("Order ID is required.");
        }

        MasterOrder masterOrder;
        List<SubOrder> subOrders;

        Optional<MasterOrder> masterOpt = query.vendorId() == null
                ? masterOrderRepository.findById(query.orderOrSubOrderId()) : Optional.empty();
        if (masterOpt.isPresent()) {
            masterOrder = masterOpt.get();
            subOrders = subOrderRepository.findByMasterOrderId(masterOrder.getId());
        } else {
            SubOrder subOrder =
                    subOrderRepository
                            .findById(query.orderOrSubOrderId())
                            .orElseThrow(
                                    () ->
                                            new OrderNotFoundException(
                                                    "Order not found with id: "
                                                            + query.orderOrSubOrderId()));
            if (query.vendorId() != null && !query.vendorId().equals(subOrder.getVendorId())) {
                throw new AccessDeniedException("Order is not available to this vendor");
            }
            masterOrder =
                    masterOrderRepository
                            .findById(subOrder.getMasterOrderId())
                            .orElseThrow(
                                    () ->
                                            new OrderNotFoundException(
                                                    "Master order not found for sub-order: "
                                                            + query.orderOrSubOrderId()));
            subOrders = List.of(subOrder);
        }

        // Chặn IDOR: Người dùng chỉ được xem trước hủy đơn của chính mình trừ khi là Quản trị viên
        // (ADMIN)
        if (query.vendorId() == null && !query.isAdmin()
                && (masterOrder.getCustomerId() == null
                        || !masterOrder.getCustomerId().equals(query.currentUserId()))) {
            throw new AccessDeniedException(
                    "User does not have permission to view cancellation preview for this order");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        List<CancellationPreviewResult.SubOrderCancellationPreviewResult> items = new ArrayList<>();
        BigDecimal totalOriginalAmount = BigDecimal.ZERO;
        BigDecimal totalRefundAmount = BigDecimal.ZERO;

        for (SubOrder subOrder : subOrders) {
            LocalDateTime departureTime =
                    subOrder.getSlotId() != null
                            ? serviceSlotDepartureLookupPort
                                    .findDepartureTime(subOrder.getSlotId())
                                    .orElse(null)
                            : null;

            BigDecimal originalAmount =
                    subOrder.getFinalAmount() != null
                            ? subOrder.getFinalAmount()
                            : (subOrder.getSubtotalAmount() != null
                                    ? subOrder.getSubtotalAmount()
                                    : BigDecimal.ZERO);

            RefundReason reason =
                    disruptionDecisions == null
                            ? RefundReason.CUSTOMER_REQUEST
                            : disruptionDecisions
                                    .verifiedReason(subOrder.getId(), subOrder.getSlotId())
                                    .orElse(RefundReason.CUSTOMER_REQUEST);
            RefundEvaluationResult eval =
                    refundPolicyEngine.evaluate(
                            reason,
                            departureTime,
                            now,
                            originalAmount,
                            null,
                            subOrder.getCancellationPolicyVersion());

            String policyApplied =
                    refundPolicyEngine.getPolicyDescription(
                            reason,
                            eval.refundPercentage(),
                            subOrder.getCancellationPolicyVersion());

            items.add(
                    new CancellationPreviewResult.SubOrderCancellationPreviewResult(
                            subOrder.getId(),
                            subOrder.getServiceId(),
                            subOrder.getSlotId(),
                            departureTime,
                            originalAmount,
                            eval.refundPercentage(),
                            eval.refundAmount(),
                            policyApplied));

            totalOriginalAmount = totalOriginalAmount.add(originalAmount);
            totalRefundAmount = totalRefundAmount.add(eval.refundAmount());
        }

        BigDecimal overallPercentage;
        if (totalOriginalAmount.compareTo(BigDecimal.ZERO) > 0) {
            overallPercentage =
                    totalRefundAmount
                            .multiply(BigDecimal.valueOf(100.0))
                            .divide(totalOriginalAmount, 1, RoundingMode.HALF_UP);
        } else {
            overallPercentage = BigDecimal.valueOf(0.0);
        }

        String masterPolicyApplied;
        if (items.isEmpty()) {
            masterPolicyApplied = "STANDARD_CANCELLATION_POLICY";
        } else {
            String firstPolicy = items.get(0).policyApplied();
            boolean allSame =
                    items.stream().allMatch(item -> firstPolicy.equals(item.policyApplied()));
            masterPolicyApplied =
                    allSame ? firstPolicy : "STANDARD_CANCELLATION_POLICY (Mixed items)";
        }

        return new CancellationPreviewResult(
                query.orderOrSubOrderId(),
                totalOriginalAmount,
                overallPercentage,
                totalRefundAmount,
                masterPolicyApplied,
                items);
    }
}

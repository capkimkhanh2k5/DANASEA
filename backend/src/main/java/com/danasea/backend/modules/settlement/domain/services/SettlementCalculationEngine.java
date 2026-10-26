package com.danasea.backend.modules.settlement.domain.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidCommissionRateException;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidOrderAmountException;
import com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason;
import com.danasea.backend.modules.settlement.domain.models.Settlement;
import com.danasea.backend.modules.settlement.domain.models.SettlementCalculationResult;
import com.danasea.backend.modules.settlement.domain.models.SettlementLineItem;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.domain.models.SubOrderCalculationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SettlementCalculationEngine {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public record CalculationItem(
        UUID subOrderId,
        UUID vendorId,
        BigDecimal subtotalAmount,
        BigDecimal commissionRate,
        SubOrderStatus status,
        BigDecimal refundAmount,
        boolean hasActiveDispute
    ) {}

    /**
     * Tính toán chi tiết từng dòng đơn hàng con (Line Item)
     */
    public SettlementLineItem calculateLineItem(SubOrderCalculationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("SubOrderCalculationContext cannot be null");
        }

        BigDecimal rate = context.getCommissionRate();
        if(rate==null) throw new InvalidCommissionRateException("Commission policy or snapshot is required");
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidCommissionRateException("Commission rate must be between 0 and 1");
        }

        BigDecimal subtotal = context.getSubtotalAmount() != null ? context.getSubtotalAmount() : BigDecimal.ZERO;
        if (subtotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidOrderAmountException("Order subtotal cannot be negative");
        }

        BigDecimal refund = context.getRefundAmount() != null ? context.getRefundAmount() : BigDecimal.ZERO;
        if (refund.compareTo(BigDecimal.ZERO) < 0) {
            refund = BigDecimal.ZERO;
        }

        BigDecimal customerPaid = context.getEffectiveFinalAmount();
        SubOrderStatus status = context.getStatus();

        // 1. Kiểm tra khiếu nại đang mở (Active Dispute) -> Tạm giữ
        boolean hasActiveDispute = context.isHasActiveDispute()
                || (context.getDisputeStatus() != null && (context.getDisputeStatus() == DisputeStatus.OPEN || context.getDisputeStatus() == DisputeStatus.UNDER_REVIEW));

        if (hasActiveDispute) {
            return SettlementLineItem.builder()
                    .id(UUID.randomUUID())
                    .subOrderId(context.getSubOrderId())
                    .grossAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .refundAmount(refund.setScale(SCALE, ROUNDING))
                    .commissionRate(rate)
                    .commissionAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .netAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .excludedReason(LineItemExclusionReason.ACTIVE_DISPUTE)
                    .build();
        }

        // 2. Kiểm tra hoàn tiền 100% (Full Refund)
        boolean isFullRefund = status == SubOrderStatus.REFUNDED
                || (context.getRefundPercentage() != null && context.getRefundPercentage().compareTo(new BigDecimal("100")) >= 0)
                || (customerPaid.signum() > 0 && refund.compareTo(customerPaid) >= 0);

        if (isFullRefund) {
            return SettlementLineItem.builder()
                    .id(UUID.randomUUID())
                    .subOrderId(context.getSubOrderId())
                    .grossAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .refundAmount(refund.setScale(SCALE, ROUNDING))
                    .commissionRate(rate)
                    .commissionAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .netAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .excludedReason(LineItemExclusionReason.FULL_REFUND)
                    .build();
        }

        // 3. Kiểm tra trạng thái đơn hợp lệ để đối soát
        if (status != null && status != SubOrderStatus.COMPLETED && status != SubOrderStatus.CHECKED_IN && status != SubOrderStatus.PARTIALLY_REFUNDED) {
            return SettlementLineItem.builder()
                    .id(UUID.randomUUID())
                    .subOrderId(context.getSubOrderId())
                    .grossAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .refundAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .commissionRate(rate)
                    .commissionAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .netAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .excludedReason(LineItemExclusionReason.NOT_ELIGIBLE)
                    .build();
        }

        BigDecimal basis = context.getEffectiveCommissionBasis();
        if (basis == null || basis.compareTo(BigDecimal.ZERO) < 0) {
            basis = BigDecimal.ZERO;
        }

        // 4. Hoàn tiền một phần (Partial Refund Net Calculation)
        if (status == SubOrderStatus.PARTIALLY_REFUNDED || refund.compareTo(BigDecimal.ZERO) > 0) {
            // Reverse platform funding in the same proportion as refunded customer cash.
            BigDecimal reversedBasis = customerPaid.signum() > 0
                    ? basis.multiply(refund).divide(customerPaid, SCALE, ROUNDING) : basis;
            BigDecimal netAfterRefund = basis.subtract(reversedBasis).max(BigDecimal.ZERO);
            if (netAfterRefund.compareTo(BigDecimal.ZERO) <= 0) {
                return SettlementLineItem.builder()
                        .id(UUID.randomUUID())
                        .subOrderId(context.getSubOrderId())
                        .grossAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                        .refundAmount(refund.setScale(SCALE, ROUNDING))
                        .commissionRate(rate)
                        .commissionAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                        .netAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                        .excludedReason(LineItemExclusionReason.FULL_REFUND)
                        .build();
            }

            BigDecimal grossAmount = netAfterRefund.setScale(SCALE, ROUNDING);
            BigDecimal commissionAmount = netAfterRefund.multiply(rate).setScale(SCALE, ROUNDING);
            BigDecimal netAmount = netAfterRefund.subtract(commissionAmount).setScale(SCALE, ROUNDING);

            return SettlementLineItem.builder()
                    .id(UUID.randomUUID())
                    .subOrderId(context.getSubOrderId())
                    .grossAmount(grossAmount)
                    .refundAmount(refund.setScale(SCALE, ROUNDING))
                    .commissionRate(rate)
                    .commissionAmount(commissionAmount)
                    .netAmount(netAmount)
                    .excludedReason(null)
                    .build();
        }

        // 5. Đơn hoàn tất chuẩn không có hoàn tiền (COMPLETED / CHECKED_IN)
        BigDecimal grossAmount = basis.setScale(SCALE, ROUNDING);
        BigDecimal commissionAmount = basis.multiply(rate).setScale(SCALE, ROUNDING);
        BigDecimal netAmount = basis.subtract(commissionAmount).setScale(SCALE, ROUNDING);

        return SettlementLineItem.builder()
                .id(UUID.randomUUID())
                .subOrderId(context.getSubOrderId())
                .grossAmount(grossAmount)
                .refundAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                .commissionRate(rate)
                .commissionAmount(commissionAmount)
                .netAmount(netAmount)
                .excludedReason(null)
                .build();
    }

    /**
     * Tính toán tổng hợp đối soát toàn kỳ trả về SettlementCalculationResult
     */
    public SettlementCalculationResult calculateSettlement(
            UUID vendorId,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<SubOrderCalculationContext> orders,
            BigDecimal defaultCommissionRate
    ) {
        log.info("Calculating settlement for vendor {} from {} to {} with {} orders",
                vendorId, periodStart, periodEnd, orders != null ? orders.size() : 0);

        if (orders == null || orders.isEmpty()) {
            return SettlementCalculationResult.builder()
                    .vendorId(vendorId)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .totalGrossRevenue(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .totalRefundAmount(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .totalCommission(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .totalNetPayout(BigDecimal.ZERO.setScale(SCALE, ROUNDING))
                    .lineItems(Collections.emptyList())
                    .build();
        }

        BigDecimal rate = defaultCommissionRate;
        List<SettlementLineItem> lineItems = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalRefund = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalCommission = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalPayout = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

        for (SubOrderCalculationContext order : orders) {
            if (order.getCommissionRate() == null) {
                if(rate==null) throw new InvalidCommissionRateException("Commission policy or snapshot is required");
                order.setCommissionRate(rate);
            }
            SettlementLineItem item = calculateLineItem(order);
            lineItems.add(item);

            totalRefund = totalRefund.add(item.getRefundAmount());
            if (!item.isExcluded()) {
                totalGross = totalGross.add(item.getGrossAmount());
                totalCommission = totalCommission.add(item.getCommissionAmount());
                totalPayout = totalPayout.add(item.getNetAmount());
            }
        }

        return SettlementCalculationResult.builder()
                .vendorId(vendorId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalGrossRevenue(totalGross)
                .totalRefundAmount(totalRefund)
                .totalCommission(totalCommission)
                .totalNetPayout(totalPayout)
                .lineItems(lineItems)
                .build();
    }

    /**
     * Tính toán tổng hợp trực tiếp ra Settlement domain model
     */
    public Settlement calculateDomainSettlement(
            UUID settlementId,
            UUID vendorId,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<SubOrderCalculationContext> orders,
            BigDecimal defaultCommissionRate
    ) {
        SettlementCalculationResult result = calculateSettlement(vendorId, periodStart, periodEnd, orders, defaultCommissionRate);
        OffsetDateTime now = OffsetDateTime.now();

        Settlement settlement = Settlement.builder()
                .id(settlementId != null ? settlementId : UUID.randomUUID())
                .vendorId(vendorId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalGrossRevenue(result.getTotalGrossRevenue())
                .totalCommission(result.getTotalCommission())
                .totalNetPayout(result.getTotalNetPayout())
                .status(SettlementStatus.DRAFT)
                .generatedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .lineItems(new ArrayList<>(result.getLineItems()))
                .build();

        for (SettlementLineItem item : settlement.getLineItems()) {
            item.setSettlementId(settlement.getId());
        }

        return settlement;
    }

    /**
     * Overload tương thích ngược cho CalculationItem
     */
    public SettlementLineItem calculateLineItem(UUID settlementId, CalculationItem item) {
        SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                .subOrderId(item.subOrderId())
                .subtotalAmount(item.subtotalAmount())
                .commissionRate(item.commissionRate())
                .status(item.status())
                .refundAmount(item.refundAmount())
                .hasActiveDispute(item.hasActiveDispute())
                .build();

        SettlementLineItem lineItem = calculateLineItem(context);
        lineItem.setSettlementId(settlementId);
        return lineItem;
    }

    public Settlement calculate(
            UUID settlementId,
            UUID vendorId,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<CalculationItem> items
    ) {
        List<SubOrderCalculationContext> contexts = items != null ? items.stream()
                .map(item -> SubOrderCalculationContext.builder()
                        .subOrderId(item.subOrderId())
                        .subtotalAmount(item.subtotalAmount())
                        .commissionRate(item.commissionRate())
                        .status(item.status())
                        .refundAmount(item.refundAmount())
                        .hasActiveDispute(item.hasActiveDispute())
                        .build())
                .toList() : Collections.emptyList();

        return calculateDomainSettlement(settlementId, vendorId, periodStart, periodEnd, contexts, null);
    }
}

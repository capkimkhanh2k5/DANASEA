package com.danasea.backend.modules.settlement.application.usecases;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.danasea.backend.modules.dispute.domain.models.DisputeStatus;
import com.danasea.backend.modules.dispute.infrastructure.persistence.entities.DisputeJpaEntity;
import com.danasea.backend.modules.dispute.infrastructure.persistence.repositories.JpaDisputeRepository;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.order.domain.models.SubOrderStatus;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.RefundJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.settlement.application.dtos.GenerateSettlementRequest;
import com.danasea.backend.modules.settlement.application.dtos.SettlementResponse;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidCommissionRateException;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidSettlementPeriodException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyFinalizedException;
import com.danasea.backend.modules.settlement.domain.models.LineItemExclusionReason;
import com.danasea.backend.modules.settlement.domain.models.SettlementLineItem;
import com.danasea.backend.modules.settlement.domain.models.SettlementStatus;
import com.danasea.backend.modules.settlement.domain.models.SubOrderCalculationContext;
import com.danasea.backend.modules.settlement.domain.services.SettlementCalculationEngine;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.entities.SettlementLineItemJpaEntity;
import com.danasea.backend.modules.settlement.infrastructure.persistence.mappers.SettlementMapper;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementLineItemRepository;
import com.danasea.backend.modules.settlement.infrastructure.persistence.repositories.JpaSettlementRepository;
import com.danasea.backend.modules.systemconfig.infrastructure.persistence.repositories.JpaSystemConfigRepository;
import com.danasea.backend.modules.vendor.application.api.VendorInternalApi;

@Service
public class GenerateSettlementUseCase {

    private final JpaSettlementRepository settlementRepository;
    private final JpaSettlementLineItemRepository lineItemRepository;
    private final JpaSubOrderRepository subOrderRepository;
    private final JpaRefundRepository refundRepository;
    private final JpaDisputeRepository disputeRepository;
    private final JpaServiceSlotRepository serviceSlotRepository;
    private final SettlementCalculationEngine calculationEngine;
    private final SettlementMapper mapper;
    private final VendorInternalApi vendorInternalApi;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private JpaSystemConfigRepository systemConfigRepository;

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setSystemConfigRepository(JpaSystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    @Autowired
    public GenerateSettlementUseCase(
            JpaSettlementRepository settlementRepository,
            JpaSettlementLineItemRepository lineItemRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaRefundRepository refundRepository,
            JpaDisputeRepository disputeRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            SettlementCalculationEngine calculationEngine,
            SettlementMapper mapper,
            VendorInternalApi vendorInternalApi
    ) {
        this.settlementRepository = settlementRepository;
        this.lineItemRepository = lineItemRepository;
        this.subOrderRepository = subOrderRepository;
        this.refundRepository = refundRepository;
        this.disputeRepository = disputeRepository;
        this.serviceSlotRepository = Objects.requireNonNull(serviceSlotRepository, "serviceSlotRepository");
        this.calculationEngine = calculationEngine;
        this.mapper = mapper;
        this.vendorInternalApi = Objects.requireNonNull(vendorInternalApi, "vendorInternalApi");
    }

    @Transactional
    public SettlementResponse execute(GenerateSettlementRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Settlement request is required.");
        }
        return execute(request.vendorId(), request.periodStart(), request.periodEnd());
    }

    @Transactional
    public SettlementResponse execute(UUID vendorId, LocalDate periodStart, LocalDate periodEnd) {
        if (vendorId == null) {
            throw new IllegalArgumentException("Vendor ID is required.");
        }
        if (periodStart == null || periodEnd == null) {
            throw new InvalidSettlementPeriodException("Settlement start and end dates are required.");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new InvalidSettlementPeriodException("Settlement start date must not be after its end date.");
        }

        // 1. Kiểm tra tính bất biến / Idempotency
        Optional<SettlementJpaEntity> existingOpt = settlementRepository
                .findByVendorIdAndPeriodForUpdate(vendorId, periodStart, periodEnd)
                .or(() -> settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(
                        vendorId, periodStart, periodEnd));
        if (existingOpt.isPresent()) {
            SettlementJpaEntity existing = existingOpt.get();
            if (existing.getStatus() != null && existing.getStatus().isImmutable()) {
                throw new SettlementAlreadyFinalizedException(
                        String.format("The settlement for this period has already been finalized or paid and cannot be re-generated (vendor: %s, status: %s)",
                                vendorId, existing.getStatus())
                );
            }
        }

        UUID settlementId = existingOpt.map(SettlementJpaEntity::getId).orElseGet(UUID::randomUUID);

        // 2. Lấy danh sách sub_orders của vendor
        List<SubOrderJpaEntity> subOrders = Objects.requireNonNull(
                subOrderRepository.findByVendorIdAndStatusIn(vendorId, Set.of(
                        SubOrderStatus.COMPLETED,
                        SubOrderStatus.CHECKED_IN,
                        SubOrderStatus.PARTIALLY_REFUNDED,
                        SubOrderStatus.REFUNDED
                )),
                "Sub-order repository returned null.");

        // 3. Lọc theo khung thời gian kỳ đối soát [periodStart, periodEnd] (bao hàm 2 đầu mốc)
        List<SubOrderJpaEntity> filteredOrders = new ArrayList<>();
        for (SubOrderJpaEntity so : subOrders) {
            LocalDate orderDate = null;
            if (so.getSlotId() != null) {
                ServiceSlotJpaEntity slot = serviceSlotRepository.findById(so.getSlotId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Settlement data is incomplete: slot not found for sub-order " + so.getId()));
                orderDate = slot.getDate();
            }

            if (orderDate == null && so.getCreatedAt() != null) {
                orderDate = so.getCreatedAt().toLocalDate();
            }

            if (orderDate != null) {
                if (orderDate.isBefore(periodStart) || orderDate.isAfter(periodEnd)) {
                    continue;
                }
            }
            filteredOrders.add(so);
        }

        // 4. Batch query cho disputes và refunds
        List<UUID> subOrderIds = filteredOrders.stream().map(SubOrderJpaEntity::getId).toList();
        Map<UUID, List<RefundJpaEntity>> refundMap = new HashMap<>();
        if (!subOrderIds.isEmpty()) {
            List<RefundJpaEntity> batchRefunds = Objects.requireNonNull(
                    refundRepository.findBySubOrderIdIn(subOrderIds),
                    "Refund repository returned null.");
            for (RefundJpaEntity refund : batchRefunds) {
                if (refund.getStatus() == RefundStatus.PROCESSED) {
                    refundMap.computeIfAbsent(refund.getSubOrderId(), key -> new ArrayList<>()).add(refund);
                }
            }
        }

        Set<UUID> activeDisputeSubOrderIds = new HashSet<>();
        if (!subOrderIds.isEmpty()) {
            List<DisputeJpaEntity> activeDisputes = Objects.requireNonNull(
                    disputeRepository.findBySubOrderIdInAndStatusIn(
                            subOrderIds,
                            List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW)),
                    "Dispute repository returned null.");
            for (DisputeJpaEntity dispute : activeDisputes) {
                activeDisputeSubOrderIds.add(dispute.getSubOrderId());
            }
        }

        // 5. Tính toán từng dòng đơn hàng
        List<SettlementLineItem> lineItems = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO.setScale(SettlementCalculationEngine.SCALE, SettlementCalculationEngine.ROUNDING);
        BigDecimal totalCommission = BigDecimal.ZERO.setScale(SettlementCalculationEngine.SCALE, SettlementCalculationEngine.ROUNDING);
        BigDecimal totalNetPayout = BigDecimal.ZERO.setScale(SettlementCalculationEngine.SCALE, SettlementCalculationEngine.ROUNDING);

        for (SubOrderJpaEntity so : filteredOrders) {
            List<RefundJpaEntity> refunds = refundMap.getOrDefault(so.getId(), Collections.emptyList());
            BigDecimal totalRefund = refunds.stream()
                    .map(RefundJpaEntity::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            boolean hasActiveDispute = activeDisputeSubOrderIds.contains(so.getId());

            BigDecimal subtotal = so.getSubtotalAmount() != null ? so.getSubtotalAmount() : BigDecimal.ZERO;
            BigDecimal rate = so.getCommissionRate();
            if(rate==null) throw new InvalidCommissionRateException(
                    "Commission snapshot missing for sub-order " + so.getId());

            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(so.getId())
                    .status(so.getStatus())
                    .subtotalAmount(subtotal)
                    .refundAmount(totalRefund)
                    .hasActiveDispute(hasActiveDispute)
                    .commissionRate(rate)
                    .vendorDiscountAmount(so.getVendorDiscountAmount())
                    .platformDiscountAmount(so.getPlatformDiscountAmount())
                    .commissionBasisAmount(so.getCommissionBasisAmount())
                    .finalAmount(so.getFinalAmount())
                    .build();

            SettlementLineItem item = calculationEngine.calculateLineItem(context);
            item.setSettlementId(settlementId);
            lineItems.add(item);

            if (!item.isExcluded()) {
                totalGross = totalGross.add(item.getGrossAmount());
                totalCommission = totalCommission.add(item.getCommissionAmount());
                totalNetPayout = totalNetPayout.add(item.getNetAmount());
            }
        }

        // 5.1. Đánh giá Quỹ thưởng An toàn Biển (Safety Rebate 2.0% tiền mặt)
        BigDecimal rebateRate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal rebateAmount = BigDecimal.ZERO.setScale(SettlementCalculationEngine.SCALE, SettlementCalculationEngine.ROUNDING);
        boolean rebateQualified = false;
        String rebateNote = null;

        boolean rebateEnabled = true;
        if (systemConfigRepository != null) {
            var enabledOpt = systemConfigRepository.findByKey("SAFETY_REBATE_ENABLED");
            if (enabledOpt.isPresent() && "false".equalsIgnoreCase(enabledOpt.get().getValue())) {
                rebateEnabled = false;
            }
        }

        if (rebateEnabled && totalGross.signum() > 0) {
            BigDecimal configRate = new BigDecimal("0.0200");
            if (systemConfigRepository != null) {
                var rateOpt = systemConfigRepository.findByKey("SAFETY_REBATE_RATE");
                if (rateOpt.isPresent() && rateOpt.get().getValue() != null && !rateOpt.get().getValue().isBlank()) {
                    try {
                        BigDecimal parsed = new BigDecimal(rateOpt.get().getValue().trim());
                        if (parsed.compareTo(BigDecimal.ONE) > 0) {
                            parsed = parsed.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                        }
                        if (parsed.signum() >= 0 && parsed.compareTo(BigDecimal.ONE) <= 0) {
                            configRate = parsed;
                        }
                    } catch (Exception ignored) {}
                }
            }

            boolean hasNoActiveDisputes = activeDisputeSubOrderIds.isEmpty();
            boolean hasNoVendorFault = true;
            if (jdbcTemplate != null) {
                try {
                    Integer faultCount = jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM vendor_fault_compensations WHERE vendor_id = ? AND created_at >= ? AND created_at <= ?",
                            Integer.class,
                            vendorId,
                            periodStart.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime(),
                            periodEnd.atTime(java.time.LocalTime.MAX).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime()
                    );
                    if (faultCount != null && faultCount > 0) {
                        hasNoVendorFault = false;
                    }
                } catch (Exception ignored) {}
            }

            boolean highRating = false;
            var vendorOpt = vendorInternalApi.findById(vendorId);
            if (vendorOpt.isPresent()) {
                var vendor = vendorOpt.get();
                if (vendor.getRatingAvg() != null && vendor.getRatingCount() != null && vendor.getRatingCount() >= 5) {
                    highRating = vendor.getRatingAvg().compareTo(new BigDecimal("4.80")) >= 0;
                }
            }

            if (hasNoActiveDisputes && hasNoVendorFault && highRating) {
                rebateQualified = true;
                rebateRate = configRate;
                rebateAmount = totalGross.multiply(rebateRate).setScale(SettlementCalculationEngine.SCALE, SettlementCalculationEngine.ROUNDING);
                totalNetPayout = totalNetPayout.add(rebateAmount);
                rebateNote = "Đủ điều kiện nhận Quỹ thưởng An toàn Biển & Vận hành xuất sắc (+" + rebateRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%)";
            } else {
                List<String> reasons = new ArrayList<>();
                if (!hasNoActiveDisputes) reasons.add("Có khiếu nại chưa đóng");
                if (!hasNoVendorFault) reasons.add("Có lỗi phạt hủy chuyến");
                if (!highRating) reasons.add("Điểm đánh giá chưa đạt 4.8*");
                rebateNote = "Không đủ điều kiện thưởng An toàn: " + String.join(", ", reasons);
            }
        }

        // 6. Lưu hoặc cập nhật Settlement
        SettlementJpaEntity settlementEntity;
        if (existingOpt.isPresent()) {
            settlementEntity = existingOpt.get();
            lineItemRepository.deleteBySettlementId(settlementId);
            settlementEntity.setGrossAmount(totalGross);
            settlementEntity.setCommissionAmount(totalCommission);
            settlementEntity.setNetPayableAmount(totalNetPayout);
            settlementEntity.setSafetyRebateRate(rebateRate);
            settlementEntity.setSafetyRebateAmount(rebateAmount);
            settlementEntity.setSafetyRebateQualified(rebateQualified);
            settlementEntity.setSafetyRebateNote(rebateNote);
            settlementEntity.setGeneratedAt(OffsetDateTime.now());
            settlementEntity = settlementRepository.save(settlementEntity);
        } else {
            settlementEntity = SettlementJpaEntity.builder()
                    .vendorId(vendorId)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .grossAmount(totalGross)
                    .commissionAmount(totalCommission)
                    .netPayableAmount(totalNetPayout)
                    .safetyRebateRate(rebateRate)
                    .safetyRebateAmount(rebateAmount)
                    .safetyRebateQualified(rebateQualified)
                    .safetyRebateNote(rebateNote)
                    .status(SettlementStatus.DRAFT)
                    .generatedAt(OffsetDateTime.now())
                    .build();
            settlementEntity.setId(settlementId);
            settlementEntity = settlementRepository.save(settlementEntity);
        }

        // 7. Lưu các LineItems
        for (SettlementLineItem item : lineItems) {
            SettlementLineItemJpaEntity lie = mapper.toLineItemEntity(item);
            lie.setSettlementId(settlementId);
            lineItemRepository.save(lie);
        }

        return mapper.toResponse(settlementEntity);
    }
}

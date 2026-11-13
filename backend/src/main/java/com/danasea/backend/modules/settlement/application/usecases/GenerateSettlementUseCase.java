package com.danasea.backend.modules.settlement.application.usecases;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderDisruptionJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.entities.SubOrderJpaEntity;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaRefundRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderDisruptionRepository;
import com.danasea.backend.modules.order.infrastructure.persistence.repositories.JpaSubOrderRepository;
import com.danasea.backend.modules.service.infrastructure.persistence.entities.ServiceSlotJpaEntity;
import com.danasea.backend.modules.service.infrastructure.persistence.repositories.JpaServiceSlotRepository;
import com.danasea.backend.modules.settlement.application.dtos.GenerateSettlementRequest;
import com.danasea.backend.modules.settlement.application.dtos.SettlementResponse;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidCommissionRateException;
import com.danasea.backend.modules.settlement.domain.exceptions.InvalidSettlementPeriodException;
import com.danasea.backend.modules.settlement.domain.exceptions.SettlementAlreadyFinalizedException;
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
    private final JpaSubOrderDisruptionRepository disruptionRepository;
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

    public GenerateSettlementUseCase(
            JpaSettlementRepository settlementRepository,
            JpaSettlementLineItemRepository lineItemRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaRefundRepository refundRepository,
            JpaDisputeRepository disputeRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            SettlementCalculationEngine calculationEngine,
            SettlementMapper mapper,
            VendorInternalApi vendorInternalApi) {
        this(settlementRepository, lineItemRepository, subOrderRepository, refundRepository,
                disputeRepository, serviceSlotRepository, null, calculationEngine, mapper, vendorInternalApi);
    }

    @Autowired
    public GenerateSettlementUseCase(
            JpaSettlementRepository settlementRepository,
            JpaSettlementLineItemRepository lineItemRepository,
            JpaSubOrderRepository subOrderRepository,
            JpaRefundRepository refundRepository,
            JpaDisputeRepository disputeRepository,
            JpaServiceSlotRepository serviceSlotRepository,
            @Autowired(required = false) JpaSubOrderDisruptionRepository disruptionRepository,
            SettlementCalculationEngine calculationEngine,
            SettlementMapper mapper,
            VendorInternalApi vendorInternalApi) {
        this.settlementRepository = settlementRepository;
        this.lineItemRepository = lineItemRepository;
        this.subOrderRepository = subOrderRepository;
        this.refundRepository = refundRepository;
        this.disputeRepository = disputeRepository;
        this.serviceSlotRepository = Objects.requireNonNull(serviceSlotRepository, "serviceSlotRepository");
        this.disruptionRepository = disruptionRepository;
        this.calculationEngine = calculationEngine;
        this.mapper = mapper;
        this.vendorInternalApi = Objects.requireNonNull(vendorInternalApi, "vendorInternalApi");
    }

    @Transactional
    public SettlementResponse execute(GenerateSettlementRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Settlement request is required.");
        }
        return execute(request.vendorId(), request.periodStart(), request.periodEnd(), LocalDate.now());
    }

    @Transactional
    public SettlementResponse execute(UUID vendorId, LocalDate periodStart, LocalDate periodEnd) {
        return execute(vendorId, periodStart, periodEnd, LocalDate.now());
    }

    @Transactional
    public SettlementResponse execute(UUID vendorId, LocalDate periodStart, LocalDate periodEnd, LocalDate evaluationDate) {
        if (vendorId == null) {
            throw new IllegalArgumentException("Vendor ID is required.");
        }
        if (periodStart == null || periodEnd == null) {
            throw new InvalidSettlementPeriodException("Settlement start and end dates are required.");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new InvalidSettlementPeriodException("Settlement start date must not be after its end date.");
        }
        LocalDate evalDate = evaluationDate != null ? evaluationDate : LocalDate.now();

        // 1. Kiểm tra tính bất biến / Idempotency
        Optional<SettlementJpaEntity> existingOpt = settlementRepository
                .findByVendorIdAndPeriodForUpdate(vendorId, periodStart, periodEnd)
                .or(() -> settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(
                        vendorId, periodStart, periodEnd));
        if (existingOpt.isPresent()) {
            SettlementJpaEntity existing = existingOpt.get();
            if (existing.getStatus() != null && existing.getStatus().isImmutable()) {
                throw new SettlementAlreadyFinalizedException(
                        String.format(
                                "The settlement for this period has already been finalized or paid and cannot be re-generated (vendor: %s, status: %s)",
                                vendorId, existing.getStatus()));
            }
        }

        UUID settlementId = existingOpt.map(SettlementJpaEntity::getId).orElseGet(UUID::randomUUID);

        // 2. Chống quyết toán trùng / chi trả trùng:
        // Lấy danh sách subOrderId đã được quyết toán và chi trả (hoặc hoàn tiền 100%) trong các kỳ đối soát đã FINALIZED / PAID của vendor
        Set<UUID> alreadySettledIds = lineItemRepository.findSettledSubOrderIdsByVendorId(vendorId);
        if (alreadySettledIds == null) {
            alreadySettledIds = Collections.emptySet();
        }

        // 3. Lấy danh sách sub_orders của vendor (Bao gồm COMPLETED, CHECKED_IN, PARTIALLY_REFUNDED, REFUNDED, NO_SHOW)
        List<SubOrderJpaEntity> subOrders = Objects.requireNonNull(
                subOrderRepository.findByVendorIdAndStatusIn(vendorId, Set.of(
                        SubOrderStatus.COMPLETED,
                        SubOrderStatus.CHECKED_IN,
                        SubOrderStatus.PARTIALLY_REFUNDED,
                        SubOrderStatus.REFUNDED,
                        SubOrderStatus.NO_SHOW)),
                "Sub-order repository returned null.");

        // 4. Lọc đơn hợp lệ:
        // - Không bỏ sót đơn gối kỳ: Các đơn trước periodStart bị giữ lại ở kỳ trước (do vướng D+7, dispute, refund)
        //   sẽ tự động được đưa vào xét duyệt ở kỳ này!
        // - Đơn không vượt quá periodEnd (orderDate <= periodEnd).
        // - Đơn chưa từng được thanh toán trong kỳ đã chốt (alreadySettledIds).
        List<SubOrderJpaEntity> candidateOrders = new ArrayList<>();
        Map<UUID, LocalDate> orderDateMap = new HashMap<>();

        for (SubOrderJpaEntity so : subOrders) {
            if (alreadySettledIds.contains(so.getId())) {
                continue; // Đã chốt chi trả ở kỳ trước
            }

            LocalDate orderDate = null;
            if (so.getSlotId() != null) {
                var slotOpt = serviceSlotRepository.findById(so.getSlotId());
                if (slotOpt.isPresent()) {
                    orderDate = slotOpt.get().getDate();
                }
            }
            if (orderDate == null && so.getCreatedAt() != null) {
                orderDate = so.getCreatedAt().toLocalDate();
            }

            if (orderDate != null) {
                if (orderDate.isAfter(periodEnd)) {
                    continue; // Đơn thuộc các ca trong tương lai sau kỳ đối soát
                }
                orderDateMap.put(so.getId(), orderDate);
                candidateOrders.add(so);
            }
        }

        // 5. Batch queries cho disputes, refunds, disruptions
        List<UUID> subOrderIds = candidateOrders.stream().map(SubOrderJpaEntity::getId).toList();

        Map<UUID, List<RefundJpaEntity>> refundMap = new HashMap<>();
        Set<UUID> pendingRefundSubOrderIds = new HashSet<>();
        if (!subOrderIds.isEmpty()) {
            List<RefundJpaEntity> batchRefunds = Objects.requireNonNull(
                    refundRepository.findBySubOrderIdIn(subOrderIds),
                    "Refund repository returned null.");
            for (RefundJpaEntity refund : batchRefunds) {
                if (refund.getStatus() == RefundStatus.PROCESSED) {
                    refundMap.computeIfAbsent(refund.getSubOrderId(), key -> new ArrayList<>()).add(refund);
                } else if (refund.getStatus() == RefundStatus.PENDING) {
                    pendingRefundSubOrderIds.add(refund.getSubOrderId());
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

        Set<UUID> activeDisruptionSubOrderIds = new HashSet<>();
        if (disruptionRepository != null && !subOrderIds.isEmpty()) {
            List<SubOrderDisruptionJpaEntity> activeDisruptions = disruptionRepository
                    .findBySubOrderIdInAndStatus(subOrderIds, "AWAITING_DECISION");
            if (activeDisruptions != null) {
                for (SubOrderDisruptionJpaEntity disruption : activeDisruptions) {
                    activeDisruptionSubOrderIds.add(disruption.getSubOrderId());
                }
            }
        }

        // 6. Tính toán từng dòng đơn hàng
        List<SettlementLineItem> lineItems = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO.setScale(SettlementCalculationEngine.SCALE,
                SettlementCalculationEngine.ROUNDING);
        BigDecimal totalCommission = BigDecimal.ZERO.setScale(SettlementCalculationEngine.SCALE,
                SettlementCalculationEngine.ROUNDING);
        BigDecimal totalNetPayout = BigDecimal.ZERO.setScale(SettlementCalculationEngine.SCALE,
                SettlementCalculationEngine.ROUNDING);

        for (SubOrderJpaEntity so : candidateOrders) {
            List<RefundJpaEntity> refunds = refundMap.getOrDefault(so.getId(), Collections.emptyList());
            BigDecimal totalRefund = refunds.stream()
                    .map(RefundJpaEntity::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            boolean hasActiveDispute = activeDisputeSubOrderIds.contains(so.getId());
            boolean hasPendingRefund = pendingRefundSubOrderIds.contains(so.getId());
            boolean hasActiveDisruption = activeDisruptionSubOrderIds.contains(so.getId());

            // Quy tắc D+7: Cửa sổ khiếu nại mở trong 7 ngày kể từ experienceDate (theo CreateDisputeUseCase)
            LocalDate experienceDate = orderDateMap.get(so.getId());
            boolean isDisputeWindowOpen = experienceDate != null && !evalDate.isAfter(experienceDate.plusDays(7));

            BigDecimal subtotal = so.getSubtotalAmount() != null ? so.getSubtotalAmount() : BigDecimal.ZERO;
            BigDecimal rate = so.getCommissionRate();
            if (rate == null)
                throw new InvalidCommissionRateException(
                        "Commission snapshot missing for sub-order " + so.getId());

            SubOrderCalculationContext context = SubOrderCalculationContext.builder()
                    .subOrderId(so.getId())
                    .status(so.getStatus())
                    .subtotalAmount(subtotal)
                    .refundAmount(totalRefund)
                    .hasActiveDispute(hasActiveDispute)
                    .hasPendingRefund(hasPendingRefund)
                    .hasActiveDisruption(hasActiveDisruption)
                    .disputeWindowOpen(isDisputeWindowOpen)
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

        // 6.1. Đánh giá Quỹ thưởng An toàn Biển (Safety Rebate 2.0% tiền mặt)
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
                            periodEnd.atTime(LocalTime.MAX).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime()
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

        // 7. Lưu hoặc cập nhật Settlement
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

        // 8. Lưu các LineItems
        for (SettlementLineItem item : lineItems) {
            SettlementLineItemJpaEntity lie = mapper.toLineItemEntity(item);
            lie.setSettlementId(settlementId);
            lineItemRepository.save(lie);
        }

        return mapper.toResponse(settlementEntity);
    }
}

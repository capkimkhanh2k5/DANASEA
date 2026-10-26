package com.danasea.backend.modules.report.application.usecases;

import com.danasea.backend.modules.order.domain.models.PaymentStatus;
import com.danasea.backend.modules.order.domain.models.RefundStatus;
import com.danasea.backend.modules.report.application.ports.PaymentRecord;
import com.danasea.backend.modules.report.application.ports.RefundRecord;
import com.danasea.backend.modules.report.application.ports.ReportDataPort;
import com.danasea.backend.modules.report.application.ports.SubOrderRecord;
import com.danasea.backend.modules.report.domain.models.TimeRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Cash and commission events; negative adjustments stay in the period of the refund. */
final class ReportFinancialLedger {
    record Entry(
            UUID subOrderId,
            UUID vendorId,
            OffsetDateTime occurredAt,
            BigDecimal gmv,
            BigDecimal discount,
            BigDecimal cash,
            BigDecimal refunds,
            BigDecimal commission,
            boolean payment,
            BigDecimal payout) {
        public Entry(
                UUID subOrderId,
                UUID vendorId,
                OffsetDateTime occurredAt,
                BigDecimal gmv,
                BigDecimal discount,
                BigDecimal cash,
                BigDecimal refunds,
                BigDecimal commission,
                boolean payment) {
            this(
                    subOrderId,
                    vendorId,
                    occurredAt,
                    gmv,
                    discount,
                    cash,
                    refunds,
                    commission,
                    payment,
                    payment ? cash.subtract(commission) : commission.subtract(refunds));
        }
    }

    private ReportFinancialLedger() {}

    static List<Entry> load(ReportDataPort port, TimeRange range, UUID vendorId) {
        List<PaymentRecord> payments =
                port.findPayments(range).stream()
                        .filter(ReportFinancialLedger::isPaid)
                        .filter(p -> range.contains(p.paidAt()))
                        .toList();
        List<RefundRecord> refunds =
                port.findRefunds(range, vendorId).stream()
                        .filter(
                                r ->
                                        r.status() == RefundStatus.PROCESSED
                                                && range.contains(r.processedAt()))
                        .toList();
        Map<UUID, SubOrderRecord> orders = new HashMap<>();
        port.findSubOrdersByMasterOrderIds(
                        payments.stream().map(PaymentRecord::masterOrderId).distinct().toList())
                .forEach(o -> orders.put(o.id(), o));
        port.findSubOrdersByIds(refunds.stream().map(RefundRecord::subOrderId).distinct().toList())
                .forEach(o -> orders.put(o.id(), o));
        Map<UUID, List<SubOrderRecord>> byMaster =
                orders.values().stream()
                        .collect(Collectors.groupingBy(SubOrderRecord::masterOrderId));
        List<Entry> result = new ArrayList<>();
        for (PaymentRecord payment : payments) {
            List<SubOrderRecord> lines =
                    byMaster.getOrDefault(payment.masterOrderId(), List.of()).stream()
                            .sorted(Comparator.comparing(o -> o.id().toString()))
                            .toList();
            Map<UUID, BigDecimal> cashShares = allocate(safe(payment.amount()), lines);
            for (SubOrderRecord order : lines) {
                if (vendorId != null && !vendorId.equals(order.vendorId())) continue;
                BigDecimal gross = safe(order.subtotalAmount());
                BigDecimal cash = cashShares.getOrDefault(order.id(), BigDecimal.ZERO);
                BigDecimal comm = commission(order, BigDecimal.ZERO);
                BigDecimal basis = effectiveCommissionBasis(order);
                BigDecimal payout = basis.subtract(comm);
                result.add(
                        new Entry(
                                order.id(),
                                order.vendorId(),
                                payment.paidAt(),
                                gross,
                                gross.subtract(cash),
                                cash,
                                BigDecimal.ZERO,
                                comm,
                                true,
                                payout));
            }
        }
        // Read the full refund history for each affected line to reverse rounded commission once.
        Map<UUID, List<RefundRecord>> history =
                port
                        .findRefundsBySubOrderIds(
                                refunds.stream().map(RefundRecord::subOrderId).distinct().toList())
                        .stream()
                        .filter(
                                r ->
                                        r.status() == RefundStatus.PROCESSED
                                                && r.processedAt() != null)
                        .collect(Collectors.groupingBy(RefundRecord::subOrderId));
        Map<UUID, BigDecimal> commissionReversals = new HashMap<>();
        Map<UUID, BigDecimal> payoutReversals = new HashMap<>();
        for (Map.Entry<UUID, List<RefundRecord>> group : history.entrySet()) {
            SubOrderRecord order = orders.get(group.getKey());
            if (order == null) continue;
            BigDecimal basis = effectiveCommissionBasis(order);
            BigDecimal cash = effectiveCashPayable(order);
            BigDecimal cumulative = BigDecimal.ZERO;
            for (RefundRecord refund :
                    group.getValue().stream()
                            .sorted(
                                    Comparator.comparing(RefundRecord::processedAt)
                                            .thenComparing(r -> r.id().toString()))
                                    .toList()) {
                BigDecimal beforeBasis = basis.subtract(cash.signum() > 0 ? basis.multiply(cumulative).divide(cash, 2, RoundingMode.HALF_UP) : cumulative).max(BigDecimal.ZERO);
                BigDecimal beforeComm = commission(order, cumulative);

                cumulative = cumulative.add(safe(refund.amount()));

                BigDecimal afterBasis = basis.subtract(cash.signum() > 0 ? basis.multiply(cumulative).divide(cash, 2, RoundingMode.HALF_UP) : cumulative).max(BigDecimal.ZERO);
                BigDecimal afterComm = commission(order, cumulative);

                BigDecimal commReversal = beforeComm.subtract(afterComm);
                BigDecimal basisReversal = beforeBasis.subtract(afterBasis);
                BigDecimal payoutReversal = basisReversal.subtract(commReversal);

                commissionReversals.put(refund.id(), commReversal);
                payoutReversals.put(refund.id(), payoutReversal);
            }
        }
        for (RefundRecord refund : refunds) {
            SubOrderRecord order = orders.get(refund.subOrderId());
            if (order == null || (vendorId != null && !vendorId.equals(order.vendorId()))) continue;
            BigDecimal commReversal = commissionReversals.getOrDefault(refund.id(), BigDecimal.ZERO);
            BigDecimal payoutReversal = payoutReversals.getOrDefault(refund.id(), BigDecimal.ZERO);
            result.add(
                    new Entry(
                            order.id(),
                            order.vendorId(),
                            refund.processedAt(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            safe(refund.amount()),
                            commReversal.negate(),
                            false,
                            payoutReversal.negate()));
        }
        return result;
    }

    private static boolean isPaid(PaymentRecord p) {
        return p.status() == PaymentStatus.SUCCESS || p.status() == PaymentStatus.REFUNDED;
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal effectiveCashPayable(SubOrderRecord o) {
        if (o.finalAmount() != null) return safe(o.finalAmount());
        return safe(o.subtotalAmount());
    }

    private static BigDecimal effectiveCommissionBasis(SubOrderRecord o) {
        if (o.commissionBasisAmount() != null) return safe(o.commissionBasisAmount());
        return safe(o.subtotalAmount());
    }

    private static BigDecimal commission(SubOrderRecord order, BigDecimal refundedCash) {
        BigDecimal basis = effectiveCommissionBasis(order);
        BigDecimal cash = effectiveCashPayable(order);
        BigDecimal reversedBasis = cash.signum() > 0
                ? basis.multiply(safe(refundedCash)).divide(cash, 2, RoundingMode.HALF_UP)
                : safe(refundedCash);
        BigDecimal remaining = basis.subtract(reversedBasis).max(BigDecimal.ZERO);
        if (order.commissionRate() != null) {
            return remaining.multiply(order.commissionRate()).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal gross = effectiveCommissionBasis(order);
        return gross.signum() == 0
                ? BigDecimal.ZERO
                : safe(order.commissionAmount())
                        .multiply(remaining)
                        .divide(gross, 2, RoundingMode.HALF_UP);
    }

    // Largest remainder allocation preserves the exact cash total, including at cent boundaries.
    private static Map<UUID, BigDecimal> allocate(BigDecimal total, List<SubOrderRecord> orders) {
        BigDecimal weight =
                orders.stream()
                        .map(ReportFinancialLedger::effectiveCashPayable)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weight.signum() <= 0) return Map.of();
        Map<UUID, BigDecimal> amounts = new HashMap<>();
        Map<UUID, BigDecimal> remainders = new HashMap<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (SubOrderRecord order : orders) {
            BigDecimal exact =
                    total.multiply(effectiveCashPayable(order))
                            .divide(weight, 12, RoundingMode.HALF_UP);
            BigDecimal rounded = exact.setScale(2, RoundingMode.DOWN);
            amounts.put(order.id(), rounded);
            remainders.put(order.id(), exact.subtract(rounded));
            allocated = allocated.add(rounded);
        }
        int cents = total.subtract(allocated).movePointRight(2).intValueExact();
        List<SubOrderRecord> ranked =
                orders.stream()
                        .sorted(
                                Comparator.<SubOrderRecord, BigDecimal>comparing(
                                                o -> remainders.get(o.id()))
                                        .reversed()
                                        .thenComparing(o -> o.id().toString()))
                        .toList();
        for (int i = 0; i < cents; i++) {
            UUID id = ranked.get(i % ranked.size()).id();
            amounts.put(id, amounts.get(id).add(new BigDecimal("0.01")));
        }
        return amounts;
    }
}

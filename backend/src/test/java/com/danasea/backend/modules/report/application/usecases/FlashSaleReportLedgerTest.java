package com.danasea.backend.modules.report.application.usecases;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.danasea.backend.modules.order.domain.models.*;
import com.danasea.backend.modules.report.application.ports.*;
import com.danasea.backend.modules.report.domain.models.TimeRange;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FlashSaleReportLedgerTest {
    final UUID master=UUID.randomUUID(),vendor=UUID.randomUUID();
    final OffsetDateTime paid=OffsetDateTime.parse("2026-10-25T10:00:00+07:00");
    final TimeRange range=TimeRange.of(paid.toLocalDate(),paid.toLocalDate());
    static BigDecimal n(String s) { return new BigDecimal(s); }
    SubOrderRecord order(String original,String cash,String commission,String funding,String basis) {
        return new SubOrderRecord(UUID.randomUUID(),master,vendor,UUID.randomUUID(),UUID.randomUUID(),1,
                n(original),n(original),n("0.10"),n(commission),n(basis).subtract(n(commission)),SubOrderStatus.COMPLETED,
                paid,paid,null,n(cash),n(funding),n(basis));
    }
    RefundRecord refund(SubOrderRecord order,String amount,int minute) {
        return new RefundRecord(UUID.randomUUID(),order.id(),UUID.randomUUID(),n(amount),null,RefundReason.CUSTOMER_CANCEL,
                RefundStatus.PROCESSED,paid.plusMinutes(minute),paid.plusMinutes(minute));
    }
    List<ReportFinancialLedger.Entry> ledger(List<SubOrderRecord> lines,String cash,List<RefundRecord> refunds) {
        var port=mock(ReportDataPort.class);
        when(port.findPayments(range)).thenReturn(List.of(new PaymentRecord(UUID.randomUUID(),master,n(cash),PaymentStatus.SUCCESS,PaymentProvider.SEPAY,paid,paid,paid)));
        when(port.findRefunds(range,vendor)).thenReturn(refunds);
        when(port.findSubOrdersByMasterOrderIds(anyList())).thenReturn(lines);
        when(port.findSubOrdersByIds(anyList())).thenReturn(lines);
        when(port.findRefundsBySubOrderIds(anyList())).thenReturn(refunds);
        return ReportFinancialLedger.load(port,range,vendor);
    }
    BigDecimal payout(List<ReportFinancialLedger.Entry> entries) { return entries.stream().map(ReportFinancialLedger.Entry::payout).reduce(BigDecimal.ZERO,BigDecimal::add); }
    BigDecimal commission(List<ReportFinancialLedger.Entry> entries) { return entries.stream().map(ReportFinancialLedger.Entry::commission).reduce(BigDecimal.ZERO,BigDecimal::add); }
    @Test void platformFundingPreservesVendorReceivableInReports() {
        var order=order("500000","470000","50000","30000","500000");
        assertThat(payout(ledger(List.of(order),"470000",List.of()))).isEqualByComparingTo("450000");
    }
    @Test void fullCustomerCashRefundReversesAllCommissionAndPayout() {
        var order=order("500000","470000","50000","30000","500000");
        var entries=ledger(List.of(order),"470000",List.of(refund(order,"470000",1)));
        assertThat(commission(entries)).isEqualByComparingTo("0"); assertThat(payout(entries)).isEqualByComparingTo("0");
    }
    @Test void partialCustomerCashRefundReversesFundingProportionately() {
        var order=order("500000","470000","50000","30000","500000");
        var entries=ledger(List.of(order),"470000",List.of(refund(order,"235000",1)));
        assertThat(commission(entries)).isEqualByComparingTo("25000"); assertThat(payout(entries)).isEqualByComparingTo("225000");
    }
    @Test void mixedItemsUseTheirActualFinalAmountsForCashAllocation() {
        var offer=order("500000","470000","50000","30000","500000"); var regular=order("500000","500000","50000","0","500000");
        var entries=ledger(List.of(offer,regular),"970000",List.of());
        assertThat(entries.stream().filter(e->e.subOrderId().equals(offer.id())).findFirst().orElseThrow().cash()).isEqualByComparingTo("470000");
        assertThat(payout(entries)).isEqualByComparingTo("900000");
    }
    @Test void existingVendorDiscountUsesItsStoredCommissionBasis() {
        var order=order("500000","450000","45000","0","450000");
        assertThat(payout(ledger(List.of(order),"450000",List.of()))).isEqualByComparingTo("405000");
    }
    @Test void cumulativeCentRefundsLeaveNoResidualCommissionOrFunding() {
        var order=order("0.04","0.03","0.01","0.01","0.04");
        var entries=ledger(List.of(order),"0.03",List.of(refund(order,"0.01",1),refund(order,"0.01",2),refund(order,"0.01",3)));
        assertThat(commission(entries)).isEqualByComparingTo("0"); assertThat(payout(entries)).isEqualByComparingTo("0");
    }
}

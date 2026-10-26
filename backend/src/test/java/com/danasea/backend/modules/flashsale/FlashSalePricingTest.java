package com.danasea.backend.modules.flashsale;

import static org.assertj.core.api.Assertions.*;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing;
import com.danasea.backend.modules.flashsale.domain.FlashSalePricing.Policy;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlashSalePricingTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private Policy demo() { return new Policy(UUID.randomUUID(), UUID.randomUUID(), 1,
            n("0.10"), n("0.80"), n("5000"), n("10000"), "DEMO_LOCAL_TEST_ONLY"); }
    @Test void preservesVendorAndBalancesQuantity() {
        var s = FlashSalePricing.calculate(n("500000"), 3, demo(), n("30000"));
        assertThat(s.originalAmount()).isEqualByComparingTo("1500000");
        assertThat(s.commissionAmount()).isEqualByComparingTo("150000");
        assertThat(s.customerPayable()).isEqualByComparingTo("1410000");
        assertThat(s.vendorReceivable()).isEqualByComparingTo("1350000");
        assertThat(s.platformContribution()).isEqualByComparingTo("60000");
        assertThat(s.vendorReceivable().add(s.platformContribution())).isEqualByComparingTo(s.customerPayable());
        assertThat(s.maximumUnitSubsidy()).isEqualByComparingTo("35000");
    }
    @Test void missingPolicyHasNoFallback() {
        assertThatThrownBy(() -> FlashSalePricing.calculate(n("500000"),1,null,n("30000")))
                .hasMessageContaining("COMMISSION_POLICY_MISSING");
    }
    @Test void rejectsExcessSubsidyAndInvalidPolicy() {
        assertThatThrownBy(() -> FlashSalePricing.calculate(n("500000"),1,demo(),n("35000.01")))
                .hasMessageContaining("SUBSIDY_EXCEEDS_POLICY");
        var p = new Policy(UUID.randomUUID(), UUID.randomUUID(), 1,n("1.01"),n("1"),n("0"),n("0"),"DEMO");
        assertThatThrownBy(() -> FlashSalePricing.calculate(n("500000"),1,p,n("1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsInvalidMoneyAndQuantity() {
        assertThatThrownBy(() -> FlashSalePricing.calculate(n("10.001"),1,demo(),n("1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlashSalePricing.calculate(n("500000"),0,demo(),n("1"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void commissionRoundsOnceOnOriginalBookingAmount() {
        var p=new Policy(UUID.randomUUID(),UUID.randomUUID(),1,n("0.035"),n("1"),n("0"),n("0"),"DEMO_LOCAL_TEST_ONLY");
        var s=FlashSalePricing.calculate(n("333333.33"),3,p,n("5000"));
        assertThat(s.commissionAmount()).isEqualByComparingTo("35000");
        assertThat(s.vendorReceivable()).isEqualByComparingTo("964999.99");
        assertThat(s.customerPayable()).isEqualByComparingTo(s.vendorReceivable().add(s.platformContribution()));
    }
    @Test void missingSettlementSnapshotCannotInventTenPercent() {
        var context=com.danasea.backend.modules.settlement.domain.models.SubOrderCalculationContext.builder()
                .subtotalAmount(n("500000")).status(com.danasea.backend.modules.order.domain.models.SubOrderStatus.COMPLETED).build();
        assertThatThrownBy(()->new com.danasea.backend.modules.settlement.domain.services.SettlementCalculationEngine().calculateLineItem(context))
                .isInstanceOf(com.danasea.backend.modules.settlement.domain.exceptions.InvalidCommissionRateException.class);
    }
    @Test void domainCannotReactivateCancelledTerms() {
        var start=java.time.Instant.now();
        var sale=new com.danasea.backend.modules.flashsale.domain.FlashSale(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                "DRAFT",n("30000"),start,start.plusSeconds(3000),1,n("30000"),null,null,UUID.randomUUID());
        assertThatThrownBy(()->sale.cancelled().enabled(n("500000"),UUID.randomUUID()))
                .hasMessageContaining("FLASH_SALE_TERMS_IMMUTABLE");
    }
    @Test void exactTimeWindowAndExistingSettlementPreserveFunding() {
        var start=java.time.Instant.parse("2026-10-25T02:00:00Z"); var end=start.plusSeconds(47*60);
        var sale=new com.danasea.backend.modules.flashsale.domain.FlashSale(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                "ENABLED",n("30000"),start,end,1,n("30000"),n("500000"),UUID.randomUUID(),UUID.randomUUID());
        assertThat(sale.activeAt(start.minusNanos(1))).isFalse(); assertThat(sale.activeAt(start)).isTrue();
        assertThat(sale.activeAt(end.minusNanos(1))).isTrue(); assertThat(sale.activeAt(end)).isFalse();
        var context=com.danasea.backend.modules.settlement.domain.models.SubOrderCalculationContext.builder()
                .subtotalAmount(n("500000")).commissionBasisAmount(n("500000")).commissionRate(n("0.10"))
                .platformDiscountAmount(n("30000")).finalAmount(n("470000"))
                .status(com.danasea.backend.modules.order.domain.models.SubOrderStatus.COMPLETED).build();
        var engine=new com.danasea.backend.modules.settlement.domain.services.SettlementCalculationEngine();
        assertThat(engine.calculateLineItem(context).getNetAmount()).isEqualByComparingTo("450000");
        context.setRefundAmount(n("235000"));
        assertThat(engine.calculateLineItem(context).getNetAmount()).isEqualByComparingTo("225000");
        context.setRefundAmount(n("470000"));
        assertThat(engine.calculateLineItem(context).getNetAmount()).isEqualByComparingTo("0");
    }
}

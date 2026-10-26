package com.danasea.backend.modules.flashsale.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public final class FlashSalePricing {
    private FlashSalePricing() {}
    public record Policy(UUID id, UUID vendorId, int version, BigDecimal rate,
            BigDecimal maximumRatio, BigDecimal unitCost, BigDecimal minimumMargin, String provenance) {}
    public record Snapshot(BigDecimal originalUnitPrice, int quantity, BigDecimal originalAmount,
            UUID policyId, int policyVersion, BigDecimal commissionRate, BigDecimal commissionAmount,
            BigDecimal unitSubsidy, BigDecimal subsidyAmount, BigDecimal customerPayable,
            BigDecimal vendorReceivable, BigDecimal platformContribution, BigDecimal maximumUnitSubsidy) {}
    public static Snapshot calculate(BigDecimal price, int quantity, Policy policy, BigDecimal subsidy) {
        if (policy == null) throw new FlashSaleException("COMMISSION_POLICY_MISSING");
        validate(policy);
        money(price); money(subsidy);
        if (quantity <= 0 || quantity > 100 || price.signum() <= 0 || subsidy.signum() <= 0)
            throw new FlashSaleException("INVALID_FLASH_SALE_PRICE");
        // Round the unit subsidy ceiling down so quantity rounding cannot spend extra commission.
        BigDecimal commission = price.multiply(policy.rate()).setScale(2,RoundingMode.FLOOR);
        BigDecimal maximum = price.min(commission.multiply(policy.maximumRatio()))
                .min(commission.subtract(policy.unitCost()).subtract(policy.minimumMargin()))
                .max(BigDecimal.ZERO).setScale(2,RoundingMode.FLOOR);
        if (subsidy.compareTo(maximum)>0 || subsidy.compareTo(price)>=0)
            throw new FlashSaleException("SUBSIDY_EXCEEDS_POLICY");
        BigDecimal q=BigDecimal.valueOf(quantity);
        BigDecimal original=price.multiply(q).setScale(2);
        BigDecimal totalCommission=original.multiply(policy.rate()).setScale(2,RoundingMode.HALF_UP);
        BigDecimal funded=subsidy.multiply(q).setScale(2);
        money(original); money(totalCommission); money(funded);
        return new Snapshot(price.setScale(2),quantity,original,policy.id(),policy.version(),policy.rate(),
                totalCommission,subsidy.setScale(2),funded,original.subtract(funded),original.subtract(totalCommission),
                totalCommission.subtract(funded),maximum);
    }
    public static void validate(Policy p) {
        if(p==null) throw new FlashSaleException("COMMISSION_POLICY_MISSING");
        ratio(p.rate()); ratio(p.maximumRatio()); money(p.unitCost()); money(p.minimumMargin());
        if(p.vendorId()==null || p.unitCost().signum()<0 || p.minimumMargin().signum()<0 || p.provenance()==null
                || p.provenance().isBlank() || p.provenance().length()>200)
            throw new FlashSaleException("INVALID_COMMISSION_POLICY");
    }
    public static void money(BigDecimal n) {
        if(n==null || n.stripTrailingZeros().scale()>2 || n.precision()-n.scale()>17)
            throw new FlashSaleException("INVALID_MONEY_PRECISION");
    }
    private static void ratio(BigDecimal n) {
        if(n==null || n.signum()<0 || n.compareTo(BigDecimal.ONE)>0 || n.stripTrailingZeros().scale()>6)
            throw new FlashSaleException("INVALID_COMMISSION_POLICY");
    }
}

import type { PublicFlashSaleOfferDto, PublicVendorPromotionDto } from '../types/service';

export interface ResolvedVendorVoucher {
  code: string;
  discountType: string;
  discountValue: number;
  minOrderAmount: number;
  maxDiscountAmount: number | null;
  shortLabelVi: string;
  shortLabelEn: string;
  conditionLabelVi: string | null;
  conditionLabelEn: string | null;
  isEligibleForSingleUnit: boolean;
  singleUnitDiscountAmount: number;
  estimatedAfterVoucherPrice: number | null;
}

export interface ResolvedServicePromotion {
  basePrice: number;
  effectivePrice: number;
  strikethroughPrice: number | null;
  hasFlashSale: boolean;
  flashSale: PublicFlashSaleOfferDto | null;
  flashSalePercent: number;
  bestVendorVoucher: ResolvedVendorVoucher | null;
  hasAnyPromotion: boolean;
}

function formatCompactVnd(amount: number): string {
  if (amount >= 1000 && amount % 1000 === 0) {
    return `${Math.round(amount / 1000)}K`;
  }
  return `${Math.round(amount).toLocaleString('vi-VN')}₫`;
}

function isAvailableFlashSale(offer: PublicFlashSaleOfferDto | null | undefined): offer is PublicFlashSaleOfferDto {
  if (!offer) return false;
  if (offer.remainingQuota <= 0) return false;
  if (offer.availability && offer.availability !== 'AVAILABLE') return false;
  return Number(offer.salePrice) > 0 && Number(offer.originalPrice) > Number(offer.salePrice);
}

export function resolveBestFlashSale(
  flashSales?: PublicFlashSaleOfferDto[] | null,
  optionId?: string | null
): PublicFlashSaleOfferDto | null {
  if (!flashSales || flashSales.length === 0) return null;
  const available = flashSales.filter(isAvailableFlashSale);
  if (available.length === 0) return null;

  const pool = optionId
    ? available.filter((offer) => offer.serviceOptionId === optionId)
    : available;
  if (pool.length === 0) return null;

  return pool.reduce((best, curr) => {
    if (curr.salePrice !== best.salePrice) {
      return curr.salePrice < best.salePrice ? curr : best;
    }
    return curr.actualDiscountPercent > best.actualDiscountPercent ? curr : best;
  });
}

function evaluateVendorVoucher(
  effectivePrice: number,
  voucher: PublicVendorPromotionDto
): ResolvedVendorVoucher | null {
  const value = Number(voucher.discountValue) || 0;
  if (!voucher.code || value <= 0) return null;

  const minOrder = Math.max(0, Number(voucher.minOrderAmount) || 0);
  const maxCap = voucher.maxDiscountAmount != null && Number(voucher.maxDiscountAmount) > 0
    ? Number(voucher.maxDiscountAmount)
    : null;
  const isPercent = voucher.discountType === 'PERCENTAGE';

  const isEligibleForSingleUnit = effectivePrice >= minOrder;
  let singleUnitDiscount = 0;
  if (isEligibleForSingleUnit && effectivePrice > 0) {
    singleUnitDiscount = isPercent ? Math.round((effectivePrice * value) / 100) : value;
    if (maxCap !== null) {
      singleUnitDiscount = Math.min(singleUnitDiscount, maxCap);
    }
    singleUnitDiscount = Math.min(singleUnitDiscount, effectivePrice);
  }

  const estimatedAfterVoucherPrice =
    isEligibleForSingleUnit && singleUnitDiscount > 0 && effectivePrice - singleUnitDiscount > 0
      ? Math.round(effectivePrice - singleUnitDiscount)
      : null;

  const valueTag = isPercent ? `-${Math.round(value)}%` : `-${formatCompactVnd(value)}`;
  const shortLabelVi = `Mã ${voucher.code} ${valueTag}`;
  const shortLabelEn = `Code ${voucher.code} ${valueTag}`;

  const conditionLabelVi =
    !isEligibleForSingleUnit && minOrder > 0 ? `đơn từ ${formatCompactVnd(minOrder)}` : null;
  const conditionLabelEn =
    !isEligibleForSingleUnit && minOrder > 0 ? `min ${formatCompactVnd(minOrder)}` : null;

  return {
    code: voucher.code,
    discountType: voucher.discountType,
    discountValue: value,
    minOrderAmount: minOrder,
    maxDiscountAmount: maxCap,
    shortLabelVi,
    shortLabelEn,
    conditionLabelVi,
    conditionLabelEn,
    isEligibleForSingleUnit,
    singleUnitDiscountAmount: singleUnitDiscount,
    estimatedAfterVoucherPrice,
  };
}

export function resolveServicePromotion(input: {
  price: number;
  flashSales?: PublicFlashSaleOfferDto[] | null;
  vendorPromotions?: PublicVendorPromotionDto[] | null;
  optionId?: string | null;
}): ResolvedServicePromotion {
  const basePrice = Math.max(0, Number(input.price) || 0);
  const bestFlashSale = resolveBestFlashSale(input.flashSales, input.optionId);

  const hasFlashSale = bestFlashSale !== null;
  const effectivePrice = hasFlashSale
    ? Math.min(basePrice > 0 ? basePrice : bestFlashSale.salePrice, bestFlashSale.salePrice)
    : basePrice;

  const strikethroughPrice = hasFlashSale
    ? Math.max(bestFlashSale.originalPrice, basePrice)
    : null;

  const flashSalePercent = hasFlashSale
    ? Math.max(
        1,
        Math.round(
          bestFlashSale.actualDiscountPercent > 0
            ? bestFlashSale.actualDiscountPercent
            : ((bestFlashSale.originalPrice - bestFlashSale.salePrice) / bestFlashSale.originalPrice) * 100
        )
      )
    : 0;

  let bestVendorVoucher: ResolvedVendorVoucher | null = null;
  if (input.vendorPromotions && input.vendorPromotions.length > 0) {
    const candidates = input.vendorPromotions
      .map((voucher) => evaluateVendorVoucher(effectivePrice, voucher))
      .filter((item): item is ResolvedVendorVoucher => item !== null);

    if (candidates.length > 0) {
      candidates.sort((a, b) => {
        if (a.isEligibleForSingleUnit !== b.isEligibleForSingleUnit) {
          return a.isEligibleForSingleUnit ? -1 : 1;
        }
        if (b.singleUnitDiscountAmount !== a.singleUnitDiscountAmount) {
          return b.singleUnitDiscountAmount - a.singleUnitDiscountAmount;
        }
        return b.discountValue - a.discountValue;
      });
      bestVendorVoucher = candidates[0];
    }
  }

  return {
    basePrice,
    effectivePrice,
    strikethroughPrice,
    hasFlashSale,
    flashSale: bestFlashSale,
    flashSalePercent,
    bestVendorVoucher,
    hasAnyPromotion: hasFlashSale || bestVendorVoucher !== null,
  };
}

import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveServicePromotion, resolveBestFlashSale } from '../servicePromotion.ts';

test('returns base price when no promotions exist', () => {
  const result = resolveServicePromotion({ price: 650000 });
  assert.equal(result.effectivePrice, 650000);
  assert.equal(result.strikethroughPrice, null);
  assert.equal(result.hasFlashSale, false);
  assert.equal(result.bestVendorVoucher, null);
  assert.equal(result.hasAnyPromotion, false);
});

test('computes Flash Sale + stackable Vendor Voucher estimate when single ticket meets minOrderAmount', () => {
  const result = resolveServicePromotion({
    price: 650000,
    flashSales: [
      {
        flashSaleId: 'fs-1',
        serviceId: 'svc-1',
        serviceOptionId: 'opt-1',
        serviceName: 'Mô tô nước – Kajin Vina',
        originalPrice: 650000,
        salePrice: 500000,
        actualDiscountAmount: 150000,
        actualDiscountPercent: 23.0769,
        startsAt: '2026-11-01T00:00:00Z',
        endsAt: '2026-11-30T23:59:59Z',
        remainingQuota: 20,
        availability: 'AVAILABLE',
        currency: 'VND',
      },
    ],
    vendorPromotions: [
      {
        id: 'v-1',
        code: 'KAJIN20K',
        vendorId: 'ven-1',
        discountType: 'FIXED',
        discountValue: 20000,
        minOrderAmount: 250000,
        maxDiscountAmount: 20000,
      },
    ],
  });

  assert.equal(result.hasFlashSale, true);
  assert.equal(result.effectivePrice, 500000);
  assert.equal(result.strikethroughPrice, 650000);
  assert.equal(result.flashSalePercent, 23);
  assert.ok(result.bestVendorVoucher);
  assert.equal(result.bestVendorVoucher.code, 'KAJIN20K');
  assert.equal(result.bestVendorVoucher.isEligibleForSingleUnit, true);
  assert.equal(result.bestVendorVoucher.estimatedAfterVoucherPrice, 480000);
  assert.equal(result.bestVendorVoucher.shortLabelVi, 'Mã KAJIN20K -20K');
});

test('keeps honest base price and shows min-order condition without subtracting when 1 ticket is below minOrderAmount', () => {
  const result = resolveServicePromotion({
    price: 150000,
    vendorPromotions: [
      {
        id: 'v-2',
        code: 'DANABEACH10',
        vendorId: 'ven-2',
        discountType: 'PERCENTAGE',
        discountValue: 10,
        minOrderAmount: 400000,
        maxDiscountAmount: 80000,
      },
    ],
  });

  assert.equal(result.hasFlashSale, false);
  assert.equal(result.effectivePrice, 150000);
  assert.equal(result.strikethroughPrice, null);
  assert.ok(result.bestVendorVoucher);
  assert.equal(result.bestVendorVoucher.isEligibleForSingleUnit, false);
  assert.equal(result.bestVendorVoucher.estimatedAfterVoucherPrice, null);
  assert.equal(result.bestVendorVoucher.conditionLabelVi, 'đơn từ 400K');
});

test('respects maxDiscountAmount cap for percentage vendor vouchers', () => {
  const result = resolveServicePromotion({
    price: 1000000,
    vendorPromotions: [
      {
        id: 'v-3',
        code: 'DANABEACH10',
        vendorId: 'ven-2',
        discountType: 'PERCENTAGE',
        discountValue: 10,
        minOrderAmount: 400000,
        maxDiscountAmount: 80000,
      },
    ],
  });

  assert.ok(result.bestVendorVoucher);
  assert.equal(result.bestVendorVoucher.singleUnitDiscountAmount, 80000);
  assert.equal(result.bestVendorVoucher.estimatedAfterVoucherPrice, 920000);
});

test('ignores sold-out Flash Sale offers and matches optionId when specified', () => {
  const offers = [
    {
      flashSaleId: 'fs-sold',
      serviceId: 'svc-1',
      serviceOptionId: 'opt-1',
      serviceName: 'Sold out',
      originalPrice: 650000,
      salePrice: 400000,
      actualDiscountAmount: 250000,
      actualDiscountPercent: 38.46,
      startsAt: '2026-11-01T00:00:00Z',
      endsAt: '2026-11-30T23:59:59Z',
      remainingQuota: 0,
      availability: 'SOLD_OUT',
      currency: 'VND',
    },
    {
      flashSaleId: 'fs-live',
      serviceId: 'svc-1',
      serviceOptionId: 'opt-2',
      serviceName: 'Live',
      originalPrice: 650000,
      salePrice: 500000,
      actualDiscountAmount: 150000,
      actualDiscountPercent: 23.08,
      startsAt: '2026-11-01T00:00:00Z',
      endsAt: '2026-11-30T23:59:59Z',
      remainingQuota: 5,
      availability: 'AVAILABLE',
      currency: 'VND',
    },
  ];

  assert.equal(resolveBestFlashSale(offers, 'opt-1'), null);
  assert.equal(resolveBestFlashSale(offers, 'opt-2')?.flashSaleId, 'fs-live');
});

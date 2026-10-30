import type { MOCK_ADMIN_VENDORS } from '../../../data/adminMockData';
import type { BadgeTier, VerificationStatus } from '../../../types';

export type VendorRecord = typeof MOCK_ADMIN_VENDORS[number];
export type VendorDecision = {
  vendorId: string;
  kind: 'approve' | 'reject';
  tier: BadgeTier;
  note: string;
  at: string;
};
export type VendorReviewEvent = VendorDecision & { previousStatus: VerificationStatus };
export type VendorReviewState = { vendors: VendorRecord[]; timeline: VendorReviewEvent[] };

export function isTestTaxCode(taxCode?: string): boolean {
  if (!taxCode) return true;
  return taxCode.startsWith('TEST-DS-') || taxCode.startsWith('TEST-');
}

export function formatTaxCode(taxCode?: string): string {
  if (!taxCode || isTestTaxCode(taxCode)) {
    return 'Chưa cập nhật';
  }
  return taxCode;
}

export function filterVendors(vendors: VendorRecord[], query: string, status: string, tier: string) {
  const search = query.trim().toLocaleLowerCase('vi');
  return vendors.filter(vendor => {
    const rawTax = vendor.taxCode || '';
    const formattedTax = formatTaxCode(rawTax).toLocaleLowerCase('vi');
    const matchesSearch = !search ||
      vendor.businessName.toLocaleLowerCase('vi').includes(search) ||
      rawTax.toLocaleLowerCase('vi').includes(search) ||
      formattedTax.includes(search);
    const matchesStatus = status === 'all' || vendor.verificationStatus === status;
    const matchesTier = tier === 'all' || vendor.badgeTier === tier;
    return matchesSearch && matchesStatus && matchesTier;
  });
}

export function applyVendorDecision(state: VendorReviewState, decision: VendorDecision, demo: boolean): VendorReviewState {
  const vendor = state.vendors.find(item => item.id === decision.vendorId);
  if (!demo || !vendor) return state;
  return {
    vendors: state.vendors.map(item => item.id !== vendor.id ? item : {
      ...item,
      verificationStatus: decision.kind === 'approve' ? 'VERIFIED' : 'REJECTED',
      badgeTier: decision.kind === 'approve' ? decision.tier : item.badgeTier,
      verifiedBy: 'Admin xem thử (cục bộ)',
      verifiedAt: decision.at,
    }),
    timeline: [...state.timeline, { ...decision, note: decision.note.trim(), previousStatus: vendor.verificationStatus }],
  };
}

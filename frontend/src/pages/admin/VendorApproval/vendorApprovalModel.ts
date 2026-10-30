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

export function filterVendors(vendors: VendorRecord[], query: string, status: string, tier: string) {
  const search = query.trim().toLocaleLowerCase('vi');
  return vendors.filter(vendor =>
    (vendor.businessName.toLocaleLowerCase('vi').includes(search) || vendor.taxCode.includes(search)) &&
    (status === 'all' || vendor.verificationStatus === status) &&
    (tier === 'all' || vendor.badgeTier === tier));
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

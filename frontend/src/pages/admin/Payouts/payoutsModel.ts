import type { MOCK_ADMIN_PAYOUTS } from '../../../data/adminMockData';
import type { PayoutRequestStatus } from '../../../types';

export type PayoutExtended = (typeof MOCK_ADMIN_PAYOUTS)[0] & {
  bankReference?: string | null;
  rejectionReason?: string | null;
  adminNote?: string | null;
  note?: string | null;
};
export type PayoutStatusFilter = 'all' | 'REQUESTED' | 'APPROVED' | 'PAID' | 'REJECTED';

export function formatDisplayCode(id: string, prefix = 'PAY'): string {
  if (!id) return '';
  if (id.startsWith('PAY-') || id.startsWith('SETTLE-') || id.startsWith('ORD-') || id.startsWith('STL-') || id.startsWith('SUB-') || id.startsWith('DISP-')) return id;
  const clean = id.replace(/-/g, '');
  const isSeed =
    clean.startsWith('00010000') ||
    clean.startsWith('00060000') ||
    clean.startsWith('00000000') ||
    new Set(clean.slice(0, 8).split('')).size <= 2;
  const code = isSeed
    ? (clean.slice(0, 4) + clean.slice(-4))
    : clean.substring(0, 8);
  return `${prefix}-${code.toUpperCase()}`;
}

export function filterPayouts(
  payouts: PayoutExtended[],
  query: string,
  status: PayoutStatusFilter
): PayoutExtended[] {
  const q = query.trim().toLowerCase();
  return payouts.filter(p => {
    const matchesStatus = status === 'all' || p.status === status;
    const matchesQuery =
      !q ||
      p.id.toLowerCase().includes(q) ||
      formatDisplayCode(p.id, 'PAY').toLowerCase().includes(q) ||
      (p.vendorName?.toLowerCase().includes(q) ?? false) ||
      (p.bankAccountNumber?.toLowerCase().includes(q) ?? false) ||
      (p.bankAccountHolder?.toLowerCase().includes(q) ?? false) ||
      (p.periodText?.toLowerCase().includes(q) ?? false);

    return matchesStatus && matchesQuery;
  });
}

export function calculatePayoutMetrics(payouts: PayoutExtended[]) {
  const totalCount = payouts.length;
  let pendingCount = 0;
  let approvedCount = 0;
  let paidCount = 0;
  let rejectedCount = 0;
  let totalPendingAmount = 0;
  let totalApprovedAmount = 0;
  let totalPaidAmount = 0;

  for (const p of payouts) {
    if (p.status === 'REQUESTED') {
      pendingCount++;
      totalPendingAmount += p.amount;
    } else if (p.status === 'APPROVED') {
      approvedCount++;
      totalApprovedAmount += p.amount;
    } else if (p.status === 'PAID') {
      paidCount++;
      totalPaidAmount += p.amount;
    } else if (p.status === 'REJECTED') {
      rejectedCount++;
    }
  }

  return {
    totalCount,
    pendingCount,
    approvedCount,
    paidCount,
    rejectedCount,
    totalPendingAmount,
    totalApprovedAmount,
    totalPaidAmount,
  };
}

export function approvePayout(
  payouts: PayoutExtended[],
  id: string,
  bankRef: string,
  processedBy: string,
  demo: boolean
): { updatedList: PayoutExtended[]; targetPayout: PayoutExtended | null } {
  const target = payouts.find(p => p.id === id);
  if (!demo || !target) {
    return { updatedList: payouts, targetPayout: null };
  }

  const updated: PayoutExtended = {
    ...target,
    status: 'PAID' as PayoutRequestStatus,
    processedBy: processedBy || 'Ban Quản trị DANASEA',
    processedAt: new Date().toISOString(),
  };

  return {
    updatedList: payouts.map(p => (p.id === id ? updated : p)),
    targetPayout: updated,
  };
}

export function rejectPayout(
  payouts: PayoutExtended[],
  id: string,
  reason: string,
  processedBy: string,
  demo: boolean
): { updatedList: PayoutExtended[]; targetPayout: PayoutExtended | null } {
  const target = payouts.find(p => p.id === id);
  if (!demo || !target) {
    return { updatedList: payouts, targetPayout: null };
  }

  const updated: PayoutExtended = {
    ...target,
    status: 'REJECTED' as PayoutRequestStatus,
    processedBy: processedBy || 'Ban Quản trị DANASEA',
    processedAt: new Date().toISOString(),
  };

  return {
    updatedList: payouts.map(p => (p.id === id ? updated : p)),
    targetPayout: updated,
  };
}

export function exportPayoutsCsv(payouts: PayoutExtended[]): string {
  const headers = ['Mã Lệnh', 'Đối Tác', 'Kỳ Đối Soát', 'Số Đơn', 'Số Tiền (VNĐ)', 'Ngân Hàng', 'Số Tài Khoản', 'Chủ Tài Khoản', 'Trạng Thái', 'Người Xử Lý'].join(',');
  const rows = payouts.map(p => [
    `"${p.id}"`,
    `"${(p.vendorName ?? '').replace(/"/g, '""')}"`,
    `"${p.periodText ?? ''}"`,
    p.totalOrdersCount,
    p.amount,
    `"${p.bankName ?? ''}"`,
    `"'${p.bankAccountNumber ?? ''}"`,
    `"${p.bankAccountHolder ?? ''}"`,
    `"${p.status}"`,
    `"${p.processedBy || ''}"`,
  ].join(','));

  return [headers, ...rows].join('\n');
}

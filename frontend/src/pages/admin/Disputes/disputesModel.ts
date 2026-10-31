import type { MOCK_ADMIN_DISPUTES } from '../../../data/adminMockData';
import type { DisputeStatus } from '../../../types';

export type DisputeExtended = (typeof MOCK_ADMIN_DISPUTES)[0];
export type DisputeStatusFilter = 'all' | 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';
export type DisputeCategoryFilter = 'all' | 'SERVICE_QUALITY' | 'WEATHER_CANCELLATION' | 'OVERCHARGING' | 'SAFETY_ISSUE';

export const DISPUTE_CATEGORY_LABELS: Record<string, string> = {
  SERVICE_QUALITY: 'Chất lượng dịch vụ',
  WEATHER_CANCELLATION: 'Thời tiết & Hủy tour',
  OVERCHARGING: 'Phụ thu không đúng',
  SAFETY_ISSUE: 'An toàn mặt nước',
};

export function filterDisputes(
  disputes: DisputeExtended[],
  query: string,
  status: DisputeStatusFilter,
  category: DisputeCategoryFilter = 'all'
): DisputeExtended[] {
  const q = query.trim().toLowerCase();
  return disputes.filter(d => {
    const matchesStatus =
      status === 'all' ||
      (status === 'OPEN' ? d.status === 'OPEN' || d.status === 'IN_PROGRESS' : d.status === status);

    const matchesCategory = category === 'all' || d.category === category;

    const matchesQuery =
      !q ||
      d.id.toLowerCase().includes(q) ||
      (d.subOrderId && d.subOrderId.toLowerCase().includes(q)) ||
      (d.serviceName && d.serviceName.toLowerCase().includes(q)) ||
      (d.customerName && d.customerName.toLowerCase().includes(q)) ||
      (d.vendorName && d.vendorName.toLowerCase().includes(q)) ||
      (d.description && d.description.toLowerCase().includes(q));

    return matchesStatus && matchesCategory && matchesQuery;
  });
}

export function calculateDisputeMetrics(disputes: DisputeExtended[]) {
  const totalCount = disputes.length;
  let openCount = 0;
  let inProgressCount = 0;
  let resolvedCount = 0;
  let totalDisputedAmount = 0;

  for (const d of disputes) {
    if (d.status === 'OPEN') {
      openCount++;
      totalDisputedAmount += d.orderAmount || 0;
    } else if (d.status === 'IN_PROGRESS') {
      inProgressCount++;
      totalDisputedAmount += d.orderAmount || 0;
    } else if (d.status === 'RESOLVED') {
      resolvedCount++;
    }
  }

  return {
    totalCount,
    openCount,
    inProgressCount,
    pendingActionCount: openCount + inProgressCount,
    resolvedCount,
    totalDisputedAmount,
  };
}

export function resolveDisputeWithRefund(
  disputes: DisputeExtended[],
  id: string,
  refundPercentage: number,
  note: string,
  resolvedBy: string,
  demo: boolean
): { updatedList: DisputeExtended[]; targetDispute: DisputeExtended | null; refundAmount: number } {
  const target = disputes.find(d => d.id === id);
  if (!target) {
    return { updatedList: disputes, targetDispute: null, refundAmount: 0 };
  }

  const orderAmt = target.orderAmount || 0;
  const refundAmount = Math.round((orderAmt * refundPercentage) / 100);

  if (!demo) {
    return { updatedList: disputes, targetDispute: target, refundAmount };
  }

  const updated: DisputeExtended = {
    ...target,
    status: 'RESOLVED' as DisputeStatus,
    resolutionNote: `${note.trim()} (Đã phê duyệt hoàn ${refundPercentage}% tương ứng ${refundAmount.toLocaleString('vi-VN')} đ)`,
    resolvedBy: resolvedBy || 'Vũ Hải Đăng (Admin)',
    resolvedAt: new Date().toISOString(),
  };

  return {
    updatedList: disputes.map(d => (d.id === id ? updated : d)),
    targetDispute: updated,
    refundAmount,
  };
}

export function dismissDispute(
  disputes: DisputeExtended[],
  id: string,
  reason: string,
  resolvedBy: string,
  demo: boolean
): { updatedList: DisputeExtended[]; targetDispute: DisputeExtended | null } {
  const target = disputes.find(d => d.id === id);
  if (!demo || !target) {
    return { updatedList: disputes, targetDispute: null };
  }

  const updated: DisputeExtended = {
    ...target,
    status: 'RESOLVED' as DisputeStatus,
    resolutionNote: `Bác bỏ khiếu nại: ${reason.trim()}`,
    resolvedBy: resolvedBy || 'Vũ Hải Đăng (Admin)',
    resolvedAt: new Date().toISOString(),
  };

  return {
    updatedList: disputes.map(d => (d.id === id ? updated : d)),
    targetDispute: updated,
  };
}

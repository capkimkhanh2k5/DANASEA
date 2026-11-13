import type { PartnerApplicationResponse, PartnerApplicationStatus } from '../../../types/partnerApplication';

export type RoleApplicationRecord = PartnerApplicationResponse;

export interface RoleApplicationFilterOptions {
  query: string;
  status: string;
}

export function filterApplications(
  applications: RoleApplicationRecord[],
  query: string,
  status: string
): RoleApplicationRecord[] {
  const search = query.trim().toLocaleLowerCase('vi');
  return applications.filter(app => {
    const matchesSearch =
      !search ||
      app.businessName.toLocaleLowerCase('vi').includes(search) ||
      (app.taxCode && app.taxCode.toLocaleLowerCase('vi').includes(search)) ||
      (app.bankAccountHolder && app.bankAccountHolder.toLocaleLowerCase('vi').includes(search)) ||
      (app.bankAccountNumber && app.bankAccountNumber.includes(search)) ||
      (app.address && app.address.toLocaleLowerCase('vi').includes(search));

    const matchesStatus = status === 'all' || app.status === status;
    return matchesSearch && matchesStatus;
  });
}

export function formatDate(value?: string | null): string {
  if (!value) return 'Chưa có thông tin';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleString('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
}

export function getStatusTone(status: PartnerApplicationStatus): 'amber' | 'teal' | 'rose' {
  switch (status) {
    case 'PENDING':
      return 'amber';
    case 'APPROVED':
      return 'teal';
    case 'REJECTED':
      return 'rose';
  }
}

export function getStatusLabel(status: PartnerApplicationStatus): string {
  switch (status) {
    case 'PENDING':
      return 'Chờ duyệt';
    case 'APPROVED':
      return 'Đã duyệt';
    case 'REJECTED':
      return 'Từ chối';
  }
}

export const MOCK_PARTNER_APPLICATIONS: RoleApplicationRecord[] = [
  {
    id: 'app-demo-01',
    userId: 'user-cst-01',
    businessName: 'Công ty TNHH Du Thuyền Sông Hàn Đà Nẵng',
    taxCode: '0401889922',
    address: 'Bến du thuyền Sông Hàn, Đường Bạch Đằng, P. Hải Châu 1, Q. Hải Châu, TP. Đà Nẵng',
    bankAccountNumber: '0071000889922',
    bankName: 'Ngân hàng TMCP Ngoại Thương Việt Nam (Vietcombank)',
    bankAccountHolder: 'CONG TY TNHH DU THUYEN SONG HAN DA NANG',
    status: 'PENDING',
    rejectionReason: null,
    reviewedBy: null,
    reviewedAt: null,
    createdAt: '2026-11-10T08:30:00Z',
    updatedAt: '2026-11-10T08:30:00Z',
  },
  {
    id: 'app-demo-02',
    userId: 'user-cst-02',
    businessName: 'Hộ KD Cano Cao Tốc Biển Non Nước',
    taxCode: '8392019283',
    address: 'Bãi biển Non Nước, P. Hòa Hải, Q. Ngũ Hành Sơn, TP. Đà Nẵng',
    bankAccountNumber: '1903678129033',
    bankName: 'Ngân hàng TMCP Kỹ Thương Việt Nam (Techcombank)',
    bankAccountHolder: 'NGUYEN VAN TIEN',
    status: 'APPROVED',
    rejectionReason: null,
    reviewedBy: 'Quản trị viên DANASEA',
    reviewedAt: '2026-11-09T14:15:00Z',
    createdAt: '2026-11-08T09:00:00Z',
    updatedAt: '2026-11-09T14:15:00Z',
  },
  {
    id: 'app-demo-03',
    userId: 'user-cst-03',
    businessName: 'Hợp Tác Xã Dịch Vụ Lặn Biển Sơn Trà',
    taxCode: '0402194821',
    address: 'Bãi Lở, Bán đảo Sơn Trà, P. Thọ Quang, Q. Sơn Trà, TP. Đà Nẵng',
    bankAccountNumber: '081293847291',
    bankName: 'Ngân hàng TMCP Quân Đội (MB Bank)',
    bankAccountHolder: 'HTX DICH VU LAN BIEN SON TRA',
    status: 'REJECTED',
    rejectionReason: 'Địa chỉ đăng ký hộ kinh doanh chưa khớp với giấy chứng nhận đăng ký kinh doanh cung cấp; tài khoản ngân hàng chưa cung cấp văn bản xác nhận ủy quyền của HTX.',
    reviewedBy: 'Quản trị viên DANASEA',
    reviewedAt: '2026-11-07T16:45:00Z',
    createdAt: '2026-11-06T11:20:00Z',
    updatedAt: '2026-11-07T16:45:00Z',
  },
  {
    id: 'app-demo-04',
    userId: 'user-cst-04',
    businessName: 'CLB Chèo SUP Bãi Biển Mỹ Khê',
    taxCode: '0403321980',
    address: 'Công viên Biển Đông, Đường Võ Nguyên Giáp, P. Phước Mỹ, Q. Sơn Trà, TP. Đà Nẵng',
    bankAccountNumber: '201928374619',
    bankName: 'Ngân hàng TMCP Tiên Phong (TPBank)',
    bankAccountHolder: 'LE THI MINH NGUYET',
    status: 'PENDING',
    rejectionReason: null,
    reviewedBy: null,
    reviewedAt: null,
    createdAt: '2026-11-11T10:05:00Z',
    updatedAt: '2026-11-11T10:05:00Z',
  },
];

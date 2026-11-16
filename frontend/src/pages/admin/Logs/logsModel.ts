import type { AuditLog } from '../../../types';

export interface AdminAuditLogItem extends AuditLog {
  actorName?: string;
  actorEmail?: string;
}

export type LogActionFilter =
  | 'ALL'
  // Backend Active Actions
  | 'RECONCILE_PAYMENT_PENDING'
  | 'RECONCILE_PAYMENT_FAILED'
  | 'RECONCILE_PAYMENT_SUCCESS'
  | 'RECONCILE_PAYMENT_SKIPPED'
  | 'PARTNER_APPLICATION_APPROVAL'
  | 'PARTNER_APPLICATION_REJECTION'
  | 'VENDOR_APPROVAL'
  | 'VENDOR_REJECTION'
  | 'VENDOR_DOCUMENT_APPROVED'
  | 'VENDOR_DOCUMENT_REJECTED'
  | 'VENDOR_PROFILE_RESUBMITTED'
  | 'SERVICE_APPROVED'
  | 'SERVICE_REJECTED'
  | 'FINALIZE_SETTLEMENT'
  | 'PAYOUT_REQUEST_APPROVED'
  | 'PAYOUT_REQUEST_REJECTED'
  | 'PAYOUT_REQUEST_PAID'
  | 'DISPUTE_RESOLVED'
  | 'SYSTEM_CONFIG_UPDATED'
  | 'COMMISSION_RULE_UPDATED'
  | 'REFUND_MANUALLY_COMPLETED'
  | 'USER_LOCKED'
  | 'USER_UNLOCKED'
  | 'CHANGE_PASSWORD'
  | 'PASSWORD_RESET'
  | 'FLASH_SALE_CREATED'
  | 'FLASH_SALE_APPROVED'
  | 'FLASH_SALE_CANCELLED'
  | 'COMPLETE_SUB_ORDER'
  | 'COMPLETE_SUB_ORDER_NO_SHOW'
  | 'DISPATCH_OVERDUE_SLOT'
  | 'EARLY_DISPATCH_SLOT'
  // Planned Actions (Chưa có log BE)
  | 'VERIFY_VENDOR'
  | 'PROCESS_REFUND'
  | 'UPDATE_PROMOTION'
  | 'CREATE_PROMOTION'
  | 'ADMIN_LOGIN'
  // Legacy / Planned Aliases
  | 'APPROVE_PAYOUT'
  | 'REJECT_PAYOUT'
  | 'MARK_PAYOUT_PAID'
  | 'RESOLVE_DISPUTE'
  | 'UPDATE_SYSTEM_CONFIG'
  | 'APPROVE_VENDOR'
  | 'REJECT_VENDOR'
  | 'VENDOR_DOCUMENT_APPROVE'
  | 'VENDOR_DOCUMENT_REJECT'
  | 'GENERATE_SETTLEMENT'
  | 'LOCK_USER_ACCOUNT'
  | 'UNLOCK_USER_ACCOUNT'
  | string;

export type LogEntityFilter =
  | 'ALL'
  // Backend Uppercase Entities
  | 'PAYMENT'
  | 'PARTNER_APPLICATION'
  | 'VENDOR'
  | 'VENDOR_DOCUMENT'
  | 'SERVICE'
  | 'SETTLEMENT'
  | 'PAYOUT_REQUEST'
  | 'DISPUTE'
  | 'SYSTEM_CONFIG'
  | 'COMMISSION_RULE'
  | 'REFUND'
  | 'USER'
  | 'ORDER'
  | 'MASTER_ORDER'
  | 'SUB_ORDER'
  | 'FLASH_SALE'
  // Planned Entities
  | 'PROMOTION'
  // Legacy PascalCase aliases
  | 'PayoutRequest'
  | 'Vendor'
  | 'VendorDocument'
  | 'Dispute'
  | 'SystemConfig'
  | 'User'
  | 'Promotion'
  | 'Order'
  | 'Refund'
  | 'Settlement'
  | 'Payment'
  | string;

export interface ActionMeta {
  label: string;
  tone: 'teal' | 'amber' | 'rose' | 'neutral';
  description: string;
}

export const ACTION_METAS: Record<string, ActionMeta> = {
  // Backend Active Actions
  RECONCILE_PAYMENT_PENDING: {
    label: 'Đối soát thanh toán (Chờ xác nhận)',
    tone: 'amber',
    description: 'Hệ thống tự động kiểm tra trạng thái giao dịch thanh toán đang chờ trên cổng thanh toán.',
  },
  RECONCILE_PAYMENT_FAILED: {
    label: 'Đối soát thanh toán thất bại',
    tone: 'rose',
    description: 'Giao dịch thanh toán không thể đối soát thành công qua cổng thanh toán.',
  },
  RECONCILE_PAYMENT_SUCCESS: {
    label: 'Đối soát thanh toán thành công',
    tone: 'teal',
    description: 'Giao dịch thanh toán đã được cổng thanh toán xác nhận thành công.',
  },
  RECONCILE_PAYMENT_SKIPPED: {
    label: 'Bỏ qua đối soát thanh toán',
    tone: 'neutral',
    description: 'Giao dịch thanh toán không thỏa điều kiện đối soát hoặc đã hoàn tất trước đó.',
  },
  PARTNER_APPLICATION_APPROVAL: {
    label: 'Duyệt đơn đăng ký đối tác',
    tone: 'teal',
    description: 'Hồ sơ xin gia nhập đối tác cung cấp dịch vụ đã được phê duyệt.',
  },
  PARTNER_APPLICATION_REJECTION: {
    label: 'Từ chối đơn đăng ký đối tác',
    tone: 'rose',
    description: 'Hồ sơ xin gia nhập đối tác cung cấp dịch vụ đã bị từ chối.',
  },
  VENDOR_APPROVAL: {
    label: 'Phê duyệt hồ sơ đối tác',
    tone: 'teal',
    description: 'Quản trị viên phê duyệt hồ sơ đối tác hoạt động trên nền tảng.',
  },
  VENDOR_REJECTION: {
    label: 'Từ chối hồ sơ đối tác',
    tone: 'rose',
    description: 'Quản trị viên từ chối hồ sơ đối tác chưa đạt tiêu chuẩn.',
  },
  VENDOR_DOCUMENT_APPROVED: {
    label: 'Duyệt chứng từ đối tác',
    tone: 'teal',
    description: 'Thẩm định và chấp thuận chứng từ pháp lý hoặc chứng nhận của đối tác.',
  },
  VENDOR_DOCUMENT_REJECTED: {
    label: 'Từ chối chứng từ đối tác',
    tone: 'rose',
    description: 'Từ chối chứng từ pháp lý của đối tác do chưa đạt tiêu chuẩn.',
  },
  VENDOR_PROFILE_RESUBMITTED: {
    label: 'Nộp lại hồ sơ đối tác',
    tone: 'neutral',
    description: 'Đối tác đã cập nhật và nộp lại hồ sơ thông tin thẩm định.',
  },
  SERVICE_APPROVED: {
    label: 'Phê duyệt dịch vụ',
    tone: 'teal',
    description: 'Quản trị viên phê duyệt dịch vụ du lịch trải nghiệm lên sàn.',
  },
  SERVICE_REJECTED: {
    label: 'Từ chối dịch vụ',
    tone: 'rose',
    description: 'Quản trị viên từ chối dịch vụ chưa đáp ứng tiêu chuẩn chất lượng/an toàn.',
  },
  FINALIZE_SETTLEMENT: {
    label: 'Chốt kỳ quyết toán',
    tone: 'teal',
    description: 'Quản trị viên tổng hợp đối soát và chốt chuyển doanh thu kỳ hạn cho đối tác.',
  },
  PAYOUT_REQUEST_APPROVED: {
    label: 'Phê duyệt yêu cầu chi trả',
    tone: 'teal',
    description: 'Quản trị viên phê duyệt yêu cầu chi trả / rút tiền của đối tác.',
  },
  PAYOUT_REQUEST_REJECTED: {
    label: 'Từ chối yêu cầu chi trả',
    tone: 'rose',
    description: 'Quản trị viên từ chối yêu cầu chi trả do thông tin không hợp lệ.',
  },
  PAYOUT_REQUEST_PAID: {
    label: 'Đánh dấu đã chi trả',
    tone: 'teal',
    description: 'Quản trị viên xác nhận đã hoàn tất chuyển tiền chi trả cho đối tác.',
  },
  DISPUTE_RESOLVED: {
    label: 'Giải quyết khiếu nại',
    tone: 'teal',
    description: 'Quản trị viên đưa ra phán quyết và giải quyết khiếu nại của khách hàng.',
  },
  SYSTEM_CONFIG_UPDATED: {
    label: 'Cập nhật cấu hình hệ thống',
    tone: 'amber',
    description: 'Quản trị viên cập nhật giá trị tham số cấu hình vận hành toàn sàn.',
  },
  COMMISSION_RULE_UPDATED: {
    label: 'Cập nhật quy tắc hoa hồng',
    tone: 'amber',
    description: 'Quản trị viên điều chỉnh tỷ lệ hoặc điều kiện quy tắc hoa hồng sàn.',
  },
  REFUND_MANUALLY_COMPLETED: {
    label: 'Hoàn tiền thủ công thành công',
    tone: 'teal',
    description: 'Giao dịch hoàn tiền đã được xử lý hoàn tất thủ công bởi quản trị viên.',
  },
  USER_LOCKED: {
    label: 'Khóa tài khoản',
    tone: 'rose',
    description: 'Tạm dừng hoặc vô hiệu hóa tài khoản vi phạm chính sách.',
  },
  USER_UNLOCKED: {
    label: 'Mở khóa tài khoản',
    tone: 'teal',
    description: 'Khôi phục quyền truy cập cho tài khoản người dùng sau khi giải tỏa vi phạm.',
  },
  CHANGE_PASSWORD: {
    label: 'Thay đổi mật khẩu',
    tone: 'neutral',
    description: 'Người dùng thực hiện đổi mật khẩu tài khoản thành công.',
  },
  PASSWORD_RESET: {
    label: 'Đặt lại mật khẩu',
    tone: 'amber',
    description: 'Yêu cầu cấp lại mật khẩu xác thực qua email hoặc quản trị viên.',
  },
  FLASH_SALE_CREATED: {
    label: 'Tạo chương trình Flash Sale',
    tone: 'neutral',
    description: 'Khởi tạo chiến dịch ưu đãi giờ vàng hoặc Flash Sale sản phẩm.',
  },
  FLASH_SALE_APPROVED: {
    label: 'Phê duyệt Flash Sale',
    tone: 'teal',
    description: 'Quản trị viên chấp thuận kích hoạt chương trình Flash Sale.',
  },
  FLASH_SALE_CANCELLED: {
    label: 'Hủy Flash Sale',
    tone: 'rose',
    description: 'Hủy bỏ chiến dịch Flash Sale trước thời hạn.',
  },
  COMPLETE_SUB_ORDER: {
    label: 'Hoàn tất đơn dịch vụ',
    tone: 'teal',
    description: 'Xác nhận khách hàng đã hoàn thành trải nghiệm dịch vụ.',
  },
  COMPLETE_SUB_ORDER_NO_SHOW: {
    label: 'Đơn dịch vụ khách vắng mặt (No-show)',
    tone: 'amber',
    description: 'Ghi nhận khách không đến trải nghiệm dịch vụ đúng hẹn.',
  },
  DISPATCH_OVERDUE_SLOT: {
    label: 'Điều phối khung giờ quá hạn',
    tone: 'amber',
    description: 'Hệ thống tự động xử lý khung giờ dịch vụ quá hạn.',
  },
  EARLY_DISPATCH_SLOT: {
    label: 'Điều phối khung giờ sớm',
    tone: 'neutral',
    description: 'Hệ thống xử lý mở sớm khung giờ dịch vụ.',
  },

  // Planned Actions (Chưa có log BE)
  VERIFY_VENDOR: {
    label: 'Xác minh đối tác (Chưa có log BE)',
    tone: 'neutral',
    description: 'Thẩm định hồ sơ năng lực và cấp huy hiệu đối tác tin cậy (Hành động dự kiến).',
  },
  PROCESS_REFUND: {
    label: 'Xử lý hoàn tiền (Chưa có log BE)',
    tone: 'neutral',
    description: 'Chấp thuận và thực hiện hoàn tiền tự động (Hành động dự kiến).',
  },
  UPDATE_PROMOTION: {
    label: 'Cập nhật khuyến mãi (Chưa có log BE)',
    tone: 'neutral',
    description: 'Điều chỉnh hạn mức hoặc thông tin chương trình ưu đãi (Hành động dự kiến).',
  },
  CREATE_PROMOTION: {
    label: 'Tạo mã khuyến mãi (Chưa có log BE)',
    tone: 'neutral',
    description: 'Phát hành chương trình ưu đãi hoặc mã voucher toàn sàn mới (Hành động dự kiến).',
  },
  ADMIN_LOGIN: {
    label: 'Đăng nhập quản trị (Chưa có log BE)',
    tone: 'neutral',
    description: 'Phiên xác thực bảo mật truy cập bảng điều khiển quản trị (Hành động dự kiến).',
  },

  // Legacy / Planned Aliases (Tương thích ngược)
  APPROVE_PAYOUT: {
    label: 'Phê duyệt yêu cầu chi trả',
    tone: 'teal',
    description: 'Phê duyệt yêu cầu chi trả cho đối tác (Mã cũ / Alias: APPROVE_PAYOUT).',
  },
  REJECT_PAYOUT: {
    label: 'Từ chối yêu cầu chi trả',
    tone: 'rose',
    description: 'Từ chối yêu cầu chi trả của đối tác (Mã cũ / Alias: REJECT_PAYOUT).',
  },
  MARK_PAYOUT_PAID: {
    label: 'Đánh dấu đã chi trả',
    tone: 'teal',
    description: 'Xác nhận đã chuyển tiền chi trả thành công (Mã cũ / Alias: MARK_PAYOUT_PAID).',
  },
  RESOLVE_DISPUTE: {
    label: 'Giải quyết khiếu nại',
    tone: 'teal',
    description: 'Phân xử tranh chấp giữa khách hàng và đối tác (Mã cũ / Alias: RESOLVE_DISPUTE).',
  },
  UPDATE_SYSTEM_CONFIG: {
    label: 'Cập nhật cấu hình hệ thống',
    tone: 'amber',
    description: 'Thay đổi tham số cấu hình vận hành hệ thống (Mã cũ / Alias: UPDATE_SYSTEM_CONFIG).',
  },
  APPROVE_VENDOR: {
    label: 'Phê duyệt hồ sơ đối tác',
    tone: 'teal',
    description: 'Phê duyệt hồ sơ đối tác gia nhập nền tảng (Mã cũ: APPROVE_VENDOR).',
  },
  REJECT_VENDOR: {
    label: 'Từ chối hồ sơ đối tác',
    tone: 'rose',
    description: 'Từ chối hồ sơ đăng ký đối tác (Mã cũ: REJECT_VENDOR).',
  },
  VENDOR_DOCUMENT_APPROVE: {
    label: 'Duyệt chứng từ đối tác',
    tone: 'teal',
    description: 'Thẩm định và chấp thuận chứng từ pháp lý đối tác (Mã cũ: VENDOR_DOCUMENT_APPROVE).',
  },
  VENDOR_DOCUMENT_REJECT: {
    label: 'Từ chối chứng từ đối tác',
    tone: 'rose',
    description: 'Từ chối chứng từ pháp lý đối tác (Mã cũ: VENDOR_DOCUMENT_REJECT).',
  },
  GENERATE_SETTLEMENT: {
    label: 'Chốt kỳ quyết toán',
    tone: 'teal',
    description: 'Tổng hợp đối soát và kết chuyển doanh thu (Mã cũ: GENERATE_SETTLEMENT).',
  },
  LOCK_USER_ACCOUNT: {
    label: 'Khóa tài khoản',
    tone: 'rose',
    description: 'Tạm dừng tài khoản người dùng (Mã cũ: LOCK_USER_ACCOUNT).',
  },
  UNLOCK_USER_ACCOUNT: {
    label: 'Mở khóa tài khoản',
    tone: 'teal',
    description: 'Khôi phục quyền truy cập tài khoản (Mã cũ: UNLOCK_USER_ACCOUNT).',
  },
};

export const ENTITY_LABELS: Record<string, string> = {
  // Uppercase standard backend entity types
  PAYMENT: 'Giao dịch thanh toán',
  PARTNER_APPLICATION: 'Đơn đăng ký đối tác',
  VENDOR: 'Hồ sơ đối tác',
  VENDOR_DOCUMENT: 'Chứng từ đối tác',
  SERVICE: 'Dịch vụ trải nghiệm',
  SETTLEMENT: 'Kỳ quyết toán',
  PAYOUT_REQUEST: 'Yêu cầu chi trả',
  DISPUTE: 'Khiếu nại khách hàng',
  SYSTEM_CONFIG: 'Cấu hình hệ thống',
  COMMISSION_RULE: 'Quy tắc hoa hồng',
  REFUND: 'Yêu cầu hoàn tiền',
  USER: 'Tài khoản người dùng',
  ORDER: 'Đơn hàng',
  MASTER_ORDER: 'Đơn hàng tổng',
  SUB_ORDER: 'Đơn hàng dịch vụ',
  FLASH_SALE: 'Flash Sale',
  PROMOTION: 'Mã khuyến mãi (Chưa có log BE)',

  // Legacy / PascalCase aliases
  Payment: 'Giao dịch thanh toán',
  PartnerApplication: 'Đơn đăng ký đối tác',
  Vendor: 'Hồ sơ đối tác',
  VendorDocument: 'Chứng từ đối tác',
  Service: 'Dịch vụ trải nghiệm',
  Settlement: 'Kỳ quyết toán',
  Refund: 'Yêu cầu hoàn tiền',
  User: 'Tài khoản người dùng',
  Order: 'Đơn hàng',
  PayoutRequest: 'Yêu cầu chi trả',
  Dispute: 'Khiếu nại khách hàng',
  SystemConfig: 'Cấu hình hệ thống',
  CommissionRule: 'Quy tắc hoa hồng',
  Promotion: 'Mã khuyến mãi (Chưa có log BE)',
  DiscountCode: 'Mã giảm giá',
};

export function getActionMeta(action: string): ActionMeta {
  if (ACTION_METAS[action]) {
    return ACTION_METAS[action];
  }
  // Format unknown SNAKE_CASE cleanly
  const cleanLabel = action
    .split('_')
    .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ');
  return {
    label: cleanLabel,
    tone: 'neutral',
    description: `Hành động hệ thống đã ghi nhận: ${action}`,
  };
}

export function formatDateTimeVN(isoString?: string): string {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(new Date(isoString));
  } catch {
    return isoString;
  }
}

export function formatShortDateVN(isoString?: string): string {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(new Date(isoString));
  } catch {
    return isoString;
  }
}

export function formatVND(amount: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount);
}

export const REDACTED_PLACEHOLDER = '[ĐÃ ẨN THÔNG TIN NHẠY CẢM]';

const SENSITIVE_KEY_PATTERN =
  /(?:pass_?word|passwd|pwd|token|jwt|secret|apiKey|api_key|privateKey|private_key|credential|auth_code|authorization|cvv|cvc|pin)/i;

/**
 * Kiểm tra key có chứa từ khóa nhạy cảm hay không (hỗ trợ camelCase, snake_case, UPPERCASE).
 */
export function isSensitiveKey(key: string): boolean {
  if (typeof key !== 'string') return false;
  return SENSITIVE_KEY_PATTERN.test(key);
}

/**
 * Đệ quy làm sạch các trường dữ liệu nhạy cảm trong Object hoặc Array.
 */
export function sanitizeValue(value: unknown): unknown {
  if (value === null || value === undefined) {
    return value;
  }

  if (Array.isArray(value)) {
    return value.map(item => sanitizeValue(item));
  }

  if (typeof value === 'object') {
    const result: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (isSensitiveKey(k)) {
        result[k] = REDACTED_PLACEHOLDER;
      } else {
        result[k] = sanitizeValue(v);
      }
    }
    return result;
  }

  return value;
}

/**
 * An toàn thông tin (Defense-in-depth):
 * Lọc ẩn các trường nhạy cảm đệ quy ở mọi cấp độ (nested objects, arrays)
 * trước khi hiển thị trên Drawer hoặc sao chép JSON.
 */
export function sanitizeMetadata(rawMetadata?: unknown): Record<string, unknown> | unknown[] | null {
  if (rawMetadata === null || rawMetadata === undefined || rawMetadata === '') {
    return null;
  }

  try {
    let parsed: unknown = rawMetadata;
    if (typeof rawMetadata === 'string') {
      const trimmed = rawMetadata.trim();
      if (!trimmed) return null;
      if (
        (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
        (trimmed.startsWith('[') && trimmed.endsWith(']'))
      ) {
        parsed = JSON.parse(trimmed);
      } else {
        return { raw: rawMetadata };
      }
    }

    if (typeof parsed !== 'object' || parsed === null) {
      return { raw: String(parsed) };
    }

    return sanitizeValue(parsed) as Record<string, unknown> | unknown[];
  } catch {
    return { raw: String(rawMetadata) };
  }
}

export interface FormattedAuditOutcome {
  key: string;
  label: string;
  value: string;
  highlight?: boolean;
}

export const FIELD_LABELS: Record<string, string> = {
  amount: 'Số tiền giao dịch / quyết toán',
  totalAmount: 'Tổng giá trị quyết toán',
  bankRef: 'Mã tham chiếu ngân hàng',
  vendor: 'Đối tác thụ hưởng',
  vendorId: 'Mã đối tác liên quan',
  payoutRequestId: 'Mã yêu cầu chi trả',
  disputeId: 'Mã khiếu nại',
  subOrderId: 'Mã đơn dịch vụ liên quan',
  subOrderCount: 'Số đơn dịch vụ quyết toán',
  refundId: 'Mã giao dịch hoàn tiền',
  refundPercentage: 'Tỷ lệ hoàn tiền (%)',
  previousStatus: 'Trạng thái trước đó',
  newStatus: 'Trạng thái mới',
  resolution: 'Phương án giải quyết',
  key: 'Khóa cấu hình',
  previousValue: 'Giá trị trước thay đổi',
  newValue: 'Giá trị mới áp dụng',
  ruleId: 'Mã quy tắc hoa hồng',
  ruleCode: 'Mã định danh quy tắc',
  ruleName: 'Tên quy tắc hoa hồng',
  targetType: 'Đối tượng áp dụng',
  previousRate: 'Tỷ lệ hoa hồng trước (%)',
  newRate: 'Tỷ lệ hoa hồng mới (%)',
  previousActive: 'Kích hoạt trước đó',
  isActive: 'Trạng thái kích hoạt',
  previousDurationDays: 'Thời hạn trước đó (ngày)',
  durationDays: 'Thời hạn áp dụng (ngày)',
  updatedBy: 'Người cập nhật',
  description: 'Mô tả chi tiết',
  badgeTier: 'Hạng đối tác cấp',
  licenseNo: 'Giấy phép kinh doanh',
  verifiedDocs: 'Chứng từ đã duyệt',
  penalty: 'Biện pháp chế tài',
  oldVal: 'Giá trị trước thay đổi',
  newVal: 'Giá trị mới áp dụng',
  reason: 'Lý do thực hiện',
  lastError: 'Chi tiết lỗi đối soát',
  provider: 'Cổng thanh toán',
  transactionId: 'Mã giao dịch cổng thanh toán',
  status: 'Trạng thái giao dịch',
  attempts: 'Số lần thử đối soát',
  reconciliationNextAttemptAt: 'Lần thử đối soát kế tiếp',
  lastReconciledAt: 'Thời điểm đối soát gần nhất',
  actorRole: 'Vai trò người thực hiện',
  note: 'Ghi chú thẩm định',
  adminNote: 'Ghi chú quản trị',
  ipAddress: 'Địa chỉ IP',
  userAgent: 'Trình duyệt / Thiết bị',
};

export const VALUE_TRANSLATIONS: Record<string, string> = {
  PLATINUM: 'Bạch kim (PLATINUM)',
  GOLD: 'Vàng (GOLD)',
  SILVER: 'Bạc (SILVER)',
  STANDARD: 'Tiêu chuẩn (STANDARD)',
  REFUND_200K: 'Hoàn tiền 200.000 ₫ (REFUND_200K)',
  WARNING_NOTE: 'Ghi nhận nhắc nhở cảnh cáo (WARNING_NOTE)',
  PAYPAL: 'Cổng thanh toán PayPal',
  VNPAY: 'Cổng thanh toán VNPay',
  MOMO: 'Ví điện tử MoMo',
  PENDING: 'Đang chờ xử lý (PENDING)',
  COMPLETED: 'Hoàn tất thành công (COMPLETED)',
  FAILED: 'Thất bại (FAILED)',
  EXPIRED: 'Hết hạn thanh toán (EXPIRED)',
  VERIFIED: 'Đã xác minh (VERIFIED)',
  REJECTED: 'Đã từ chối (REJECTED)',
  APPROVED: 'Đã phê duyệt (APPROVED)',
  PAID: 'Đã chi trả (PAID)',
  RESOLVED: 'Đã giải quyết (RESOLVED)',
  CUSTOMER_REFUND: 'Hoàn tiền cho khách hàng (CUSTOMER_REFUND)',
  VENDOR_PAYOUT: 'Thanh toán cho đối tác (VENDOR_PAYOUT)',
  SPLIT_EQUAL: 'Phân chia 50-50 (SPLIT_EQUAL)',
  DISMISSED: 'Bác đơn khiếu nại (DISMISSED)',
};

/**
 * Trình bày metadata bằng nhãn tiếng Việt và giá trị dễ đọc cho người quản trị
 */
export function parseAuditOutcome(rawMetadata?: string | null): FormattedAuditOutcome[] {
  const sanitized = sanitizeMetadata(rawMetadata);
  if (!sanitized) return [];

  if (Array.isArray(sanitized)) {
    return sanitized.map((item, index) => ({
      key: `item_${index}`,
      label: `Mục #${index + 1}`,
      value: typeof item === 'object' && item !== null ? JSON.stringify(item) : String(item),
    }));
  }

  const outcomes: FormattedAuditOutcome[] = [];

  for (const [key, val] of Object.entries(sanitized as Record<string, unknown>)) {
    const label = FIELD_LABELS[key] || key;
    let displayVal: string;

    if (val === null || val === undefined) {
      displayVal = 'Chưa ghi nhận (null)';
    } else if (key === 'amount' && (typeof val === 'number' || !isNaN(Number(val)))) {
      displayVal = formatVND(Number(val));
    } else if (key === 'verifiedDocs') {
      displayVal = `${val} tài liệu đã kiểm tra hợp lệ`;
    } else if (typeof val === 'string' && VALUE_TRANSLATIONS[val]) {
      displayVal = VALUE_TRANSLATIONS[val];
    } else if (typeof val === 'string' && val.includes('GATEWAY_QUERY_AWAITING_VERIFICATION')) {
      displayVal = `Chờ xác thực cổng thanh toán (Thiếu mã tham chiếu hoặc ngày giao dịch gốc)`;
    } else if (typeof val === 'object' && val !== null) {
      displayVal = JSON.stringify(val);
    } else {
      displayVal = String(val);
    }

    outcomes.push({
      key,
      label,
      value: displayVal,
      highlight: key === 'amount' || key === 'newVal' || key === 'resolution' || key === 'lastError',
    });
  }

  return outcomes;
}

export function filterAuditLogs(
  logs: AdminAuditLogItem[],
  query: string,
  actionFilter: LogActionFilter,
  entityFilter: LogEntityFilter
): AdminAuditLogItem[] {
  const q = query.trim().toLowerCase();

  return logs.filter(log => {
    // Action filter (supporting alias resolution)
    if (actionFilter !== 'ALL') {
      const matchAction =
        log.action === actionFilter ||
        (actionFilter === 'VENDOR_APPROVAL' && log.action === 'APPROVE_VENDOR') ||
        (actionFilter === 'APPROVE_VENDOR' && log.action === 'VENDOR_APPROVAL') ||
        (actionFilter === 'VENDOR_REJECTION' && log.action === 'REJECT_VENDOR') ||
        (actionFilter === 'REJECT_VENDOR' && log.action === 'VENDOR_REJECTION') ||
        (actionFilter === 'VENDOR_DOCUMENT_APPROVED' && log.action === 'VENDOR_DOCUMENT_APPROVE') ||
        (actionFilter === 'VENDOR_DOCUMENT_APPROVE' && log.action === 'VENDOR_DOCUMENT_APPROVED') ||
        (actionFilter === 'VENDOR_DOCUMENT_REJECTED' && log.action === 'VENDOR_DOCUMENT_REJECT') ||
        (actionFilter === 'VENDOR_DOCUMENT_REJECT' && log.action === 'VENDOR_DOCUMENT_REJECTED') ||
        (actionFilter === 'FINALIZE_SETTLEMENT' && log.action === 'GENERATE_SETTLEMENT') ||
        (actionFilter === 'GENERATE_SETTLEMENT' && log.action === 'FINALIZE_SETTLEMENT') ||
        (actionFilter === 'USER_LOCKED' && log.action === 'LOCK_USER_ACCOUNT') ||
        (actionFilter === 'LOCK_USER_ACCOUNT' && log.action === 'USER_LOCKED') ||
        (actionFilter === 'USER_UNLOCKED' && log.action === 'UNLOCK_USER_ACCOUNT') ||
        (actionFilter === 'UNLOCK_USER_ACCOUNT' && log.action === 'USER_UNLOCKED') ||
        (actionFilter === 'PAYOUT_REQUEST_APPROVED' && log.action === 'APPROVE_PAYOUT') ||
        (actionFilter === 'APPROVE_PAYOUT' && log.action === 'PAYOUT_REQUEST_APPROVED') ||
        (actionFilter === 'PAYOUT_REQUEST_REJECTED' && log.action === 'REJECT_PAYOUT') ||
        (actionFilter === 'REJECT_PAYOUT' && log.action === 'PAYOUT_REQUEST_REJECTED') ||
        (actionFilter === 'PAYOUT_REQUEST_PAID' && log.action === 'MARK_PAYOUT_PAID') ||
        (actionFilter === 'MARK_PAYOUT_PAID' && log.action === 'PAYOUT_REQUEST_PAID') ||
        (actionFilter === 'DISPUTE_RESOLVED' && log.action === 'RESOLVE_DISPUTE') ||
        (actionFilter === 'RESOLVE_DISPUTE' && log.action === 'DISPUTE_RESOLVED') ||
        (actionFilter === 'SYSTEM_CONFIG_UPDATED' && log.action === 'UPDATE_SYSTEM_CONFIG') ||
        (actionFilter === 'UPDATE_SYSTEM_CONFIG' && log.action === 'SYSTEM_CONFIG_UPDATED');

      if (!matchAction) {
        return false;
      }
    }

    // Entity filter (case-insensitive and snake_case/PascalCase fallback for demo mode)
    if (entityFilter !== 'ALL') {
      const normLogEntity = log.entityType.replace(/_/g, '').toUpperCase();
      const normFilterEntity = entityFilter.replace(/_/g, '').toUpperCase();
      const matchEntity =
        log.entityType === entityFilter ||
        normLogEntity === normFilterEntity;
      if (!matchEntity) {
        return false;
      }
    }

    // Search query
    if (!q) return true;

    const matchesId = log.id.toLowerCase().includes(q);
    const matchesActorName = Boolean(log.actorName && log.actorName.toLowerCase().includes(q));
    const matchesActorEmail = Boolean(log.actorEmail && log.actorEmail.toLowerCase().includes(q));
    const matchesEntityId = log.entityId.toLowerCase().includes(q);
    const matchesEntityType = log.entityType.toLowerCase().includes(q);
    const matchesAction = log.action.toLowerCase().includes(q);
    const matchesActionLabel = getActionMeta(log.action).label.toLowerCase().includes(q);
    const matchesEntityLabel = (ENTITY_LABELS[log.entityType] || ENTITY_LABELS[log.entityType.toUpperCase()] || '').toLowerCase().includes(q);
    const matchesMeta = Boolean(log.metadata && log.metadata.toLowerCase().includes(q));

    return (
      matchesId ||
      matchesActorName ||
      matchesActorEmail ||
      matchesEntityId ||
      matchesEntityType ||
      matchesEntityLabel ||
      matchesAction ||
      matchesActionLabel ||
      matchesMeta
    );
  });
}

import type { AuditLog } from '../../../types';

export interface AdminAuditLogItem extends AuditLog {
  actorName?: string;
  actorEmail?: string;
}

export type LogActionFilter =
  | 'ALL'
  | 'RECONCILE_PAYMENT_PENDING'
  | 'RECONCILE_PAYMENT_FAILED'
  | 'RECONCILE_PAYMENT_SUCCESS'
  | 'PARTNER_APPLICATION_REJECTION'
  | 'PARTNER_APPLICATION_APPROVAL'
  | 'VENDOR_DOCUMENT_APPROVE'
  | 'VENDOR_DOCUMENT_REJECT'
  | 'APPROVE_VENDOR'
  | 'REJECT_VENDOR'
  | 'APPROVE_PAYOUT'
  | 'VERIFY_VENDOR'
  | 'RESOLVE_DISPUTE'
  | 'PROCESS_REFUND'
  | 'REFUND_MANUALLY_COMPLETED'
  | 'UPDATE_PROMOTION'
  | 'CREATE_PROMOTION'
  | 'GENERATE_SETTLEMENT'
  | 'UPDATE_SYSTEM_CONFIG'
  | 'LOCK_USER_ACCOUNT'
  | 'UNLOCK_USER_ACCOUNT'
  | 'ADMIN_LOGIN'
  | string;

export type LogEntityFilter =
  | 'ALL'
  | 'PAYMENT'
  | 'PARTNER_APPLICATION'
  | 'VENDOR_DOCUMENT'
  | 'PayoutRequest'
  | 'Vendor'
  | 'Dispute'
  | 'SystemConfig'
  | 'User'
  | 'Promotion'
  | 'Order'
  | 'ORDER'
  | 'MASTER_ORDER'
  | 'SUB_ORDER'
  | 'Refund'
  | 'REFUND'
  | 'Settlement'
  | 'SETTLEMENT'
  | 'SERVICE'
  | 'FLASH_SALE'
  | string;

export interface ActionMeta {
  label: string;
  tone: 'teal' | 'amber' | 'rose' | 'neutral';
  description: string;
}

export const ACTION_METAS: Record<string, ActionMeta> = {
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
  PARTNER_APPLICATION_REJECTION: {
    label: 'Từ chối đơn đăng ký đối tác',
    tone: 'rose',
    description: 'Hồ sơ xin gia nhập đối tác cung cấp dịch vụ đã bị từ chối.',
  },
  PARTNER_APPLICATION_APPROVAL: {
    label: 'Duyệt đơn đăng ký đối tác',
    tone: 'teal',
    description: 'Hồ sơ xin gia nhập đối tác cung cấp dịch vụ đã được phê duyệt.',
  },
  APPROVE_PAYOUT: {
    label: 'Duyệt quyết toán',
    tone: 'teal',
    description: 'Chấp thuận yêu cầu rút tiền hoặc giải ngân cho đối tác.',
  },
  VERIFY_VENDOR: {
    label: 'Xác minh đối tác',
    tone: 'teal',
    description: 'Thẩm định hồ sơ năng lực và cấp huy hiệu đối tác tin cậy.',
  },
  REJECT_VENDOR: {
    label: 'Từ chối đối tác',
    tone: 'rose',
    description: 'Từ chối hồ sơ đăng ký đối tác chưa đạt tiêu chuẩn an toàn.',
  },
  APPROVE_VENDOR: {
    label: 'Phê duyệt đối tác',
    tone: 'teal',
    description: 'Phê duyệt hồ sơ đối tác gia nhập nền tảng.',
  },
  VENDOR_DOCUMENT_APPROVE: {
    label: 'Duyệt chứng từ đối tác',
    tone: 'teal',
    description: 'Thẩm định và chấp thuận chứng từ pháp lý hoặc chứng nhận của đối tác.',
  },
  VENDOR_DOCUMENT_REJECT: {
    label: 'Từ chối chứng từ đối tác',
    tone: 'rose',
    description: 'Từ chối chứng từ pháp lý của đối tác do chưa đạt tiêu chuẩn.',
  },
  RESOLVE_DISPUTE: {
    label: 'Giải quyết khiếu nại',
    tone: 'amber',
    description: 'Phân xử tranh chấp giữa khách hàng và nhà cung cấp dịch vụ.',
  },
  PROCESS_REFUND: {
    label: 'Xử lý hoàn tiền',
    tone: 'amber',
    description: 'Chấp thuận và thực hiện hoàn tiền giao dịch cho khách hàng.',
  },
  REFUND_MANUALLY_COMPLETED: {
    label: 'Hoàn tiền thủ công thành công',
    tone: 'teal',
    description: 'Giao dịch hoàn tiền đã được xử lý hoàn tất thủ công bởi quản trị viên.',
  },
  UPDATE_PROMOTION: {
    label: 'Cập nhật khuyến mãi',
    tone: 'neutral',
    description: 'Điều chỉnh hạn mức hoặc thông tin chương trình ưu đãi.',
  },
  CREATE_PROMOTION: {
    label: 'Tạo mã khuyến mãi',
    tone: 'teal',
    description: 'Phát hành chương trình ưu đãi hoặc mã voucher toàn sàn mới.',
  },
  GENERATE_SETTLEMENT: {
    label: 'Tạo kỳ quyết toán',
    tone: 'neutral',
    description: 'Tổng hợp đối soát và kết chuyển doanh thu kỳ hạn cho đối tác.',
  },
  UPDATE_SYSTEM_CONFIG: {
    label: 'Cập nhật cấu hình',
    tone: 'neutral',
    description: 'Thay đổi tham số vận hành sàn hoặc tiêu chuẩn an toàn biển.',
  },
  LOCK_USER_ACCOUNT: {
    label: 'Khóa tài khoản',
    tone: 'rose',
    description: 'Tạm dừng hoặc vô hiệu hóa tài khoản vi phạm chính sách.',
  },
  UNLOCK_USER_ACCOUNT: {
    label: 'Mở khóa tài khoản',
    tone: 'teal',
    description: 'Khôi phục quyền truy cập cho tài khoản người dùng sau khi giải tỏa vi phạm.',
  },
  ADMIN_LOGIN: {
    label: 'Đăng nhập quản trị',
    tone: 'neutral',
    description: 'Phiên xác thực bảo mật truy cập bảng điều khiển quản trị.',
  },
};

export const ENTITY_LABELS: Record<string, string> = {
  PAYMENT: 'Giao dịch thanh toán',
  Payment: 'Giao dịch thanh toán',
  PARTNER_APPLICATION: 'Đơn đăng ký đối tác',
  PartnerApplication: 'Đơn đăng ký đối tác',
  VENDOR_DOCUMENT: 'Chứng từ đối tác',
  VendorDocument: 'Chứng từ đối tác',
  PayoutRequest: 'Yêu cầu quyết toán',
  PAYOUT_REQUEST: 'Yêu cầu quyết toán',
  Vendor: 'Hồ sơ đối tác',
  VENDOR: 'Hồ sơ đối tác',
  Dispute: 'Khiếu nại khách hàng',
  DISPUTE: 'Khiếu nại khách hàng',
  SystemConfig: 'Tham số hệ thống',
  SYSTEM_CONFIG: 'Tham số hệ thống',
  User: 'Tài khoản người dùng',
  USER: 'Tài khoản người dùng',
  Promotion: 'Mã khuyến mãi',
  PROMOTION: 'Mã khuyến mãi',
  Order: 'Đơn hàng',
  ORDER: 'Đơn hàng',
  MASTER_ORDER: 'Đơn hàng tổng',
  SUB_ORDER: 'Đơn hàng dịch vụ',
  Refund: 'Yêu cầu hoàn tiền',
  REFUND: 'Yêu cầu hoàn tiền',
  Settlement: 'Kỳ quyết toán',
  SETTLEMENT: 'Kỳ quyết toán',
  SERVICE: 'Dịch vụ trải nghiệm',
  FLASH_SALE: 'Flash Sale',
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
  amount: 'Số tiền quyết toán',
  bankRef: 'Mã tham chiếu ngân hàng',
  vendor: 'Đối tác thụ hưởng',
  vendorId: 'Mã đối tác liên quan',
  badgeTier: 'Hạng đối tác cấp',
  licenseNo: 'Giấy phép kinh doanh',
  verifiedDocs: 'Chứng từ đã duyệt',
  resolution: 'Phương án giải quyết',
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
    // Action filter
    if (actionFilter !== 'ALL' && log.action !== actionFilter) {
      return false;
    }

    // Entity filter
    if (entityFilter !== 'ALL' && log.entityType !== entityFilter) {
      return false;
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
    const matchesEntityLabel = (ENTITY_LABELS[log.entityType] || '').toLowerCase().includes(q);
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

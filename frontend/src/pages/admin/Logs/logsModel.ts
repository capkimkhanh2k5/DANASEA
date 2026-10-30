import type { AuditLog } from '../../../types';

export interface AdminAuditLogItem extends AuditLog {
  actorName?: string;
  actorEmail?: string;
}

export type LogActionFilter =
  | 'ALL'
  | 'APPROVE_PAYOUT'
  | 'VERIFY_VENDOR'
  | 'RESOLVE_DISPUTE'
  | 'UPDATE_SYSTEM_CONFIG'
  | 'LOCK_USER_ACCOUNT';

export type LogEntityFilter =
  | 'ALL'
  | 'PayoutRequest'
  | 'Vendor'
  | 'Dispute'
  | 'SystemConfig'
  | 'User';

export interface ActionMeta {
  label: string;
  tone: 'teal' | 'amber' | 'rose' | 'neutral';
  description: string;
}

export const ACTION_METAS: Record<string, ActionMeta> = {
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
  RESOLVE_DISPUTE: {
    label: 'Giải quyết khiếu nại',
    tone: 'amber',
    description: 'Phân xử tranh chấp giữa khách hàng và nhà cung cấp dịch vụ.',
  },
  UPDATE_SYSTEM_CONFIG: {
    label: 'Cập nhật cấu hình',
    tone: 'neutral',
    description: 'Thay đổi tham số vận hành sàn hoặc an toàn biển.',
  },
  LOCK_USER_ACCOUNT: {
    label: 'Khóa tài khoản',
    tone: 'rose',
    description: 'Tạm dừng hoặc vô hiệu hóa tài khoản vi phạm chính sách.',
  },
};

export const ENTITY_LABELS: Record<string, string> = {
  PayoutRequest: 'Yêu cầu quyết toán',
  Vendor: 'Hồ sơ đối tác',
  Dispute: 'Khiếu nại khách hàng',
  SystemConfig: 'Tham số hệ thống',
  User: 'Tài khoản người dùng',
};

export function getActionMeta(action: string): ActionMeta {
  return (
    ACTION_METAS[action] || {
      label: action,
      tone: 'neutral',
      description: 'Hành động hệ thống đã ghi nhận',
    }
  );
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
 * - Không làm thay đổi (mutate) dữ liệu gốc.
 * - Xử lý mọi cấp độ lồng nhau (nested objects, arrays).
 * - Redact giá trị của key nhạy cảm thành REDACTED_PLACEHOLDER.
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
 *
 * Lưu ý kiến trúc bảo mật:
 * Frontend sanitization là lớp bảo vệ trực quan bổ sung, không thay thế
 * cho cơ chế kiểm soát truy cập và bảo vệ dữ liệu ở tầng Backend/Database.
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

const FIELD_LABELS: Record<string, string> = {
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
};

const VALUE_TRANSLATIONS: Record<string, string> = {
  PLATINUM: 'Bạch kim (PLATINUM)',
  GOLD: 'Vàng (GOLD)',
  SILVER: 'Bạc (SILVER)',
  STANDARD: 'Tiêu chuẩn (STANDARD)',
  REFUND_200K: 'Hoàn tiền 200.000 ₫ (REFUND_200K)',
  WARNING_NOTE: 'Ghi nhận nhắc nhở cảnh cáo (WARNING_NOTE)',
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
    let displayVal = String(val);

    if (key === 'amount' && (typeof val === 'number' || !isNaN(Number(val)))) {
      displayVal = formatVND(Number(val));
    } else if (key === 'verifiedDocs') {
      displayVal = `${val} tài liệu đã kiểm tra hợp lệ`;
    } else if (typeof val === 'string' && VALUE_TRANSLATIONS[val]) {
      displayVal = VALUE_TRANSLATIONS[val];
    } else if (typeof val === 'object' && val !== null) {
      displayVal = JSON.stringify(val);
    }

    outcomes.push({
      key,
      label,
      value: displayVal,
      highlight: key === 'amount' || key === 'newVal' || key === 'resolution',
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
    const matchesMeta = Boolean(log.metadata && log.metadata.toLowerCase().includes(q));

    return (
      matchesId ||
      matchesActorName ||
      matchesActorEmail ||
      matchesEntityId ||
      matchesEntityType ||
      matchesAction ||
      matchesMeta
    );
  });
}

import type { CommissionRuleDto } from '../../../types/adminSetting';

export interface DisplayCategoryRule {
  id: string;
  ruleCode: string;
  ruleName: string;
  targetType: 'CATEGORY';
  categoryId?: string | null;
  categoryName: string;
  categorySlug: string;
  commissionRatePercent: number;
  isActive: boolean;
  isCustom: boolean;
  originalRule?: CommissionRuleDto;
}

export interface SettingsData {
  commissionRate: string;
  emergencyHotline: string;
  maxSafeWave: string;
  maxSafeWind: string;
}

export const DEFAULT_SETTINGS: SettingsData = {
  commissionRate: '10',
  emergencyHotline: '0236 3920 477',
  maxSafeWave: '1.5',
  maxSafeWind: '25',
};

// Backward-compatible alias
export const DEFAULT_CONFIG_VALUES = {
  ...DEFAULT_SETTINGS,
  payoutCycle: '15',
};

export function validateCommissionRate(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return 'Tỷ lệ hoa hồng không được để trống.';
  }
  const num = Number(trimmed);
  if (isNaN(num)) {
    return 'Tỷ lệ hoa hồng phải là chữ số.';
  }
  if (num < 0 || num > 100) {
    return 'Tỷ lệ hoa hồng phải là số từ 0% đến 100%.';
  }
  return null;
}

export function validateHotline(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) {
    return 'Số điện thoại liên hệ không được để trống.';
  }
  if (trimmed.length < 7 || trimmed.length > 20) {
    return 'Số điện thoại phải từ 7 đến 20 ký tự.';
  }
  if (!/^[\d\s\-+.]+$/.test(trimmed)) {
    return 'Số điện thoại chỉ được chứa chữ số, khoảng trắng hoặc dấu gạch nối.';
  }
  return null;
}

export function formatDateTimeVN(isoString?: string): string {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(isoString));
  } catch {
    return isoString;
  }
}

// ============================================================================
// Biểu phí hoa hồng 12 danh mục & Màu Badge UI (Tuân thủ Purple Ban)
// ============================================================================

export interface CategoryGroupMeta {
  groupId: 'gear' | 'sports' | 'tours' | 'other';
  groupName: string;
  shortName: string;
  defaultRate: number;
  badgeBg: string;
  badgeText: string;
  badgeBorder: string;
}

export const CATEGORY_GROUPS: Record<'gear' | 'sports' | 'tours' | 'other', CategoryGroupMeta> = {
  gear: {
    groupId: 'gear',
    groupName: 'Nhóm 1: Tiện ích & Thuê dụng cụ',
    shortName: 'Thuê thiết bị',
    defaultRate: 7.0,
    badgeBg: '#ecfdf5',
    badgeText: '#065f46',
    badgeBorder: '#a7f3d0',
  },
  sports: {
    groupId: 'sports',
    groupName: 'Nhóm 2: Thể thao biển phổ thông',
    shortName: 'Thể thao biển phổ thông',
    defaultRate: 10.0,
    badgeBg: '#eef7f8',
    badgeText: '#0b5d6f',
    badgeBorder: '#b8d8dc',
  },
  tours: {
    groupId: 'tours',
    groupName: 'Nhóm 3: Tour chuyên sâu & Lặn biển',
    shortName: 'Trải nghiệm chuyên sâu',
    defaultRate: 12.0,
    badgeBg: '#fffbeb',
    badgeText: '#92400e',
    badgeBorder: '#fde68a',
  },
  other: {
    groupId: 'other',
    groupName: 'Danh mục khác',
    shortName: 'Khác',
    defaultRate: 10.0,
    badgeBg: '#f8fafc',
    badgeText: '#475569',
    badgeBorder: '#e2e8f0',
  },
};

const SLUG_GROUP_MAP: Record<string, 'gear' | 'sports' | 'tours'> = {
  // Nhóm 1: 7.0%
  'thue-sup': 'gear',
  'thue-kayak': 'gear',
  'thue-van-luot-song': 'gear',
  // Nhóm 2: 10.0%
  'cano-du-bay': 'sports',
  'mo-to-nuoc-jetski': 'sports',
  'truot-phao-chuoi': 'sports',
  'cheo-sup-kayak': 'sports',
  // Nhóm 3: 12.0%
  'lan-ngam-san-ho': 'tours',
  'cau-ca-bien': 'tours',
  'cau-muc-dem': 'tours',
  'hoc-luot-song': 'tours',
  'surf-tour-co-huong-dan': 'tours',
  'du-thuyen-ngam-hoang-hon': 'tours',
};

/**
 * Xác định nhóm biểu phí danh mục dựa vào slug cố định (không phụ thuộc vào tỷ lệ hiện tại)
 */
export function getCategoryGroupMeta(slug?: string | null): CategoryGroupMeta {
  if (!slug) return CATEGORY_GROUPS.other;
  const normalized = slug.trim().toLowerCase();
  const groupKey = SLUG_GROUP_MAP[normalized] || 'other';
  return CATEGORY_GROUPS[groupKey];
}

// ============================================================================
// P0-1: Chuyển đổi & Chuẩn hóa an toàn đơn vị Safety Rebate (Thưởng SLA)
// ============================================================================

/**
 * Đọc tỷ lệ thưởng SLA từ giá trị chuỗi lưu trong system_configs và chuyển thành số phần trăm (%).
 * - Nếu lưu theo tỷ lệ thập phân (đúng spec): "0.02" -> 2.0%, "0.025" -> 2.5%, "0.01" -> 1.0%, "0.005" -> 0.5%
 * - Nếu lưu theo tỷ lệ phần trăm (legacy seed V54): "2.0" -> 2.0%, "2" -> 2.0%
 * - An toàn với các giá trị rỗng/lỗi -> fallback về 2.0% mặc định
 */
export function parseSafetyRebatePercent(rawValue: string | undefined | null): number {
  if (!rawValue || typeof rawValue !== 'string' || !rawValue.trim()) {
    return 2.0;
  }
  const num = Number(rawValue.trim());
  if (isNaN(num) || num < 0) {
    return 2.0;
  }
  // Với chính sách thưởng tiền mặt SLA, tỷ lệ thưởng an toàn luôn nằm trong khoảng 0% - 10%.
  // Nếu giá trị thô <= 0.20, nó đang được lưu dưới dạng tỷ lệ thập phân (fraction e.g. 0.02 tương ứng 2%).
  // Nếu giá trị thô > 0.20, nó đang được lưu dưới dạng số phần trăm trực tiếp (legacy e.g. 2.0 tương ứng 2%).
  if (num <= 0.20) {
    return Number((num * 100).toFixed(2));
  }
  return Number(num.toFixed(2));
}

/**
 * Chuyển số phần trăm người dùng nhập (% e.g. 2.0) thành chuỗi payload gửi lên Backend PUT.
 * Theo đúng API Contract của leader spec: {"value": "0.02"}.
 * Đồng thời bảo đảm Backend GenerateSettlementUseCase luôn nhận giá trị <= 1 để không bị kích hoạt
 * logic lỗi (chia 100 sai khi giá trị <= 1).
 */
export function toSafetyRebatePayload(percent: number | string): string {
  const num = typeof percent === 'number' ? percent : Number(percent.toString().trim());
  if (isNaN(num) || num < 0) {
    return '0.02';
  }
  const fraction = num / 100;
  return fraction.toFixed(4).replace(/\.?0+$/, '') || '0';
}

/**
 * Kiểm tra tính hợp lệ của tỷ lệ thưởng SLA nhập vào (cho phép từ 0% đến 10%)
 */
export function validateSafetyRebateRate(value: string | number): string | null {
  if (value === '' || value === null || value === undefined) {
    return 'Tỷ lệ thưởng SLA không được để trống.';
  }
  const num = typeof value === 'number' ? value : Number(value.toString().trim());
  if (isNaN(num)) {
    return 'Tỷ lệ thưởng SLA phải là chữ số.';
  }
  if (num < 0 || num > 10) {
    return 'Tỷ lệ thưởng SLA phải từ 0% đến 10%.';
  }
  return null;
}

// ============================================================================
// Kiểm tra dữ liệu nhập (Validation) cho Commission Rules
// ============================================================================

export function validateCommissionRatePercent(value: string | number): string | null {
  if (value === '' || value === null || value === undefined) {
    return 'Tỷ lệ hoa hồng không được để trống.';
  }
  const num = typeof value === 'number' ? value : Number(value.toString().trim());
  if (isNaN(num)) {
    return 'Tỷ lệ hoa hồng phải là chữ số.';
  }
  if (num < 0 || num > 100) {
    return 'Tỷ lệ hoa hồng phải từ 0% đến 100%.';
  }
  return null;
}

export function validateDurationDays(value: string | number): string | null {
  if (value === '' || value === null || value === undefined) {
    return 'Số ngày áp dụng không được để trống.';
  }
  const num = typeof value === 'number' ? value : Number(value.toString().trim());
  if (isNaN(num) || !Number.isInteger(num)) {
    return 'Số ngày áp dụng phải là số nguyên.';
  }
  if (num <= 0 || num > 3650) {
    return 'Số ngày áp dụng phải lớn hơn 0 và tối đa 3650 ngày (10 năm).';
  }
  return null;
}

// ============================================================================
// Định dạng tỷ lệ phần trăm hiển thị (Format Percent Display)
// ============================================================================

export function formatPercentDisplay(val: number): string {
  if (isNaN(val)) return '0';
  return Number.isInteger(val) ? val.toFixed(0) : val.toFixed(1);
}

// ============================================================================
// Thông số mặc định hệ thống (System Baseline)
// ============================================================================

export const SYSTEM_DEFAULTS = {
  platformCommissionRate: 10.0,
  onboardingRate: 5.0,
  onboardingDays: 90,
  safetyRebateRate: 2.0,
  safetyRebateEnabled: true,
};

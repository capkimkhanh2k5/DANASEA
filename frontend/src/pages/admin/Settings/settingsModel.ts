import type { SystemConfig } from '../../../types';

export type SettingsTab = 'all' | 'operations' | 'maritime_safety';

export interface SettingsFormData {
  commissionRate: string;
  payoutCycle: string;
  maxSafeWave: string;
  maxSafeWind: string;
  emergencyHotline: string;
}

export interface SettingsErrors {
  commissionRate?: string;
  payoutCycle?: string;
  maxSafeWave?: string;
  maxSafeWind?: string;
  emergencyHotline?: string;
}

export interface ConfigDefinition {
  key: keyof SettingsFormData;
  dbKey: string;
  label: string;
  group: 'operations' | 'maritime_safety';
  unit: string;
  step?: string;
  inputType: 'number' | 'text';
  placeholder: string;
  description: string;
  operationalNote: string;
  categoryLabel: string;
  disclaimer?: string;
}

export const CONFIG_DEFINITIONS: ConfigDefinition[] = [
  {
    key: 'commissionRate',
    dbKey: 'PLATFORM_COMMISSION_RATE',
    label: 'Tỷ lệ hoa hồng sàn mặc định',
    group: 'operations',
    unit: '%',
    step: '0.5',
    inputType: 'number',
    placeholder: '10',
    description: 'Tỷ lệ chiết khấu áp dụng trên tổng giá trị đơn dịch vụ con (SubOrder) khi hoàn tất thanh toán.',
    operationalNote: 'Chỉ áp dụng cho các đơn đặt mới sau thời điểm lưu cấu hình. Không hồi tố đơn lịch sử.',
    categoryLabel: 'Tài chính & Giao dịch',
  },
  {
    key: 'maxSafeWave',
    dbKey: 'WEATHER_MAX_SAFE_WAVE_M',
    label: 'Ngưỡng sóng biển tham chiếu vận hành',
    group: 'maritime_safety',
    unit: 'mét (m)',
    step: '0.1',
    inputType: 'number',
    placeholder: '1.5',
    description: 'Ngưỡng chiều cao sóng mô phỏng để hệ thống tự động gắn nhãn cảnh báo cấm xuất bến tour cano, lặn biển và SUP.',
    operationalNote: 'Dữ liệu tham chiếu vận hành nội bộ sàn DANASEA, không thay thế văn bản chỉ đạo của cơ quan quản lý.',
    categoryLabel: 'An toàn biển & Cứu hộ',
    disclaimer: 'Thông số tham chiếu vận hành do hệ thống quản lý, không phải tiêu chuẩn pháp lý nhà nước.',
  },
  {
    key: 'maxSafeWind',
    dbKey: 'WEATHER_MAX_SAFE_WIND_KMH',
    label: 'Ngưỡng gió giật tham chiếu cảnh báo',
    group: 'maritime_safety',
    unit: 'km/h',
    step: '1',
    inputType: 'number',
    placeholder: '25',
    description: 'Vận tốc gió giật kích hoạt cờ cảnh báo (Cờ Vàng/Cờ Đỏ) trên ứng dụng điều hành và bảng tin thuyền trưởng.',
    operationalNote: 'Hệ thống gửi thông báo đẩy tới thuyền trưởng khi cảm biến thời tiết chạm ngưỡng khai báo.',
    categoryLabel: 'An toàn biển & Cứu hộ',
    disclaimer: 'Thông số tham chiếu vận hành do hệ thống quản lý, không phải tiêu chuẩn pháp lý nhà nước.',
  },
  {
    key: 'emergencyHotline',
    dbKey: 'EMERGENCY_PORT_HOTLINE',
    label: 'Số liên hệ Ban Quản lý Bán đảo Sơn Trà & các bãi biển du lịch Đà Nẵng',
    group: 'maritime_safety',
    unit: '',
    inputType: 'text',
    placeholder: '0236 3920 477',
    description: 'Số điện thoại liên hệ BQL bán đảo Sơn Trà và các bãi biển du lịch Đà Nẵng phục vụ điều phối và hỗ trợ thông tin cứu hộ.',
    operationalNote: 'Liên hệ giờ hành chính và trực ban theo quy chế phối hợp của Ban Quản lý bán đảo Sơn Trà và các bãi biển du lịch Đà Nẵng.',
    categoryLabel: 'An toàn biển & Cứu hộ',
  },
];

export const DEFAULT_CONFIG_VALUES: SettingsFormData = {
  commissionRate: '10',
  payoutCycle: '15',
  maxSafeWave: '1.5',
  maxSafeWind: '25',
  emergencyHotline: '0236 3920 477',
};

export function extractFormDataFromConfigs(configs: SystemConfig[]): SettingsFormData {
  return {
    commissionRate: configs.find(c => c.key === 'PLATFORM_COMMISSION_RATE')?.value || DEFAULT_CONFIG_VALUES.commissionRate,
    payoutCycle: configs.find(c => c.key === 'AUTO_PAYOUT_CYCLE_DAYS')?.value || DEFAULT_CONFIG_VALUES.payoutCycle,
    maxSafeWave: configs.find(c => c.key === 'WEATHER_MAX_SAFE_WAVE_M')?.value || DEFAULT_CONFIG_VALUES.maxSafeWave,
    maxSafeWind: configs.find(c => c.key === 'WEATHER_MAX_SAFE_WIND_KMH')?.value || DEFAULT_CONFIG_VALUES.maxSafeWind,
    emergencyHotline: configs.find(c => c.key === 'EMERGENCY_PORT_HOTLINE')?.value || DEFAULT_CONFIG_VALUES.emergencyHotline,
  };
}

export function validateSettingsForm(form: SettingsFormData): SettingsErrors {
  const errors: SettingsErrors = {};

  // Commission rate: 0 to 100
  const comm = Number(form.commissionRate);
  if (isNaN(comm) || comm < 0 || comm > 100) {
    errors.commissionRate = 'Tỷ lệ hoa hồng phải là số từ 0% đến 100%.';
  }

  // Payout cycle: 1 to 365
  const cycle = Number(form.payoutCycle);
  if (isNaN(cycle) || !Number.isInteger(cycle) || cycle < 1 || cycle > 365) {
    errors.payoutCycle = 'Chu kỳ đối soát phải là số nguyên từ 1 đến 365 ngày.';
  }

  // Max safe wave: 0.1 to 10.0m
  const wave = Number(form.maxSafeWave);
  if (isNaN(wave) || wave <= 0 || wave > 10) {
    errors.maxSafeWave = 'Ngưỡng sóng tham chiếu phải là số lớn hơn 0 và không quá 10.0m.';
  }

  // Max safe wind: 1 to 150 km/h
  const wind = Number(form.maxSafeWind);
  if (isNaN(wind) || wind <= 0 || wind > 150) {
    errors.maxSafeWind = 'Ngưỡng gió tham chiếu phải là số từ 1 đến 150 km/h.';
  }

  // Hotline: min 7 chars, only digits/spaces/dashes
  const hotline = form.emergencyHotline.trim();
  if (!hotline) {
    errors.emergencyHotline = 'Đường dây nóng cứu hộ không được để trống.';
  } else if (hotline.length < 7 || hotline.length > 20) {
    errors.emergencyHotline = 'Số hotline phải từ 7 đến 20 ký tự.';
  } else if (!/^[\d\s\-+]+$/.test(hotline)) {
    errors.emergencyHotline = 'Số hotline chỉ được chứa số, khoảng trắng hoặc dấu gạch nối.';
  }

  return errors;
}

export function hasFormChanges(current: SettingsFormData, original: SettingsFormData): boolean {
  return (
    current.commissionRate !== original.commissionRate ||
    current.payoutCycle !== original.payoutCycle ||
    current.maxSafeWave !== original.maxSafeWave ||
    current.maxSafeWind !== original.maxSafeWind ||
    current.emergencyHotline !== original.emergencyHotline
  );
}

export function getChangedKeys(current: SettingsFormData, original: SettingsFormData): (keyof SettingsFormData)[] {
  const changed: (keyof SettingsFormData)[] = [];
  if (current.commissionRate !== original.commissionRate) changed.push('commissionRate');
  if (current.payoutCycle !== original.payoutCycle) changed.push('payoutCycle');
  if (current.maxSafeWave !== original.maxSafeWave) changed.push('maxSafeWave');
  if (current.maxSafeWind !== original.maxSafeWind) changed.push('maxSafeWind');
  if (current.emergencyHotline !== original.emergencyHotline) changed.push('emergencyHotline');
  return changed;
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

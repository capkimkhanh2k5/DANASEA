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

import type { DiscountCode, DiscountType } from '../../../types';

export type PromotionStatusFilter = 'all' | 'active' | 'inactive' | 'expired';
export type PromotionTypeFilter = 'all' | DiscountType;

export function isPromotionExpired(promo: DiscountCode): boolean {
  if (!promo.validTo) return false;
  return new Date(promo.validTo).getTime() < Date.now();
}

export function isPromotionDepleted(promo: DiscountCode): boolean {
  return promo.maxUses > 0 && promo.usedCount >= promo.maxUses;
}

export function filterPromotions(
  promotions: DiscountCode[],
  query: string,
  status: PromotionStatusFilter,
  type: PromotionTypeFilter
): DiscountCode[] {
  const search = query.trim().toUpperCase();
  return promotions.filter(promo => {
    const matchesSearch =
      !search ||
      promo.code.toUpperCase().includes(search) ||
      promo.id.toUpperCase().includes(search);

    const matchesType = type === 'all' || promo.discountType === type;

    let matchesStatus = true;
    if (status === 'active') {
      matchesStatus = promo.isActive && !isPromotionExpired(promo) && !isPromotionDepleted(promo);
    } else if (status === 'inactive') {
      matchesStatus = !promo.isActive && !isPromotionExpired(promo);
    } else if (status === 'expired') {
      matchesStatus = isPromotionExpired(promo) || isPromotionDepleted(promo);
    }

    return matchesSearch && matchesType && matchesStatus;
  });
}

export type PromotionFormData = {
  code: string;
  discountType: DiscountType;
  discountValue: number;
  maxUses: number;
  validFrom: string;
  validTo: string;
  isActive: boolean;
};

/**
 * Lấy ngày hiện tại theo giờ Việt Nam (UTC+7) định dạng YYYY-MM-DD.
 */
export function getVietnamTodayString(): string {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  return formatter.format(new Date());
}

/**
 * Trả về ngày cuối cùng của tháng cho một ngày YYYY-MM-DD bất kỳ.
 * Xử lý chính xác tháng 2 năm nhuận (29 ngày), năm thường (28 ngày), tháng 30 và 31 ngày.
 */
export function getLastDayOfMonth(dateStr: string): string {
  if (!dateStr || !dateStr.includes('-')) return '';
  const parts = dateStr.slice(0, 10).split('-');
  if (parts.length < 2) return '';
  const year = parseInt(parts[0], 10);
  const month = parseInt(parts[1], 10);
  if (isNaN(year) || isNaN(month) || month < 1 || month > 12) return '';

  // Ngày 0 của tháng kế tiếp trong UTC chính là ngày cuối cùng của tháng hiện tại
  const lastDate = new Date(Date.UTC(year, month, 0));
  const lastDay = lastDate.getUTCDate();
  const monthPadded = String(month).padStart(2, '0');
  const dayPadded = String(lastDay).padStart(2, '0');
  return `${year}-${monthPadded}-${dayPadded}`;
}

/**
 * Khi người dùng thay đổi ngày bắt đầu:
 * - Tính lại ngày kết thúc tối thiểu (ngày cuối tháng bắt đầu mới).
 * - Nếu ngày kết thúc hiện tại < ngày tối thiểu, cập nhật thành ngày tối thiểu.
 * - Nếu ngày kết thúc hiện tại đã >= ngày tối thiểu, giữ nguyên giá trị đã chọn.
 */
export function computeNextValidToOnStartChange(newValidFrom: string, currentValidTo: string): string {
  if (!newValidFrom) return currentValidTo;
  const minEnd = getLastDayOfMonth(newValidFrom);
  if (!minEnd) return currentValidTo;
  if (!currentValidTo || currentValidTo < minEnd) {
    return minEnd;
  }
  return currentValidTo;
}

export function validatePromotionForm(data: PromotionFormData): { valid: boolean; error?: string } {
  if (!data.code.trim()) {
    return { valid: false, error: 'Mã ưu đãi không được để trống' };
  }
  if (!/^[A-Z0-9_-]+$/.test(data.code.trim().toUpperCase())) {
    return { valid: false, error: 'Mã ưu đãi chỉ được chứa chữ hoa, số, dấu gạch ngang hoặc gạch dưới' };
  }
  if (data.discountType === 'PERCENTAGE' && (data.discountValue < 1 || data.discountValue > 100)) {
    return { valid: false, error: 'Mức giảm theo phần trăm phải nằm trong khoảng từ 1% đến 100%' };
  }
  if (data.discountType === 'FIXED' && data.discountValue < 1000) {
    return { valid: false, error: 'Mức giảm cố định tối thiểu là 1.000 VNĐ' };
  }
  if (data.maxUses < 1) {
    return { valid: false, error: 'Số lượt sử dụng tối đa phải từ 1 lượt trở lên' };
  }
  if (data.validFrom && data.validTo) {
    const minEndDate = getLastDayOfMonth(data.validFrom);
    if (minEndDate && data.validTo < minEndDate) {
      return {
        valid: false,
        error: `Ngày kết thúc tối thiểu phải là ngày cuối cùng của tháng bắt đầu (${minEndDate})`,
      };
    }
    if (new Date(data.validFrom) > new Date(data.validTo)) {
      return { valid: false, error: 'Thời gian kết thúc phải sau thời gian bắt đầu' };
    }
  }
  return { valid: true };
}

export function savePromotion(
  promotions: DiscountCode[],
  data: PromotionFormData,
  editingId: string | null,
  demo: boolean
): { updatedList: DiscountCode[]; savedItem: DiscountCode | null } {
  if (!demo) return { updatedList: promotions, savedItem: null };

  const now = new Date().toISOString();
  if (editingId) {
    const existing = promotions.find(p => p.id === editingId);
    if (!existing) return { updatedList: promotions, savedItem: null };

    const updatedItem: DiscountCode = {
      ...existing,
      code: data.code.trim().toUpperCase(),
      discountType: data.discountType,
      discountValue: Number(data.discountValue),
      maxUses: Number(data.maxUses),
      validFrom: data.validFrom ? new Date(data.validFrom).toISOString() : existing.validFrom,
      validTo: data.validTo ? new Date(data.validTo).toISOString() : existing.validTo,
      isActive: data.isActive,
      updatedAt: now,
    };

    return {
      updatedList: promotions.map(p => (p.id === editingId ? updatedItem : p)),
      savedItem: updatedItem,
    };
  }

  const newItem: DiscountCode = {
    id: `promo-${Date.now()}`,
    code: data.code.trim().toUpperCase(),
    scope: 'GLOBAL',
    vendorId: '',
    discountType: data.discountType,
    discountValue: Number(data.discountValue),
    maxUses: Number(data.maxUses),
    usedCount: 0,
    validFrom: data.validFrom ? new Date(data.validFrom).toISOString() : now,
    validTo: data.validTo ? new Date(data.validTo).toISOString() : now,
    isActive: data.isActive,
    createdAt: now,
    updatedAt: now,
  };

  return {
    updatedList: [newItem, ...promotions],
    savedItem: newItem,
  };
}

export function togglePromotionActive(
  promotions: DiscountCode[],
  id: string,
  demo: boolean
): { updatedList: DiscountCode[]; targetPromo: DiscountCode | null; nextState: boolean } {
  const target = promotions.find(p => p.id === id);
  if (!demo || !target) {
    return { updatedList: promotions, targetPromo: target || null, nextState: target ? !target.isActive : false };
  }

  const nextActive = !target.isActive;
  const updatedList = promotions.map(p =>
    p.id === id
      ? { ...p, isActive: nextActive, updatedAt: new Date().toISOString() }
      : p
  );

  return {
    updatedList,
    targetPromo: { ...target, isActive: nextActive },
    nextState: nextActive,
  };
}

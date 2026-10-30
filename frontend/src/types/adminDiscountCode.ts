export type DiscountScope = 'PLATFORM' | 'VENDOR';
export type DiscountType = 'PERCENTAGE' | 'FIXED';
export type DiscountSponsorType = 'PLATFORM' | 'VENDOR';

export interface DiscountCodeResponse {
  id: string;
  code: string;
  scope: DiscountScope;
  sponsorType?: DiscountSponsorType;
  vendorId?: string | null;
  serviceId?: string | null;
  discountType: DiscountType;
  discountValue: number;
  minOrderAmount?: number | null;
  maxDiscountAmount?: number | null;
  maxUses?: number | null;
  usedCount?: number;
  maxUsesPerUser?: number | null;
  validFrom?: string | null;
  validTo?: string | null;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
  recipientCustomerId?: string | null;
  replacementSubOrderId?: string | null;
}

export interface CreateDiscountCodeRequest {
  code: string;
  scope: DiscountScope;
  sponsorType?: DiscountSponsorType;
  vendorId?: string | null;
  serviceId?: string | null;
  discountType: DiscountType;
  discountValue: number;
  minOrderAmount?: number | null;
  maxDiscountAmount?: number | null;
  maxUses?: number | null;
  maxUsesPerUser?: number | null;
  validFrom?: string | null;
  validTo?: string | null;
  isActive?: boolean;
}

export interface UpdateDiscountCodeRequest {
  discountValue?: number;
  minOrderAmount?: number | null;
  maxDiscountAmount?: number | null;
  maxUses?: number | null;
  maxUsesPerUser?: number | null;
  validFrom?: string | null;
  validTo?: string | null;
  isActive?: boolean;
}

export interface GetDiscountCodesParams {
  code?: string;
  scope?: DiscountScope;
  vendorId?: string;
  isActive?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface PageDiscountCodeResponse {
  content?: DiscountCodeResponse[];
  totalElements?: number;
  totalPages?: number;
  size?: number;
  number?: number;
}

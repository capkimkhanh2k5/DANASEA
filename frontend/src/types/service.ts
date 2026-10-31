export interface ServiceSummaryDto {
  id: string;
  name: string;
  slug?: string;
  shortDescription?: string;
  price: number;
  promotionalPrice?: number | null;
  averageRating: number;
  reviewCount: number;
  viewCount: number;
  primaryImageUrl?: string;
  address?: string;
}

export interface ServicePageResponse {
  content: ServiceSummaryDto[];
  page: number;
  size: number;
  totalElements: number;
  total: number;
}

export interface ServiceOptionDto {
  id: string;
  serviceId: string;
  name: string;
  optionType: 'SHARED' | 'PRIVATE' | string;
  pricingUnit: string;
  price: number;
  maxPaxPerPackage: number;
  benefits?: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface StructuredSlotDto {
  slotId: string;
  date: string;
  startTime: string;
  endTime: string;
  capacity: number;
  availableCapacity: number;
  price: number;
  optionId?: string;
  pricingUnit?: string;
  maxPaxPerPackage?: number;
  inventoryType?: string;
  bookable: boolean;
}

export interface ServiceDetailDto {
  id: string;
  name: string;
  slug?: string;
  description: string;
  price: number;
  promotionalPrice?: number | null;
  address?: string;
  latitude?: number;
  longitude?: number;
  averageRating: number;
  reviewCount: number;
  viewCount: number;
  categoryId: string;
  categoryName?: string;
  imageUrls: string[];
  availableSlots?: string[];
  options: ServiceOptionDto[];
  vendorId: string;
  businessName?: string;
  badgeTier?: string;
  duration?: number;
  capacity?: number;
  participantConditions?: string;
  waiverRequired: boolean;
  waiverVersion?: number;
  waiverContent?: string;
  waiverLanguage?: string;
  waiverFallbackUsed?: boolean;
  refundPolicy?: string;
  cancellationPolicy?: string;
  safetyRules?: string;
  slots: StructuredSlotDto[];
}

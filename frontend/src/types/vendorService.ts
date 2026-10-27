export type ServiceStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'PUBLISHED'
  | 'PAUSED';

export type OptionType = 'SHARED' | 'PRIVATE';
export type PricingUnit = 'PER_PERSON' | 'PER_PACKAGE';
export type OptionStatus = 'ACTIVE' | 'INACTIVE';
export type InventoryType = 'PERSON_LIMIT' | 'SHARED_CAPACITY_UNITS';
export type SlotStatus = 'OPEN' | 'CLOSED';
export type SafetyDocStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ServiceImageDto {
  id: string;
  serviceId: string;
  url: string;
  sortOrder: number;
  createdAt: string;
}

export interface VendorServiceDto {
  id: string;
  vendorId: string;
  categoryId: string;
  name: string;
  nameEn: string | null;
  slug: string;
  description: string;
  descriptionEn: string | null;
  price: number;
  durationMinutes: number;
  capacityPerSlot: number;
  locationName: string;
  address: string;
  latitude: number | null;
  longitude: number | null;
  status: ServiceStatus;
  rejectionReason: string | null;
  waiverContent: string | null;
  waiverContentEn: string | null;
  waiverRequired: boolean;
  waiverVersion: number;
  weatherSensitive: boolean;
  minWindKmh: number | null;
  maxWaveM: number | null;
  avgRating: number | null;
  ratingCount: number;
  viewCount: number;
  images: ServiceImageDto[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateServiceRequest {
  categoryId: string;
  name: string;
  nameEn?: string;
  description?: string;
  descriptionEn?: string;
  price: number;
  durationMinutes: number;
  capacityPerSlot?: number;
  locationName?: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  waiverContent?: string;
  waiverContentEn?: string;
  waiverRequired?: boolean;
  weatherSensitive?: boolean;
  minWindKmh?: number;
  maxWaveM?: number;
}

export interface UpdateServiceRequest {
  categoryId?: string;
  name?: string;
  nameEn?: string;
  description?: string;
  descriptionEn?: string;
  price?: number;
  durationMinutes?: number;
  capacityPerSlot?: number;
  locationName?: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  waiverContent?: string;
  waiverContentEn?: string;
  waiverRequired?: boolean;
  weatherSensitive?: boolean;
  minWindKmh?: number;
  maxWaveM?: number;
}

export interface VendorServiceOptionDto {
  id: string;
  serviceId: string;
  name: string;
  optionType: OptionType;
  pricingUnit: PricingUnit;
  price: number;
  maxPaxPerPackage: number | null;
  benefits: string | null;
  status: OptionStatus;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateServiceOptionRequest {
  name: string;
  optionType: OptionType;
  pricingUnit: PricingUnit;
  price: number;
  maxPaxPerPackage?: number | null;
  benefits?: string | null;
}

export interface UpdateServiceOptionRequest {
  name?: string;
  price?: number;
  maxPaxPerPackage?: number | null;
  benefits?: string | null;
  status?: OptionStatus;
}

export interface SafetyDocumentDto {
  id: string;
  serviceId: string;
  fileUrl: string;
  status: SafetyDocStatus;
  reviewedBy: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
}

export interface SlotUnitDto {
  id: string;
  unitNumber: number;
  capacity: number;
  bookedCount: number;
  availableCapacity: number;
}

export interface CreateSlotUnitRequest {
  unitNumber: number;
  capacity: number;
}

export interface VendorServiceSlotDto {
  id: string;
  serviceId: string;
  date: string;
  startTime: string;
  endTime: string;
  status: SlotStatus;
  inventoryType: InventoryType;
  capacity: number;
  bookedCount: number;
  availableCapacity: number;
  units?: SlotUnitDto[];
}

export interface CreateServiceSlotRequest {
  date: string;
  startTime: string;
  endTime: string;
  inventoryType: InventoryType;
  capacity?: number;
  units?: CreateSlotUnitRequest[];
}

export interface UpdateServiceSlotRequest {
  status?: SlotStatus;
  capacity?: number;
  units?: CreateSlotUnitRequest[];
}

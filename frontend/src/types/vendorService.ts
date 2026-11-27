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
export type SlotStatus = 'OPEN' | 'CLOSED' | 'BLOCKED' | 'CANCELLED';
export type SafetyDocStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'SUPERSEDED';

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

export interface ServiceSlotOptionSummaryDto {
  id: string;
  name: string;
  optionType: OptionType;
  pricingUnit: PricingUnit;
  price: number;
  maxPaxPerPackage?: number | null;
}

export interface SlotUnitDto {
  id: string;
  unitNumber: number;
  capacity: number;
  bookedCount: number;
  availableCapacity: number;
  equipmentId?: string | null;
  equipmentBlocked?: boolean;
}

export interface CreateSlotUnitRequest {
  unitNumber: number;
  capacity: number;
  equipmentId?: string | null;
  detachEquipment?: boolean;
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
  options?: ServiceSlotOptionSummaryDto[];
  optionBindingMode?: string | null;
  legacyCompatibleOptions?: ServiceSlotOptionSummaryDto[];
  optionId?: string | null;
  optionName?: string | null;
  optionType?: OptionType | null;
}

export interface CreateServiceSlotRequest {
  date: string;
  startTime: string;
  endTime: string;
  inventoryType: InventoryType;
  capacity?: number;
  units?: CreateSlotUnitRequest[];
  optionId?: string;
  optionIds: string[];
}

export interface UpdateServiceSlotRequest {
  status?: SlotStatus;
  capacity?: number;
  units?: CreateSlotUnitRequest[];
  optionIds?: string[];
}

export interface SlotTemplateRequest {
  startTime: string; // "HH:mm:ss"
  endTime: string;   // "HH:mm:ss"
  inventoryType: InventoryType;
  capacity?: number | null;
  units?: CreateSlotUnitRequest[] | null;
  optionIds: string[];
}

export type BatchCreateServiceSlotsRequest = (
  | { dates: string[]; startDate?: never; endDate?: never; daysOfWeek?: never }
  | { dates?: never; startDate: string; endDate: string; daysOfWeek: string[] }
) & {
  slotTemplates: SlotTemplateRequest[];
};

export interface BatchCreateServiceSlotsResponse {
  totalCreated: number;
  slots: VendorServiceSlotDto[];
}

export interface OperatingScheduleWeeklyRuleDto {
  dayOfWeek: string; // "MONDAY".."SUNDAY"
  startTime: string; // "HH:mm:ss"
  endTime: string;   // "HH:mm:ss"
  inventoryType: InventoryType;
  capacity?: number | null;
  units?: { unitNumber: number; capacity: number; equipmentId?: string | null }[] | null;
  optionIds: string[];
}

export interface OperatingScheduleDto {
  startsOn: string;
  endsOn?: string | null;
  active: boolean;
  rules: OperatingScheduleWeeklyRuleDto[];
}

export interface PutOperatingScheduleRequest {
  startsOn: string;
  endsOn?: string | null;
  active: boolean;
  rules: OperatingScheduleWeeklyRuleDto[];
}

export interface OperatingScheduleUpdateResponse {
  schedule: OperatingScheduleDto;
  createdSlots: number;
  closedSlots: number;
}

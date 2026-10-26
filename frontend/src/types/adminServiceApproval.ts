export type AdminServiceStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'PAUSED';
export type SafetyDocumentStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ServiceImageResult {
  id: string;
  serviceId: string;
  url: string;
  sortOrder?: number;
}

export interface AdminServiceResult {
  id: string;
  vendorId: string;
  categoryId: string;
  name: string;
  nameEn?: string | null;
  slug: string;
  description?: string | null;
  descriptionEn?: string | null;
  price: number;
  durationMinutes: number;
  capacityPerSlot: number;
  locationName: string;
  address: string;
  latitude: number;
  longitude: number;
  status: AdminServiceStatus;
  rejectionReason?: string | null;
  waiverContent?: string | null;
  waiverContentEn?: string | null;
  waiverRequired?: boolean;
  waiverVersion?: number;
  weatherSensitive?: boolean;
  minWindKmh?: number | null;
  maxWaveM?: number | null;
  avgRating?: number;
  ratingCount?: number;
  viewCount?: number;
  images?: ServiceImageResult[];
  createdAt: string;
  updatedAt: string;
}

export interface SafetyDocumentResponse {
  id: string;
  serviceId: string;
  fileUrl: string;
  status: SafetyDocumentStatus;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  rejectionReason?: string | null;
  createdAt: string;
}

export interface RejectServiceRequest {
  reason: string;
}

export interface RejectSafetyDocumentRequest {
  rejectionReason: string;
}

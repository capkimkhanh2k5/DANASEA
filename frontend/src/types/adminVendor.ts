import type { BadgeTier } from './index';

export type VendorVerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type VendorDocStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type VendorDocType = 'BUSINESS_LICENSE' | 'SAFETY_CERT' | string;

export interface VendorDocumentResponse {
  id: string;
  vendorId: string;
  docType: VendorDocType;
  fileUrl: string;
  status: VendorDocStatus;
  reviewedBy: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminVendorResponse {
  id: string;
  userId: string;
  businessName: string;
  taxCode: string;
  address: string;
  bankAccountNumber: string;
  bankName: string;
  bankAccountHolder: string;
  verificationStatus: VendorVerificationStatus;
  verifiedBy: string | null;
  verifiedAt: string | null;
  ratingAvg: number;
  ratingCount: number;
  badgeTier: BadgeTier;
  createdAt: string;
  updatedAt: string;
  documents?: VendorDocumentResponse[];
}

export interface RejectVendorRequest {
  reason: string;
}

export interface RejectVendorDocumentRequest {
  reason: string;
}

export interface AdminVendorQueryParams {
  status?: VendorVerificationStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

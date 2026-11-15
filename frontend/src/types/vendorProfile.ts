import type { BadgeTier } from './index';

export type VerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type DocType = 'BUSINESS_LICENSE' | 'SAFETY_CERT';
export type DocStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface VendorProfileDto {
  id: string;
  userId: string;
  businessName: string;
  taxCode: string;
  address: string;
  bankAccountNumber: string;
  bankName: string;
  bankAccountHolder: string;
  verificationStatus: VerificationStatus;
  badgeTier: BadgeTier;
  ratingAvg: number;
  ratingCount: number;
  createdAt: string;
  updatedAt: string;
  rejectionReason?: string | null;
}

export interface UpdateVendorProfileRequest {
  businessName?: string;
  address?: string;
  phone?: string;
  bankAccountNumber?: string;
  bankName?: string;
  bankAccountHolder?: string;
}

export interface VendorDocumentDto {
  id: string;
  vendorId: string;
  docType: DocType;
  fileUrl: string;
  status: DocStatus;
  reviewedBy: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

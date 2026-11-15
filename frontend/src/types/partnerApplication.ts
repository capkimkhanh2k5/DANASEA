export type PartnerApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface PartnerApplicationResponse {
  id: string;
  userId: string;
  businessName: string;
  taxCode: string;
  address: string;
  bankAccountNumber: string;
  bankName: string;
  bankAccountHolder: string;
  status: PartnerApplicationStatus;
  rejectionReason: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SubmitPartnerApplicationRequest {
  businessName: string;
  taxCode: string;
  address: string;
  bankAccountNumber: string;
  bankName: string;
  bankAccountHolder: string;
}

export interface ResubmitPartnerApplicationRequest {
  businessName: string;
  taxCode: string;
  address: string;
  bankAccountNumber: string;
  bankName: string;
  bankAccountHolder: string;
}

export interface AdminRejectApplicationRequest {
  rejectionReason: string;
}

export interface PartnerApplicationQueryParams {
  status?: PartnerApplicationStatus;
  search?: string;
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

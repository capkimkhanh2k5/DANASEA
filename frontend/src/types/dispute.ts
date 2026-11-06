export type DisputeReason =
  | 'SERVICE_NOT_AS_DESCRIBED'
  | 'VENDOR_NO_SHOW'
  | 'SAFETY_CONCERN'
  | 'PAYMENT_ISSUE'
  | 'OTHER';

export type DisputeStatus =
  | 'OPEN'
  | 'UNDER_REVIEW'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'RESOLVED_REFUND'
  | 'RESOLVED_REJECTED'
  | 'RESOLVED_PARTIAL'
  | 'CLOSED';

export interface CreateDisputeRequest {
  subOrderId: string;
  reason: DisputeReason;
  description: string;
  evidenceUrls?: string[];
}

export interface SubmitVendorResponseRequest {
  response: string;
  evidenceUrls?: string[];
}

export interface DisputeResponse {
  id: string;
  orderId: string;
  subOrderId: string;
  customerId: string;
  reason: DisputeReason;
  description: string;
  evidenceUrls?: string[];
  status: DisputeStatus;
  refundPercentage?: number;
  adminNote?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  createdAt: string;
  updatedAt?: string;
  vendorResponse?: string;
  vendorEvidenceUrls?: string[];
  vendorRespondedAt?: string;
}

export interface DisputePageResponse {
  content: DisputeResponse[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

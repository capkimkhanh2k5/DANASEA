export type DisputeStatus =
  | 'OPEN'
  | 'UNDER_REVIEW'
  | 'RESOLVED_REFUND'
  | 'RESOLVED_REJECTED'
  | 'RESOLVED_PARTIAL';

export type DisputeReason =
  | 'SERVICE_NOT_AS_DESCRIBED'
  | 'VENDOR_NO_SHOW'
  | 'SAFETY_CONCERN'
  | 'PAYMENT_ISSUE'
  | 'OTHER'
  | string;

export interface DisputeResponse {
  id: string;
  orderId: string;
  subOrderId: string;
  customerId: string;
  reason: DisputeReason;
  description: string;
  evidenceUrls?: string[];
  status: DisputeStatus;
  refundPercentage?: number | null;
  adminNote?: string | null;
  resolvedBy?: string | null;
  resolvedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  vendorResponse?: string | null;
  vendorEvidenceUrls?: string[] | null;
  vendorRespondedAt?: string | null;
}

export interface ResolveDisputeRequest {
  resolution: DisputeStatus;
  refundPercentage?: number | null;
  adminNote?: string | null;
}

export interface AdminDisputeQueryParams {
  status?: DisputeStatus;
  reason?: DisputeReason;
  page?: number;
  size?: number;
  sort?: string;
}

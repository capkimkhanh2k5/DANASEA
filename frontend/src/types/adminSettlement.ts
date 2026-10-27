export type SettlementStatus = 'DRAFT' | 'PENDING' | 'FINALIZED' | 'PAID';

export interface SettlementLineItemResponse {
  id: string;
  subOrderId: string;
  grossAmount: number;
  refundAmount?: number;
  commissionRate: number;
  commissionAmount: number;
  netAmount: number;
  excludedReason?: string | null;
  isExcluded?: boolean;
}

export interface SettlementResponse {
  id: string;
  vendorId: string;
  periodStart: string;
  periodEnd: string;
  grossAmount: number;
  commissionAmount: number;
  netPayableAmount: number;
  status: SettlementStatus;
  generatedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SettlementDetailResponse {
  id: string;
  vendorId: string;
  periodStart: string;
  periodEnd: string;
  grossAmount: number;
  commissionAmount: number;
  netPayableAmount: number;
  status: SettlementStatus;
  generatedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  lineItems: SettlementLineItemResponse[];
}

export interface GenerateSettlementRequest {
  vendorId: string;
  periodStart: string;
  periodEnd: string;
}

export interface AdminSettlementQueryParams {
  vendorId?: string;
  status?: SettlementStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

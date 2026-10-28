export type MasterOrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED';
export type PaymentOrderStatus = 'PENDING' | 'PAID' | 'REFUNDED' | 'FAILED';
export type SubOrderStatus =
  | 'PENDING'
  | 'PAID'
  | 'CONFIRMED'
  | 'ATTENDED'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'NO_SHOW';

export type PaymentProvider = 'VIETQR' | 'SEPAY' | 'VNPAY' | string;
export type RefundStatus = 'PENDING' | 'PROCESSED' | 'FAILED';
export type RefundReason =
  | 'CUSTOMER_REQUEST'
  | 'CUSTOMER_CANCEL'
  | 'WEATHER_DISRUPTION'
  | 'ADMIN_OVERRIDE'
  | string;

export interface AdminOrderSummaryResponse {
  id: string;
  bookingId: string;
  customerId: string;
  status: MasterOrderStatus;
  paymentStatus: PaymentOrderStatus;
  totalAmount: number;
  discountAmount: number;
  totalItems: number;
  vendorIds: string[];
  paymentDeadline?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SubOrderResponse {
  id: string;
  vendorId: string;
  serviceId: string;
  slotId: string;
  quantity: number;
  unitPrice: number;
  subtotalAmount: number;
  status: SubOrderStatus;
  discountAmount?: number;
  finalAmount?: number;
  waiverRequired?: boolean;
}

export interface OrderResponse {
  id: string;
  bookingId: string;
  customerId: string;
  status: MasterOrderStatus;
  totalAmount: number;
  discountAmount: number;
  items: SubOrderResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface PaymentResponse {
  id: string;
  masterOrderId: string;
  provider: PaymentProvider;
  providerTransactionId?: string | null;
  amount: number;
  status: PaymentOrderStatus;
  paymentUrl?: string | null;
  qrCodeUrl?: string | null;
  createdAt: string;
  updatedAt: string;
  lastError?: string | null;
}

export interface RefundDetailResponse {
  id: string;
  subOrderId: string;
  amount: number;
  percentage?: number | null;
  reason: RefundReason;
  status: RefundStatus;
  provider?: PaymentProvider | null;
  providerRefundId?: string | null;
  providerTransactionId?: string | null;
  createdAt: string;
  processedAt?: string | null;
  lastError?: string | null;
}

export interface AdminOrderQueryParams {
  status?: MasterOrderStatus;
  paymentStatus?: PaymentOrderStatus;
  vendorId?: string;
  customerId?: string;
  fromDate?: string;
  toDate?: string;
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

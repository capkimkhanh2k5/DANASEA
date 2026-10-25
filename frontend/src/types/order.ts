export interface CreateOrderRequest {
  bookingId: string;
  discountCode?: string;
}

export interface SubOrderResponse {
  subOrderId: string;
  vendorId: string;
  serviceId: string;
  slotId: string;
  quantity: number;
  unitPrice: number;
  subtotalAmount: number;
  status: string;
  waiverAccepted: boolean;
}

export interface OrderResponse {
  id: string;
  customerId: string;
  status: string;
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
  createdAt: string;
  subOrders: SubOrderResponse[];
}

export interface OrderPageResponse<T = OrderResponse> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DiscountPreviewRequest {
  bookingId: string;
  code: string;
}

export interface ItemDiscountPreviewResponse {
  subOrderId?: string;
  serviceId?: string;
  originalAmount: number;
  discountAmount: number;
  finalAmount: number;
}

export interface DiscountPreviewResponse {
  valid: boolean;
  errorCode?: string;
  message?: string;
  code?: string;
  scope?: string;
  sponsorType?: string;
  originalTotalAmount: number;
  totalDiscountAmount: number;
  finalPayableAmount: number;
  itemBreakdown?: ItemDiscountPreviewResponse[];
}

export type PaymentProviderType = 'VNPAY' | 'MOMO' | 'SEPAY' | 'PAYPAL';

export interface CreatePaymentIntentRequest {
  provider: PaymentProviderType;
}

export interface PaymentIntentResponse {
  paymentId: string;
  orderId: string;
  provider: string;
  amount: number;
  status: string;
  paymentUrl: string;
  expiresAt: string;
}

export interface PaymentResponse {
  paymentId: string;
  orderId: string;
  provider: string;
  amount: number;
  status: string;
  createdAt: string;
}

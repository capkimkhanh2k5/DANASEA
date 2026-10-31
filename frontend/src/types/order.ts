export type MasterOrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'PARTIALLY_COMPLETED'
  | 'COMPLETED'
  | 'CANCELLED';

export type SubOrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'REJECTED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED'
  | 'CHECKED_IN'
  | 'IN_PROGRESS';

export interface CreateOrderRequest {
  bookingId: string;
  discountCode?: string;
}

export interface SubOrderResponse {
  id: string;
  subOrderId?: string;
  vendorId?: string;
  serviceId: string;
  slotId?: string;
  quantity: number;
  unitPrice: number;
  subtotalAmount: number;
  status: SubOrderStatus | string;
  discountAmount?: number;
  finalAmount?: number;
  waiverRequired?: boolean;
  waiverVersion?: number;
  waiverContent?: string;
  waiverLanguage?: string;
  waiverFallbackUsed?: boolean;
  waiverAccepted?: boolean;
  waiverAcceptedAt?: string;
  waiverAcceptedBy?: string;
  waiverAcceptedLanguage?: string;
  cancellationPolicyVersion?: string;
  // UI enrichment fields (joined from service catalog/slots if needed)
  serviceName?: string;
  serviceThumbnail?: string;
  serviceLocation?: string;
  vendorName?: string;
  slotDate?: string;
  slotTime?: string;
}

export interface OrderResponse {
  id: string;
  customerId?: string;
  bookingId?: string;
  status: MasterOrderStatus | string;
  totalAmount: number;
  discountAmount?: number;
  finalAmount?: number;
  createdAt: string;
  updatedAt?: string;
  items: SubOrderResponse[];
  subOrders?: SubOrderResponse[];
}

export interface AcceptSubOrderWaiverRequest {
  accepted: boolean;
  version: number;
  language: string;
}

export interface SubOrderWaiverAcceptanceResponse {
  subOrderId: string;
  masterOrderId: string;
  accepted: boolean;
  waiverVersion: number;
  language: string;
  acceptedAt: string;
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
  paymentReference: string;
  paymentUrl?: string;
  createdAt?: string;
  expiresAt?: string;
}

export interface PaymentResponse {
  paymentId: string;
  orderId: string;
  provider: string;
  amount: number;
  status: string;
  createdAt: string;
}

export interface SubOrderCancellationPreview {
  subOrderId: string;
  serviceId: string;
  slotId: string;
  departureTime?: string;
  originalAmount: number;
  refundPercentage: number;
  refundAmount: number;
  policyApplied: string;
}

export interface CancellationPreviewResponse {
  orderId: string;
  originalAmount: number;
  refundPercentage: number;
  refundAmount: number;
  policyApplied: string;
  items: SubOrderCancellationPreview[];
}

export type RefundReason =
  | 'CUSTOMER_REQUEST'
  | 'CUSTOMER_CANCEL'
  | 'WEATHER'
  | 'VENDOR_FAULT'
  | 'SYSTEM_FAULT'
  | 'ADMIN_OVERRIDE'
  | 'DISPUTE'
  | 'COMPENSATION';

export interface RefundRequest {
  reason?: RefundReason;
}

export interface RefundResponse {
  id?: string;
  refundId?: string;
  subOrderId: string;
  amount: number;
  percentage?: number;
  refundPercentage?: number;
  reason: string;
  status: string;
  provider?: string;
  providerRefundId?: string;
  providerTransactionId?: string;
  processedAt?: string;
  createdAt?: string;
  lastError?: string;
}

export interface OrderReceiptItemResponse {
  subOrderId: string;
  serviceName: string;
  serviceNameEn?: string;
  quantity: number;
  unitPrice: number;
  subtotalAmount: number;
  discountAmount: number;
  finalAmount: number;
  departureTime?: string;
  vendorName?: string;
  locationName?: string;
}

export interface OrderReceiptResponse {
  orderId: string;
  orderCode: string;
  receiptCode: string;
  customerId: string;
  totalAmount: number;
  discountAmount: number;
  status: string;
  paidAt?: string;
  paymentProvider?: string;
  items: OrderReceiptItemResponse[];
}

export interface QrCodeResponse {
  subOrderId: string;
  qrToken: string;
  expiresAt: string;
}

export interface RescheduleOptionItem {
  slotId: string;
  bookingDate: string;
  startTime: string;
  endTime: string;
  availableCapacity: number;
  canAccommodate: boolean;
  accommodateNote: string;
  referencePrice: number;
  additionalFee: number;
  isProposedByVendor: boolean;
  proposalId: string | null;
  proposalExpiresAt: string | null;
}

export interface RescheduleOptionsSummaryResponse {
  subOrderId: string;
  currentSlotId: string;
  currentBookingDate: string;
  currentBookingTime: string;
  serviceId: string;
  serviceName: string;
  quantity: number;
  pricingUnit: string | null;
  rescheduleVersion: number;
  rescheduleAllowed: boolean;
  eligibilityReason: string;
  options: RescheduleOptionItem[];
  decisionDeadline: string | null;
  fullRefundAvailable: boolean;
  disruptionReason: string | null;
}

export interface ConfirmRescheduleRequest {
  targetSlotId: string;
  proposalId?: string | null;
  expectedVersion: number;
}

export interface SubOrderRescheduleResponse {
  subOrderId: string;
  historyId: string;
  fromSlotId: string;
  toSlotId: string;
  fromBookingDate: string;
  fromBookingTime: string;
  toBookingDate: string;
  toBookingTime: string;
  rescheduledAt: string;
  status: string;
  rescheduleVersion: number;
}

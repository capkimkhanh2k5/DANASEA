export interface BookingHoldItemRequest {
  slotId: string;
  quantity: number;
  optionId?: string;
  participantsCount?: number;
  allowSplit?: boolean;
}

export interface BookingHoldRequest {
  items: BookingHoldItemRequest[];
}

export interface BookingHoldItemResponse {
  id: string;
  serviceId: string;
  vendorId: string;
  slotId: string;
  quantity: number;
  bookingDate: string;
  bookingTime: string;
  price: number;
  subtotal: number;
}

export interface BookingHoldResponse {
  bookingId: string;
  customerId: string;
  status: string;
  totalAmount: number;
  expiresAt?: string;
  holdExpiresAt?: string;
  items: BookingHoldItemResponse[];
}

export interface BookingDetailResponse {
  bookingId: string;
  customerId: string;
  status: string;
  totalAmount: number;
  expiresAt?: string;
  holdExpiresAt?: string;
  items: BookingHoldItemResponse[];
  createdAt?: string;
  updatedAt?: string;
}

export interface BookingSummaryResponse {
  bookingId: string;
  customerId: string;
  status: string;
  totalAmount: number;
  totalItems: number;
  holdExpiresAt?: string;
  createdAt?: string;
}

export interface CancelBookingRequest {
  reason?: string;
}

export interface BookingCancelResponse {
  bookingId: string;
  status: string;
  cancelledAt: string;
  earliestServiceTime?: string;
  refundEligible: boolean;
  refundPercentage: number;
  refundAmount: number;
  cancellationReason?: string;
  message?: string;
}


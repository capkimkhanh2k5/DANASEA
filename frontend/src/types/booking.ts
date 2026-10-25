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
  expiresAt: string;
  items: BookingHoldItemResponse[];
}

export interface BookingDetailResponse {
  bookingId: string;
  customerId: string;
  status: string;
  totalAmount: number;
  expiresAt: string;
  items: BookingHoldItemResponse[];
}

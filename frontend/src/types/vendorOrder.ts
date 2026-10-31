/**
 * DTOs cho phân hệ Quản lý Đơn hàng & Quét mã QR Check-in của Vendor
 * Khớp 100% với Backend Java:
 * - SubOrderResponse.java & OrderPageResponse.java (modules/order)
 * - VendorBookingItemResponse.java & PageResponse.java (modules/booking)
 * - VerifyCheckinRequest.java & VerifyCheckinResponse.java (modules/checkin)
 * - CreateRescheduleProposalRequest.java & RescheduleProposalResponse.java (modules/order)
 */

export type SubOrderStatus =
  | 'PENDING'
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'REFUNDED';

export type BookingStatus =
  | 'HOLD'
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'CANCELLED'
  | 'REJECTED';

// Đơn hàng con (SubOrder)
export interface VendorSubOrderDto {
  id: string;
  vendorId: string;
  serviceId: string;
  slotId?: string | null;
  quantity: number;
  unitPrice: number;
  subtotalAmount: number;
  status: SubOrderStatus;
  discountAmount?: number;
  finalAmount?: number;
  waiverRequired?: boolean;
  waiverAccepted?: boolean;
  waiverAcceptedAt?: string | null;
  checkedInAt?: string | null;
  serviceTitle?: string;
  customerName?: string;
  customerPhone?: string;
  customerEmail?: string;
  dateStr?: string;
  timeSlot?: string;
}

// Phân trang đơn hàng
export interface OrderPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// Phân trang danh sách vé / lượt đặt
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first?: boolean;
  last?: boolean;
}

// Chi tiết lượt đặt vé (Booking Item) của Vendor
export interface VendorBookingItemDto {
  itemId: string;
  bookingId: string;
  serviceId: string;
  vendorId: string;
  slotId?: string | null;
  quantity: number;
  bookingDate: string; // YYYY-MM-DD
  bookingTime: string; // HH:mm:ss
  price: number;
  subtotal: number;
  bookingStatus: BookingStatus;
  createdAt: string;
}

// Tham số truy vấn danh sách Bookings
export interface GetVendorBookingsParams {
  status?: BookingStatus;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

// Request xác thực check-in mã QR
export interface VerifyCheckinRequest {
  qrToken: string;
}

// Response xác thực check-in
export interface VerifyCheckinResponse {
  subOrderId: string;
  vendorId: string;
  serviceId: string;
  slotId?: string | null;
  quantity: number;
  status: SubOrderStatus;
  checkedInAt?: string | null;
  verifiedByStaffId?: string | null;
  message?: string;
}

// Request từ chối lượt đặt
export interface VendorRejectBookingRequest {
  reason: string;
}

// Request gửi đề xuất đổi ca cho khách
export interface CreateRescheduleProposalRequest {
  proposedSlotId: string;
  reason: string;
  reasonType?: 'OPERATIONAL' | 'WEATHER'; // Mặc định OPERATIONAL
  expiresAt?: string | null;
}

// Response kết quả đề xuất đổi ca
export interface RescheduleProposalResponse {
  id: string;
  subOrderId: string;
  vendorId: string;
  proposedSlotId: string;
  proposedBookingDate?: string;
  proposedStartTime?: string;
  proposedEndTime?: string;
  reason: string;
  reasonType: string;
  status: string;
  proposalVersion: number;
  expiresAt?: string;
  createdAt: string;
}

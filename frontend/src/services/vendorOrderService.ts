import { apiClient } from './apiClient';
import type {
  VendorSubOrderDto,
  OrderPageResponse,
  VendorBookingItemDto,
  PageResponse,
  GetVendorBookingsParams,
  VerifyCheckinRequest,
  VerifyCheckinResponse,
  VendorRejectBookingRequest,
  CreateRescheduleProposalRequest,
  RescheduleProposalResponse,
} from '../types/vendorOrder';

export const vendorOrderService = {
  /**
   * V10: Lấy danh sách các đơn hàng con (Sub-orders) thuộc quyền quản lý của Vendor
   */
  async getVendorOrders(page = 0, size = 20): Promise<OrderPageResponse<VendorSubOrderDto>> {
    const response = await apiClient.get<OrderPageResponse<VendorSubOrderDto>>('/vendor/orders', {
      params: { page, size },
    });
    return response.data;
  },

  /**
   * V10: Lấy danh sách chi tiết các lượt đặt vé (Booking Items) của Vendor
   */
  async getVendorBookings(params?: GetVendorBookingsParams): Promise<PageResponse<VendorBookingItemDto>> {
    const response = await apiClient.get<PageResponse<VendorBookingItemDto>>('/vendor/bookings', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? 20,
        status: params?.status,
        sortBy: params?.sortBy ?? 'createdAt',
        sortDir: params?.sortDir ?? 'desc',
      },
    });
    return response.data;
  },

  /**
   * V12: Quét hoặc nhập mã vé QR điện tử để xác thực check-in tại bến
   */
  async verifyCheckin(qrToken: string): Promise<VerifyCheckinResponse> {
    const payload: VerifyCheckinRequest = { qrToken: qrToken.trim() };
    const response = await apiClient.post<VerifyCheckinResponse>('/vendor/checkin/verify', payload);
    return response.data;
  },

  /**
   * V11: Từ chối lượt đặt vé của khách hàng kèm lý do
   */
  async rejectBooking(
    bookingItemId: string,
    reason: string,
    idempotencyKey?: string
  ): Promise<VendorSubOrderDto> {
    const payload: VendorRejectBookingRequest = { reason: reason.trim() };
    const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined;
    const response = await apiClient.patch<VendorSubOrderDto>(
      `/vendor/bookings/${bookingItemId}/reject`,
      payload,
      { headers }
    );
    return response.data;
  },

  /**
   * V11: Vendor gửi đề xuất đổi sang ca khởi hành mới do lý do vận hành / thời tiết
   */
  async createRescheduleProposal(
    subOrderId: string,
    request: CreateRescheduleProposalRequest
  ): Promise<RescheduleProposalResponse> {
    const response = await apiClient.post<RescheduleProposalResponse>(
      `/vendor/sub-orders/${subOrderId}/reschedule-proposals`,
      request
    );
    return response.data;
  },
};

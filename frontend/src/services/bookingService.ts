import { apiClient } from './apiClient';
import type {
  BookingHoldRequest,
  BookingHoldResponse,
  BookingDetailResponse,
  BookingSummaryResponse,
} from '../types/booking';
import { flowLogger } from '../utils/flowLogger';

export const bookingService = {
  /**
   * Hold slots for checkout (15-minute reservation window)
   * POST /api/bookings/hold
   */
  async holdBooking(request: BookingHoldRequest): Promise<BookingHoldResponse> {
    flowLogger.start({
      step: '03_HOLD',
      stepName: 'Giữ chỗ 15 phút (Hold Booking)',
      operation: 'POST /api/bookings/hold',
      payload: request,
    });

    try {
      const response = await apiClient.post<BookingHoldResponse>('/bookings/hold', request);
      flowLogger.success(
        {
          step: '03_HOLD',
          stepName: 'Giữ chỗ 15 phút (Hold Booking)',
          operation: 'POST /api/bookings/hold',
        },
        {
          bookingId: response.data.bookingId,
          status: response.data.status,
          holdExpiresAt: response.data.holdExpiresAt || response.data.expiresAt,
          totalAmount: response.data.totalAmount,
        }
      );
      return response.data;
    } catch (err) {
      flowLogger.error(
        {
          step: '03_HOLD',
          stepName: 'Giữ chỗ 15 phút (Hold Booking)',
          operation: 'POST /api/bookings/hold',
          payload: request,
        },
        err
      );
      throw err;
    }
  },

  /**
   * Confirm booking from hold
   * POST /api/bookings/{holdId}/confirm
   */
  async confirmBooking(holdId: string): Promise<BookingHoldResponse> {
    const response = await apiClient.post<BookingHoldResponse>(`/bookings/${holdId}/confirm`);
    return response.data;
  },

  /**
   * Cancel booking hold
   * DELETE /api/bookings/hold/{holdId}
   */
  async cancelBookingHold(holdId: string): Promise<void> {
    await apiClient.delete(`/bookings/hold/${holdId}`);
  },

  /**
   * Get booking detail
   * GET /api/bookings/{id}
   */
  async getBookingDetail(id: string): Promise<BookingDetailResponse> {
    const response = await apiClient.get<BookingDetailResponse>(`/bookings/${id}`);
    return response.data;
  },

  /**
   * Get customer bookings
   * GET /api/bookings
   */
  async getMyBookings(params?: {
    status?: string;
    page?: number;
    size?: number;
    sortBy?: string;
    sortDir?: string;
  }): Promise<{ content: BookingSummaryResponse[]; totalElements: number; totalPages: number }> {
    const response = await apiClient.get('/bookings', { params });
    return response.data;
  },
};

import { apiClient } from './apiClient';
import type {
  BookingHoldRequest,
  BookingHoldResponse,
  BookingDetailResponse,
  BookingSummaryResponse,
  CancelBookingRequest,
  BookingCancelResponse,
} from '../types/booking';

export const bookingService = {
  /**
   * Hold slots for checkout (15-minute reservation window)
   * POST /api/bookings/hold
   */
  async holdBooking(request: BookingHoldRequest): Promise<BookingHoldResponse> {
    const response = await apiClient.post<BookingHoldResponse>('/bookings/hold', request);
    return response.data;
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
    const response = await apiClient.get('/api/bookings', { params });
    return response.data;
  },

  /**
   * Cancel confirmed booking
   * PATCH /api/bookings/{id}/cancel
   */
  async cancelBooking(
    bookingId: string,
    request?: CancelBookingRequest,
    idempotencyKey?: string
  ): Promise<BookingCancelResponse> {
    const key = idempotencyKey || `cancel-${bookingId}`;
    const response = await apiClient.patch<BookingCancelResponse>(
      `/bookings/${bookingId}/cancel`,
      request || {},
      {
        headers: {
          'Idempotency-Key': key,
        },
      }
    );
    return response.data;
  },
};


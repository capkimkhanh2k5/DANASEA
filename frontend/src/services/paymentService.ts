import { apiClient } from './apiClient';
import type {
  CreatePaymentIntentRequest,
  PaymentIntentResponse,
  PaymentResponse,
} from '../types/order';

export const paymentService = {
  /**
   * Create payment intent with selected provider (VNPAY, MOMO, SEPAY, PAYPAL)
   * POST /api/payments/{orderId}/create-intent
   */
  async createPaymentIntent(
    orderId: string,
    request: CreatePaymentIntentRequest,
    idempotencyKey?: string
  ): Promise<PaymentIntentResponse> {
    const key =
      idempotencyKey ||
      (typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `pay-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`);

    const response = await apiClient.post<PaymentIntentResponse>(
      `/payments/${orderId}/create-intent`,
      request,
      {
        headers: {
          'Idempotency-Key': key,
        },
      }
    );
    return response.data;
  },

  /**
   * Get payment status by order ID
   * GET /api/payments/{orderId}/status
   */
  async getPaymentStatus(orderId: string): Promise<PaymentResponse[]> {
    const response = await apiClient.get<PaymentResponse[]>(`/payments/${orderId}/status`);
    return response.data;
  },
};

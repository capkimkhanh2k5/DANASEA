import { apiClient } from './apiClient';
import type {
  CreatePaymentIntentRequest,
  PaymentIntentResponse,
  OrderPaymentStatusResponse,
  PaymentProviderType,
  PaymentResponse,
} from '../types/order';

export const paymentService = {
  async getAvailableProviders(signal?: AbortSignal): Promise<PaymentProviderType[]> {
    const response = await apiClient.get<PaymentProviderType[]>('/payments/providers', { signal });
    return response.data;
  },

  /**
   * Create a payment intent for the selected provider.
   * POST /api/payments/{orderId}/create-intent
   */
  async createPaymentIntent(
    orderId: string,
    request: CreatePaymentIntentRequest,
    idempotencyKey: string
  ): Promise<PaymentIntentResponse> {
    const response = await apiClient.post<PaymentIntentResponse>(
      `/payments/${orderId}/create-intent`,
      request,
      {
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
      }
    );
    return response.data;
  },

  async getPaymentDetail(paymentId: string, signal?: AbortSignal): Promise<PaymentResponse> {
    const response = await apiClient.get<PaymentResponse>(`/payments/detail/${paymentId}`, { signal });
    return response.data;
  },

  async getOrderPaymentStatus(orderId: string, signal?: AbortSignal): Promise<OrderPaymentStatusResponse> {
    const response = await apiClient.get<OrderPaymentStatusResponse>(`/payments/${orderId}/payment-status`, { signal });
    return response.data;
  },

  async capturePayPalOrder(paymentId: string, paypalOrderId: string): Promise<PaymentResponse> {
    const response = await apiClient.post<PaymentResponse>('/payments/paypal/capture', {
      paymentId,
      paypalOrderId,
    }, {
      headers: { 'Idempotency-Key': `paypal-return-${paymentId}` },
    });
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

import { apiClient } from './apiClient';
import { clearPaymentIntentKey, getPaymentIntentKey } from '../utils/checkoutPaymentSession';
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
    request: CreatePaymentIntentRequest
  ): Promise<PaymentIntentResponse> {
    const { provider } = request;
    const key = getPaymentIntentKey(orderId, provider);
    try {
      const response = await apiClient.post<PaymentIntentResponse>(
        `/payments/${orderId}/create-intent`,
        request,
        { headers: { 'Idempotency-Key': key } }
      );
      if (response.data.paymentUrl || response.data.paymentReference) {
        clearPaymentIntentKey(orderId, provider, key);
      }
      return response.data;
    } catch (error) {
      const { status, data } = (error as {
        response?: { status?: number; data?: { code?: string; errorCode?: string } };
      }).response ?? {};
      const code = data?.code ?? data?.errorCode;
      if (code === 'PAYMENT_ATTEMPT_CLOSED' || code === 'PAYMENT_WINDOW_EXPIRED'
          || (code !== 'WAIVER_ACCEPTANCE_REQUIRED' && status && status >= 400 && status < 500
              && ![401, 408, 409, 425, 429].includes(status))) {
        clearPaymentIntentKey(orderId, provider, key);
      }
      throw error;
    }
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

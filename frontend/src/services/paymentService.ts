import { apiClient } from './apiClient';
import { flowLogger } from '../utils/flowLogger';
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

    flowLogger.start({
      step: '05_PAYMENT',
      stepName: 'Khởi tạo thanh toán & QR',
      operation: `POST /api/payments/${orderId}/create-intent`,
      payload: { orderId, ...request, idempotencyKey: key },
    });

    try {
      const response = await apiClient.post<PaymentIntentResponse>(
        `/payments/${orderId}/create-intent`,
        request,
        {
          headers: {
            'Idempotency-Key': key,
          },
        }
      );

      flowLogger.success(
        {
          step: '05_PAYMENT',
          stepName: 'Khởi tạo thanh toán & QR',
          operation: `POST /api/payments/${orderId}/create-intent`,
        },
        {
          provider: response.data.provider,
          paymentUrl: response.data.paymentUrl,
          paymentReference: response.data.paymentReference,
        }
      );

      return response.data;
    } catch (err) {
      flowLogger.error(
        {
          step: '05_PAYMENT',
          stepName: 'Khởi tạo thanh toán & QR',
          operation: `POST /api/payments/${orderId}/create-intent`,
          payload: { orderId, ...request, idempotencyKey: key },
        },
        err
      );
      throw err;
    }
  },

  /**
   * Get payment status by order ID
   * GET /api/payments/{orderId}/status
   */
  async getPaymentStatus(orderId: string): Promise<PaymentResponse[]> {
    const response = await apiClient.get<PaymentResponse[]>(`/payments/${orderId}/status`);
    return response.data;
  },

  /**
   * Forward VNPay return params to backend IPN webhook to verify checksum and transition order to PAID
   * GET /api/payments/webhook/vnpay
   */
  async processVNPayCallback(params: Record<string, string>): Promise<Record<string, string>> {
    const response = await apiClient.get<Record<string, string>>('/payments/webhook/vnpay', {
      params,
    });
    return response.data;
  },
};

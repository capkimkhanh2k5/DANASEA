import { apiClient } from './apiClient';
import { clearPaymentIntentKey, getPaymentIntentKey } from '../utils/checkoutPaymentSession';
import type {
  CreatePaymentIntentRequest,
  PaymentIntentResponse,
  OrderPaymentStatusResponse,
  PaymentProviderType,
  PaymentResponse,
} from '../types/order';

export const paymentProviderNames: Record<PaymentProviderType, string> = {
  VNPAY: 'VNPay',
  PAYPAL: 'PayPal',
  PAYOS: 'payOS',
  ZALOPAY: 'ZaloPay',
  MOMO: 'MoMo',
  SEPAY: 'SePay',
};

async function requestPaymentIntent(
  orderId: string,
  request: CreatePaymentIntentRequest,
  action: 'create-intent' | 'switch-provider'
): Promise<PaymentIntentResponse> {
  const { provider } = request;
  const key = getPaymentIntentKey(orderId, provider);
  try {
    const response = await apiClient.post<PaymentIntentResponse>(
      `/payments/${orderId}/${action}`,
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
}

export const paymentService = {
  async getAvailableProviders(signal?: AbortSignal): Promise<PaymentProviderType[]> {
    const response = await apiClient.get<PaymentProviderType[]>('/payments/providers', { signal });
    return response.data;
  },

  createPaymentIntent(orderId: string, request: CreatePaymentIntentRequest): Promise<PaymentIntentResponse> {
    return requestPaymentIntent(orderId, request, 'create-intent');
  },

  continuePayment(
    orderId: string,
    request: CreatePaymentIntentRequest,
    status: OrderPaymentStatusResponse
  ): Promise<PaymentIntentResponse> {
    if (status.isExpired) throw new Error('Đơn hàng đã hết hạn thanh toán. Vui lòng đặt lại.');
    if (status.orderStatus !== 'PENDING_PAYMENT' || status.paymentStatus !== 'UNPAID') {
      throw new Error('Đơn hàng đã được xử lý. Vui lòng kiểm tra trạng thái đơn.');
    }
    const switching = status.activeProvider && status.activeProvider !== request.provider;
    return requestPaymentIntent(orderId, request, switching ? 'switch-provider' : 'create-intent');
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

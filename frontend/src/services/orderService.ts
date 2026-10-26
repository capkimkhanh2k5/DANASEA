import { apiClient } from './apiClient';
import type {
  CreateOrderRequest,
  OrderResponse,
  OrderPageResponse,
  DiscountPreviewRequest,
  DiscountPreviewResponse,
  AcceptSubOrderWaiverRequest,
  SubOrderWaiverAcceptanceResponse,
  CancellationPreviewResponse,
  RefundRequest,
  RefundResponse,
  PaymentResponse,
  OrderReceiptResponse,
  QrCodeResponse,
} from '../types/order';

export const orderService = {
  /**
   * Preview discount code for a held booking
   * POST /api/checkout/discount-preview
   */
  async previewDiscount(request: DiscountPreviewRequest): Promise<DiscountPreviewResponse> {
    const response = await apiClient.post<DiscountPreviewResponse>('/checkout/discount-preview', request);
    return response.data;
  },

  /**
   * Create master order from held booking
   * POST /api/orders
   */
  async createOrder(request: CreateOrderRequest, idempotencyKey?: string): Promise<OrderResponse> {
    const key = idempotencyKey || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `order-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`);
    const response = await apiClient.post<OrderResponse>('/orders', request, {
      headers: {
        'Idempotency-Key': key,
      },
    });
    return response.data;
  },

  /**
   * Get customer order history
   * GET /api/orders
   */
  async getMyOrders(page = 0, size = 20): Promise<OrderPageResponse> {
    const response = await apiClient.get<OrderPageResponse>('/orders', {
      params: { page, size },
    });
    return response.data;
  },

  /**
   * Get master order detail
   * GET /api/orders/{id}
   */
  async getOrderDetail(id: string): Promise<OrderResponse> {
    const response = await apiClient.get<OrderResponse>(`/orders/${id}`);
    return response.data;
  },

  /**
   * Accept safety waiver for a sub-order
   * POST /api/sub-orders/{subOrderId}/waiver-acceptance
   */
  async acceptSubOrderWaiver(
    subOrderId: string,
    request: AcceptSubOrderWaiverRequest
  ): Promise<SubOrderWaiverAcceptanceResponse> {
    const response = await apiClient.post<SubOrderWaiverAcceptanceResponse>(
      `/sub-orders/${subOrderId}/waiver-acceptance`,
      request
    );
    return response.data;
  },

  /**
   * Get cancellation preview and refund breakdown
   * GET /api/orders/{id}/cancellation-preview
   */
  async getCancellationPreview(orderId: string): Promise<CancellationPreviewResponse> {
    const response = await apiClient.get<CancellationPreviewResponse>(`/orders/${orderId}/cancellation-preview`);
    return response.data;
  },

  /**
   * Request refund for customer order
   * POST /api/orders/{id}/refund-request
   */
  async requestRefund(orderId: string, reason?: RefundRequest['reason'], idempotencyKey?: string): Promise<RefundResponse[]> {
    const key = idempotencyKey || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `refund-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`);
    // Backend guardrail: customers may only submit CUSTOMER_REQUEST or CUSTOMER_CANCEL
    const safeReason = reason === 'CUSTOMER_CANCEL' ? 'CUSTOMER_CANCEL' : 'CUSTOMER_REQUEST';
    const response = await apiClient.post<RefundResponse[]>(
      `/orders/${orderId}/refund-request`,
      { reason: safeReason },
      {
        headers: {
          'Idempotency-Key': key,
        },
      }
    );
    return response.data;
  },

  /**
   * Get refunds for an order
   * GET /api/orders/{id}/refunds
   */
  async getOrderRefunds(orderId: string): Promise<RefundResponse[]> {
    const response = await apiClient.get<RefundResponse[]>(`/orders/${orderId}/refunds`);
    return response.data;
  },

  /**
   * Get payment transactions for an order
   * GET /api/orders/{id}/payments
   */
  async getOrderPayments(orderId: string): Promise<PaymentResponse[]> {
    const response = await apiClient.get<PaymentResponse[]>(`/orders/${orderId}/payments`);
    return response.data;
  },

  /**
   * Get electronic receipt data (JSON format)
   * GET /api/orders/{id}/receipt
   */
  async getOrderReceipt(orderId: string): Promise<OrderReceiptResponse> {
    const response = await apiClient.get<OrderReceiptResponse>(`/orders/${orderId}/receipt`);
    return response.data;
  },

  /**
   * Download electronic receipt / E-Ticket as binary PDF
   * GET /api/orders/{id}/receipt?format=pdf
   */
  async downloadOrderReceiptPdf(orderId: string): Promise<Blob> {
    const response = await apiClient.get(`/orders/${orderId}/receipt`, {
      params: { format: 'pdf' },
      responseType: 'blob',
      headers: {
        Accept: 'application/pdf',
      },
    });
    return response.data as Blob;
  },

  /**
   * Generate or retrieve Check-in QR Code for a booking / subOrder
   * POST /api/bookings/{id}/qr-code
   */
  async getCheckinQrCode(identifier: string): Promise<QrCodeResponse> {
    const response = await apiClient.post<QrCodeResponse>(`/bookings/${identifier}/qr-code`);
    return response.data;
  },
};


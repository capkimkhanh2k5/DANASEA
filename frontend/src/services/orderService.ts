import { apiClient } from './apiClient';
import type {
  CreateOrderRequest,
  OrderResponse,
  OrderPageResponse,
  DiscountPreviewRequest,
  DiscountPreviewResponse,
  AcceptSubOrderWaiverRequest,
  SubOrderWaiverAcceptanceResponse,
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
};

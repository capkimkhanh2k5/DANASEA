import { apiClient } from './apiClient';

export interface CreateReviewRequest {
  rating: number;
  comment: string;
  images?: string[];
}

export interface UpdateReviewRequest {
  rating?: number;
  comment?: string;
  images?: string[];
}

export interface ReviewResponse {
  id: string;
  subOrderId: string;
  customerId: string;
  customerName?: string;
  customerAvatar?: string;
  vendorId?: string;
  serviceId?: string;
  rating: number;
  comment: string;
  images?: string[];
  vendorReply?: string;
  vendorRepliedAt?: string;
  isFlagged?: boolean;
  isVisible?: boolean;
  flagReason?: string;
  moderationNote?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const reviewService = {
  /**
   * Submit a review for a completed sub-order
   * POST /api/sub-orders/{id}/reviews
   */
  async createReview(subOrderId: string, request: CreateReviewRequest): Promise<ReviewResponse> {
    const response = await apiClient.post<ReviewResponse>(`/sub-orders/${subOrderId}/reviews`, request);
    return response.data;
  },

  /**
   * Update an existing review for a sub-order within 7 days
   * PUT /api/sub-orders/{id}/reviews
   */
  async updateReviewBySubOrder(subOrderId: string, request: UpdateReviewRequest): Promise<ReviewResponse> {
    const response = await apiClient.put<ReviewResponse>(`/sub-orders/${subOrderId}/reviews`, request);
    return response.data;
  },

  /**
   * Get public reviews for a specific service
   * GET /api/services/{id}/reviews
   */
  async getServiceReviews(serviceId: string, page = 0, size = 10): Promise<PageResponse<ReviewResponse>> {
    const response = await apiClient.get<PageResponse<ReviewResponse>>(`/services/${serviceId}/reviews`, {
      params: { page, size },
    });
    return response.data;
  },
};

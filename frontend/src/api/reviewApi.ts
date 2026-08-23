import { apiClient } from './client';
import type { Page } from '../types/api';
import type { CreateReviewRequest, ReviewResponse, ReviewSummaryResponse } from '../types/review';

export const reviewApi = {
  async createReview(data: CreateReviewRequest): Promise<ReviewResponse> {
    const response = await apiClient.post<ReviewResponse>('/api/v1/reviews', data);
    return response.data;
  },

  async getProductReviews(productId: number, page = 0, size = 10): Promise<Page<ReviewResponse>> {
    const response = await apiClient.get<Page<ReviewResponse>>(`/api/v1/reviews/product/${productId}`, {
      params: { page, size },
    });
    return response.data;
  },

  async getProductReviewSummary(productId: number): Promise<ReviewSummaryResponse> {
    const response = await apiClient.get<ReviewSummaryResponse>(`/api/v1/reviews/product/${productId}/summary`);
    return response.data;
  },

  async deleteReview(id: number): Promise<void> {
    await apiClient.delete(`/api/v1/reviews/${id}`);
  },
};

import { apiClient } from './client';
import { generateUUID } from '../utils/idempotency';
import type { Page } from '../types/api';
import type { CreateOrderRequest, OrderResponse, CancelOrderRequest } from '../types/order';

export interface GetUserOrdersParams {
  page?: number;
  size?: number;
}

export const orderApi = {
  async createOrder(data: CreateOrderRequest, idempotencyKey?: string): Promise<OrderResponse> {
    const key = idempotencyKey || generateUUID();
    const response = await apiClient.post<OrderResponse>('/api/v1/orders', data, {
      headers: {
        'Idempotency-Key': key,
      },
    });
    return response.data;
  },

  async getOrderById(id: number): Promise<OrderResponse> {
    const response = await apiClient.get<OrderResponse>(`/api/v1/orders/${id}`);
    return response.data;
  },

  async getUserOrders(userId: number, params?: GetUserOrdersParams): Promise<Page<OrderResponse>> {
    const response = await apiClient.get<Page<OrderResponse>>(`/api/v1/orders/user/${userId}`, {
      params,
    });
    return response.data;
  },

  async cancelOrder(id: number, data?: CancelOrderRequest): Promise<OrderResponse> {
    const response = await apiClient.post<OrderResponse>(`/api/v1/orders/${id}/cancel`, data || {});
    return response.data;
  },
};

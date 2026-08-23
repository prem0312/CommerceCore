import { apiClient } from './client';
import type { Page } from '../types/api';
import type { ProductResponse, ProductRequest } from '../types/product';

export interface ListProductsParams {
  category?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const productApi = {
  async listProducts(params?: ListProductsParams): Promise<Page<ProductResponse>> {
    const response = await apiClient.get<Page<ProductResponse>>('/api/v1/products', {
      params,
    });
    return response.data;
  },

  async getProductById(id: number): Promise<ProductResponse> {
    const response = await apiClient.get<ProductResponse>(`/api/v1/products/${id}`);
    return response.data;
  },

  async createProduct(data: ProductRequest): Promise<ProductResponse> {
    const response = await apiClient.post<ProductResponse>('/api/v1/products', data);
    return response.data;
  },

  async updateProduct(id: number, data: ProductRequest): Promise<ProductResponse> {
    const response = await apiClient.put<ProductResponse>(`/api/v1/products/${id}`, data);
    return response.data;
  },

  async deleteProduct(id: number): Promise<void> {
    await apiClient.delete(`/api/v1/products/${id}`);
  },
};

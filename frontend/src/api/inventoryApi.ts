import { apiClient } from './client';
import type {
  InventoryResponse,
  AdjustStockRequest,
  ReserveStockRequest,
  ReleaseStockRequest,
  BatchReserveRequest,
  BatchReleaseRequest,
} from '../types/inventory';

export const inventoryApi = {
  async getInventoryByProductId(productId: number): Promise<InventoryResponse> {
    const response = await apiClient.get<InventoryResponse>(`/api/v1/inventory/${productId}`);
    return response.data;
  },

  async adjustStock(productId: number, data: AdjustStockRequest): Promise<InventoryResponse> {
    const response = await apiClient.patch<InventoryResponse>(`/api/v1/inventory/${productId}/adjust`, data);
    return response.data;
  },

  async reserveStock(productId: number, data: ReserveStockRequest): Promise<InventoryResponse> {
    const response = await apiClient.post<InventoryResponse>(`/api/v1/inventory/${productId}/reserve`, data);
    return response.data;
  },

  async releaseStock(productId: number, data: ReleaseStockRequest): Promise<InventoryResponse> {
    const response = await apiClient.post<InventoryResponse>(`/api/v1/inventory/${productId}/release`, data);
    return response.data;
  },

  async batchReserve(data: BatchReserveRequest): Promise<void> {
    await apiClient.post('/api/v1/inventory/batch-reserve', data);
  },

  async batchRelease(data: BatchReleaseRequest): Promise<void> {
    await apiClient.post('/api/v1/inventory/batch-release', data);
  },
};

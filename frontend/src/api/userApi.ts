import { apiClient } from './client';
import type {
  UserProfileResponse,
  UpdateProfileRequest,
  UpdateStatusRequest,
  AddressRequest,
  AddressResponse,
} from '../types/user';

export const userApi = {
  async getCurrentUser(): Promise<UserProfileResponse> {
    const response = await apiClient.get<UserProfileResponse>('/api/v1/users/me');
    return response.data;
  },

  async getUserById(id: number): Promise<UserProfileResponse> {
    const response = await apiClient.get<UserProfileResponse>(`/api/v1/users/${id}`);
    return response.data;
  },

  async updateProfile(id: number, data: UpdateProfileRequest): Promise<UserProfileResponse> {
    const response = await apiClient.put<UserProfileResponse>(`/api/v1/users/${id}`, data);
    return response.data;
  },

  async updateStatus(id: number, data: UpdateStatusRequest): Promise<UserProfileResponse> {
    const response = await apiClient.patch<UserProfileResponse>(`/api/v1/users/${id}/status`, data);
    return response.data;
  },

  async getAddresses(userId: number): Promise<AddressResponse[]> {
    const response = await apiClient.get<AddressResponse[]>(`/api/v1/users/${userId}/addresses`);
    return response.data;
  },

  async addAddress(userId: number, data: AddressRequest): Promise<AddressResponse> {
    const response = await apiClient.post<AddressResponse>(`/api/v1/users/${userId}/addresses`, data);
    return response.data;
  },

  async deleteAddress(userId: number, addressId: number): Promise<void> {
    await apiClient.delete(`/api/v1/users/${userId}/addresses/${addressId}`);
  },
};

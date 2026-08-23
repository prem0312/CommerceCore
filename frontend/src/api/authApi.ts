import { apiClient } from './client';
import type { AuthResponse, LoginRequest, RegisterRequest, TokenValidationResponse } from '../types/auth';

export const authApi = {
  async register(data: RegisterRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/api/v1/auth/register', data);
    return response.data;
  },

  async login(data: LoginRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/api/v1/auth/login', data);
    return response.data;
  },

  async validateToken(): Promise<TokenValidationResponse> {
    const response = await apiClient.get<TokenValidationResponse>('/api/v1/auth/validate');
    return response.data;
  },
};

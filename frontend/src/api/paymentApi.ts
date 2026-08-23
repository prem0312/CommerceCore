import { apiClient } from './client';
import { generateUUID } from '../utils/idempotency';
import type { PaymentResponse, InitiatePaymentRequest } from '../types/payment';

export const paymentApi = {
  async initiatePayment(data: InitiatePaymentRequest, idempotencyKey?: string): Promise<PaymentResponse> {
    const key = idempotencyKey || generateUUID();
    const response = await apiClient.post<PaymentResponse>('/api/v1/payments', data, {
      headers: {
        'Idempotency-Key': key,
      },
    });
    return response.data;
  },

  async getPaymentById(id: number): Promise<PaymentResponse> {
    const response = await apiClient.get<PaymentResponse>(`/api/v1/payments/${id}`);
    return response.data;
  },

  async getPaymentByOrderId(orderId: number): Promise<PaymentResponse> {
    const response = await apiClient.get<PaymentResponse>(`/api/v1/payments/order/${orderId}`);
    return response.data;
  },
};

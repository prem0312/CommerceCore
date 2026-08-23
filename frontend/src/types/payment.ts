export type PaymentStatus = 'SUCCESS' | 'FAILED' | 'PENDING';

export interface InitiatePaymentRequest {
  orderId: number;
  amount: number;
  currency?: string;
}

export interface PaymentResponse {
  id: number;
  orderId: number;
  transactionId: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  idempotencyKey: string;
  failureReason: string | null;
  createdAt: string;
}

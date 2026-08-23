export type OrderStatus =
  | 'PENDING'
  | 'PAYMENT_PENDING'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'PAYMENT_FAILED'
  | 'CANCELLED';

export interface OrderItemRequest {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
}

export interface CreateOrderRequest {
  items: OrderItemRequest[];
  currency?: string;
}

export interface OrderItemResponse {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface OrderResponse {
  id: number;
  userId: number;
  status: OrderStatus;
  totalAmount: number;
  currency: string;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface CancelOrderRequest {
  reason?: string;
}

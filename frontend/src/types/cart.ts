export interface CartItem {
  productId: number;
  productName: string;
  price: number;
  quantity: number;
  category?: string;
  maxAvailable?: number;
}

export interface CartState {
  items: CartItem[];
  totalItems: number;
  totalPrice: number;
}

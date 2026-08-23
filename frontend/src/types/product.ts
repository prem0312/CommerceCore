export interface ProductResponse {
  id: number;
  name: string;
  description: string;
  price: number;
  category: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  name: string;
  description?: string;
  price: number;
  category: string;
  initialStock: number;
  reorderThreshold: number;
}

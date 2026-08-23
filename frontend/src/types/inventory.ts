export interface InventoryResponse {
  id: number;
  productId: number;
  availableQuantity: number;
  reservedQuantity: number;
  reorderThreshold: number;
  version: number;
  updatedAt: string;
}

export interface ReserveStockRequest {
  quantity: number;
}

export interface ReleaseStockRequest {
  quantity: number;
}

export interface AdjustStockRequest {
  availableQuantity: number;
  reorderThreshold: number;
}

export interface StockReservationItem {
  productId: number;
  quantity: number;
}

export interface BatchReserveRequest {
  items: StockReservationItem[];
}

export interface BatchReleaseRequest {
  items: StockReservationItem[];
}

import React from 'react';
import type { OrderStatus } from '../../types/order';
import { Badge } from '../common/Badge';

interface OrderStatusBadgeProps {
  status: OrderStatus;
  size?: 'sm' | 'md';
}

export const OrderStatusBadge: React.FC<OrderStatusBadgeProps> = ({ status, size = 'md' }) => {
  const statusConfig: Record<
    OrderStatus,
    { variant: 'default' | 'primary' | 'success' | 'warning' | 'danger' | 'neutral'; label: string }
  > = {
    PENDING: { variant: 'warning', label: 'Pending' },
    PAYMENT_PENDING: { variant: 'warning', label: 'Payment Pending' },
    CONFIRMED: { variant: 'success', label: 'Confirmed' },
    PROCESSING: { variant: 'primary', label: 'Processing' },
    SHIPPED: { variant: 'primary', label: 'Shipped' },
    DELIVERED: { variant: 'success', label: 'Delivered' },
    PAYMENT_FAILED: { variant: 'danger', label: 'Payment Failed' },
    CANCELLED: { variant: 'danger', label: 'Cancelled' },
  };

  const config = statusConfig[status] || { variant: 'neutral', label: status };

  return (
    <Badge variant={config.variant} size={size} dot>
      {config.label}
    </Badge>
  );
};

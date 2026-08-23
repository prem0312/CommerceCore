import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Package, ArrowRight, Clock, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react';
import { orderApi } from '../../api/orderApi';
import { useAuth } from '../../hooks/useAuth';
import { extractApiErrorMessage } from '../../api/client';
import type { OrderResponse } from '../../types/order';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { OrderStatusBadge } from '../../components/orders/OrderStatusBadge';
import { Button } from '../../components/common/Button';
import { EmptyState } from '../../components/common/EmptyState';
import { Alert } from '../../components/common/Alert';
import { Spinner } from '../../components/common/Spinner';

export const OrderHistoryPage: React.FC = () => {
  const { user } = useAuth();
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const loadOrders = useCallback(async () => {
    if (!user?.userId) return;

    setIsLoading(true);
    setError(null);
    try {
      const response = await orderApi.getUserOrders(user.userId, { page, size: 10 });
      setOrders(response.content || []);
      setTotalPages(response.totalPages || 1);
    } catch (err) {
      setError(extractApiErrorMessage(err, 'Failed to retrieve order history.'));
    } finally {
      setIsLoading(false);
    }
  }, [user?.userId, page]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  return (
    <div className="space-y-6">
      <div className="bg-white p-6 sm:p-8 rounded-3xl border border-slate-200/80 shadow-xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-slate-100">
          <div>
            <h2 className="text-xl font-bold text-slate-900">Your Orders</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Authoritative order tracking from Order Service (`:8084`)
            </p>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={loadOrders}
            disabled={isLoading}
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
          >
            Refresh
          </Button>
        </div>

        {error && (
          <Alert variant="error" title="Failed to Load Orders">
            {error}
          </Alert>
        )}

        {isLoading ? (
          <div className="py-12 flex flex-col items-center justify-center space-y-3">
            <Spinner size="lg" />
            <span className="text-xs text-slate-500 font-medium">Fetching order records...</span>
          </div>
        ) : orders.length === 0 ? (
          <EmptyState
            icon={<Package className="w-8 h-8 text-slate-400" />}
            title="No Orders Placed Yet"
            description="When you place an order, its state machine progression and receipt will appear here."
            action={
              <Link to="/">
                <Button variant="primary" rightIcon={<ArrowRight className="w-4 h-4" />}>
                  Browse Catalogue
                </Button>
              </Link>
            }
          />
        ) : (
          <div className="space-y-4">
            {orders.map((order) => {
              const itemCount = order.items ? order.items.reduce((sum, i) => sum + i.quantity, 0) : 0;

              return (
                <div
                  key={order.id}
                  className="p-5 sm:p-6 rounded-2xl border border-slate-200 hover:border-slate-300 transition-all bg-white shadow-xs space-y-4"
                >
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-xl bg-slate-100 flex items-center justify-center text-slate-700 font-mono font-bold text-xs">
                        #{order.id}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-bold text-slate-900">
                            Order #{order.id}
                          </span>
                          <OrderStatusBadge status={order.status} size="sm" />
                        </div>
                        <div className="flex items-center gap-2 text-xs text-slate-400 mt-0.5">
                          <Clock className="w-3.5 h-3.5" />
                          <span>Placed on {formatDate(order.createdAt)}</span>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between sm:justify-end gap-4">
                      <div className="text-right">
                        <span className="text-xs text-slate-400 block font-medium">Total</span>
                        <span className="text-base font-extrabold text-slate-900">
                          {formatCurrency(order.totalAmount, order.currency)}
                        </span>
                      </div>

                      <Link to={`/account/orders/${order.id}`}>
                        <Button variant="outline" size="sm" rightIcon={<ArrowRight className="w-3.5 h-3.5" />}>
                          Details
                        </Button>
                      </Link>
                    </div>
                  </div>

                  {/* Items summary */}
                  {order.items && order.items.length > 0 && (
                    <div className="text-xs text-slate-600 flex flex-wrap gap-2 items-center">
                      <span className="font-semibold text-slate-700">{itemCount} item{itemCount > 1 ? 's' : ''}:</span>
                      {order.items.slice(0, 3).map((it) => (
                        <span key={it.id} className="bg-slate-100 px-2 py-0.5 rounded-md text-[11px] text-slate-700 font-medium">
                          {it.quantity}× {it.productName}
                        </span>
                      ))}
                      {order.items.length > 3 && (
                        <span className="text-slate-400 text-[11px]">
                          +{order.items.length - 3} more
                        </span>
                      )}
                    </div>
                  )}
                </div>
              );
            })}

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-2 pt-4">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page <= 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  leftIcon={<ChevronLeft className="w-4 h-4" />}
                >
                  Previous
                </Button>
                <span className="text-xs font-semibold text-slate-600 px-3">
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  rightIcon={<ChevronRight className="w-4 h-4" />}
                >
                  Next
                </Button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

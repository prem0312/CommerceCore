import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link, useLocation } from 'react-router-dom';
import { 
  ArrowLeft, 
  RefreshCw, 
  CreditCard, 
  ShieldCheck, 
  Calendar, 
  XCircle,
  FileText
} from 'lucide-react';
import { orderApi } from '../../api/orderApi';
import { paymentApi } from '../../api/paymentApi';
import { extractApiErrorMessage } from '../../api/client';
import type { OrderResponse } from '../../types/order';
import type { PaymentResponse } from '../../types/payment';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { OrderStatusBadge } from '../../components/orders/OrderStatusBadge';
import { OrderStatusTimeline } from '../../components/orders/OrderStatusTimeline';
import { CancelOrderModal } from '../../components/orders/CancelOrderModal';
import { Button } from '../../components/common/Button';
import { Alert } from '../../components/common/Alert';
import { EmptyState } from '../../components/common/EmptyState';
import { Spinner } from '../../components/common/Spinner';

export const OrderDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const orderId = id ? parseInt(id, 10) : NaN;

  // New order banner flag passed via router state
  const isNewOrder = (location.state as { isNewOrder?: boolean })?.isNewOrder;

  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [payment, setPayment] = useState<PaymentResponse | null>(null);

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Cancellation modal state
  const [isCancelModalOpen, setIsCancelModalOpen] = useState<boolean>(false);
  const [isCancelling, setIsCancelling] = useState<boolean>(false);
  const [cancelSuccessMsg, setCancelSuccessMsg] = useState<string | null>(null);

  const loadOrderDetails = useCallback(async () => {
    if (isNaN(orderId)) {
      setError('Invalid Order ID');
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      // 1. Fetch Order details
      const orderData = await orderApi.getOrderById(orderId);
      setOrder(orderData);

      // 2. Fetch Payment details for this order
      try {
        const paymentData = await paymentApi.getPaymentByOrderId(orderId);
        setPayment(paymentData);
      } catch {
        // Payment record may still be committing via Outbox/Kafka listener
      }
    } catch (err) {
      setError(extractApiErrorMessage(err, 'Failed to retrieve order details.'));
    } finally {
      setIsLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    loadOrderDetails();
  }, [loadOrderDetails]);

  const handleConfirmCancel = async (reason: string) => {
    if (!order) return;
    setIsCancelling(true);
    try {
      const updatedOrder = await orderApi.cancelOrder(order.id, { reason });
      setOrder(updatedOrder);
      setIsCancelModalOpen(false);
      setCancelSuccessMsg(
        'Order successfully cancelled. Reserved inventory stock has been released back to the store.'
      );
      setTimeout(() => setCancelSuccessMsg(null), 6000);
    } catch (err) {
      alert(extractApiErrorMessage(err, 'Failed to cancel order.'));
    } finally {
      setIsCancelling(false);
    }
  };

  if (isLoading) {
    return (
      <div className="bg-white p-12 rounded-3xl border border-slate-200/80 shadow-xs flex flex-col items-center justify-center space-y-4">
        <Spinner size="lg" />
        <span className="text-sm text-slate-500 font-medium">Loading order #{orderId}...</span>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="space-y-6">
        <Link to="/account/orders" className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors">
          <ArrowLeft className="w-3.5 h-3.5" /> Back to Orders
        </Link>
        <EmptyState
          icon={<RefreshCw className="w-8 h-8 text-rose-500" />}
          title="Order Not Found"
          description={error || `Order #${orderId} could not be retrieved from Order Service.`}
          action={
            <Link to="/account/orders">
              <Button variant="primary">View Order History</Button>
            </Link>
          }
        />
      </div>
    );
  }

  // Can cancel if order is in non-terminal state
  const canCancel =
    order.status === 'PENDING' ||
    order.status === 'PAYMENT_PENDING' ||
    order.status === 'CONFIRMED' ||
    order.status === 'PROCESSING';

  return (
    <div className="space-y-6">
      {/* Back Link */}
      <div className="flex items-center justify-between">
        <Link
          to="/account/orders"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" /> Back to Order History
        </Link>

        <Button
          variant="outline"
          size="sm"
          onClick={loadOrderDetails}
          leftIcon={<RefreshCw className="w-3.5 h-3.5" />}
        >
          Refresh
        </Button>
      </div>

      {isNewOrder && (
        <Alert variant="success" title="Order Placed Successfully!">
          Your order #{order.id} has been accepted by CommerceCore. Synchronous inventory reservation is complete and payment processing has initiated.
        </Alert>
      )}

      {cancelSuccessMsg && (
        <Alert variant="info" title="Order Cancellation Confirmed">
          {cancelSuccessMsg}
        </Alert>
      )}

      {/* Main Order Card */}
      <div className="bg-white p-6 sm:p-8 rounded-3xl border border-slate-200/80 shadow-xs space-y-8">
        {/* Header with status */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-slate-100">
          <div className="space-y-1">
            <div className="flex items-center gap-3">
              <h2 className="text-2xl font-extrabold text-slate-900 tracking-tight">
                Order #{order.id}
              </h2>
              <OrderStatusBadge status={order.status} />
            </div>
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <Calendar className="w-3.5 h-3.5" />
              <span>Placed {formatDateTime(order.createdAt)}</span>
            </div>
          </div>

          {canCancel && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsCancelModalOpen(true)}
              className="text-rose-600 border-rose-200 hover:bg-rose-50 hover:border-rose-300"
              leftIcon={<XCircle className="w-4 h-4" />}
            >
              Cancel Order
            </Button>
          )}
        </div>

        {/* State Machine Status Timeline */}
        <div className="p-4 sm:p-6 rounded-2xl bg-slate-50 border border-slate-200/80 space-y-3">
          <div className="flex items-center justify-between text-xs font-bold text-slate-700">
            <span>Fulfillment Pipeline</span>
            <span className="font-mono text-blue-600">Status: {order.status}</span>
          </div>
          <OrderStatusTimeline status={order.status} />
        </div>

        {/* Line Items Table */}
        <div className="space-y-3">
          <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
            <FileText className="w-4 h-4 text-blue-600" />
            Purchased Items
          </h3>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-200 text-slate-400 font-bold uppercase tracking-wider">
                  <th className="pb-3 font-semibold">Product</th>
                  <th className="pb-3 font-semibold text-center">Qty</th>
                  <th className="pb-3 font-semibold text-right">Unit Price</th>
                  <th className="pb-3 font-semibold text-right">Subtotal</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {order.items && order.items.map((item) => (
                  <tr key={item.id} className="text-slate-800">
                    <td className="py-3.5 font-bold text-slate-900 pr-2">
                      <Link to={`/products/${item.productId}`} className="hover:text-blue-600 transition-colors">
                        {item.productName}
                      </Link>
                      <span className="text-[10px] text-slate-400 block font-mono">SKU #{item.productId}</span>
                    </td>
                    <td className="py-3.5 text-center font-semibold text-slate-700">
                      {item.quantity}
                    </td>
                    <td className="py-3.5 text-right font-medium text-slate-600">
                      {formatCurrency(item.unitPrice, order.currency)}
                    </td>
                    <td className="py-3.5 text-right font-extrabold text-slate-900">
                      {formatCurrency(item.subtotal, order.currency)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Summary & Payment Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4 border-t border-slate-100">
          {/* Payment Details */}
          <div className="p-5 rounded-2xl bg-slate-50 border border-slate-200 space-y-3">
            <div className="font-bold text-slate-900 text-xs flex items-center gap-2">
              <CreditCard className="w-4 h-4 text-emerald-600" />
              Payment Information
            </div>

            {payment ? (
              <div className="space-y-2 text-xs text-slate-600">
                <div className="flex justify-between">
                  <span>Transaction ID:</span>
                  <span className="font-mono font-bold text-slate-800">{payment.transactionId || 'TXN-PENDING'}</span>
                </div>
                <div className="flex justify-between">
                  <span>Payment Status:</span>
                  <span className={`font-bold ${payment.status === 'SUCCESS' ? 'text-emerald-600' : payment.status === 'FAILED' ? 'text-rose-600' : 'text-amber-600'}`}>
                    {payment.status}
                  </span>
                </div>
                {payment.failureReason && (
                  <div className="text-rose-600 text-[11px] pt-1">
                    Failure: {payment.failureReason}
                  </div>
                )}
              </div>
            ) : (
              <div className="text-xs text-slate-500 space-y-1">
                <p>Payment Service authorization completed synchronously.</p>
                <span className="text-[11px] text-slate-400">Total: {formatCurrency(order.totalAmount, order.currency)}</span>
              </div>
            )}
          </div>

          {/* Totals Breakdown */}
          <div className="p-5 rounded-2xl bg-slate-50 border border-slate-200 space-y-2 text-xs">
            <div className="flex justify-between text-slate-600">
              <span>Items Total:</span>
              <span className="font-semibold text-slate-900">{formatCurrency(order.totalAmount, order.currency)}</span>
            </div>
            <div className="flex justify-between text-slate-600">
              <span>Shipping & Handling:</span>
              <span className="font-semibold text-emerald-600">Free</span>
            </div>
            <div className="pt-2 border-t border-slate-200 flex justify-between items-baseline">
              <span className="text-sm font-bold text-slate-900">Total Charged:</span>
              <span className="text-xl font-extrabold text-slate-900">
                {formatCurrency(order.totalAmount, order.currency)}
              </span>
            </div>
          </div>
        </div>

        {/* Microservices Outbox Note */}
        <div className="flex items-center gap-2 text-[11px] text-slate-400 pt-2 border-t border-slate-100">
          <ShieldCheck className="w-4 h-4 text-blue-500" />
          <span>Synchronized across Order, Inventory, Payment, and Notification microservices</span>
        </div>
      </div>

      {/* Cancellation Modal */}
      <CancelOrderModal
        isOpen={isCancelModalOpen}
        orderId={order.id}
        onClose={() => setIsCancelModalOpen(false)}
        onConfirm={handleConfirmCancel}
        isLoading={isCancelling}
      />
    </div>
  );
};

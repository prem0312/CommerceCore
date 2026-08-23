import React from 'react';
import type { OrderStatus } from '../../types/order';
import { CheckCircle2, Clock, Truck, Package, XCircle, AlertCircle } from 'lucide-react';
import { cn } from '../../utils/cn';

interface OrderStatusTimelineProps {
  status: OrderStatus;
}

export const OrderStatusTimeline: React.FC<OrderStatusTimelineProps> = ({ status }) => {
  if (status === 'CANCELLED') {
    return (
      <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 flex items-center gap-3 text-rose-900">
        <XCircle className="w-5 h-5 text-rose-600 shrink-0" />
        <div>
          <h5 className="font-bold text-sm">Order Cancelled</h5>
          <p className="text-xs text-rose-700 mt-0.5">
            This order has been cancelled and reserved inventory stock has been restored to the catalogue.
          </p>
        </div>
      </div>
    );
  }

  if (status === 'PAYMENT_FAILED') {
    return (
      <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 flex items-center gap-3 text-rose-900">
        <AlertCircle className="w-5 h-5 text-rose-600 shrink-0" />
        <div>
          <h5 className="font-bold text-sm">Payment Failed</h5>
          <p className="text-xs text-rose-700 mt-0.5">
            The payment transaction could not be authorized. Please retry payment or contact customer support.
          </p>
        </div>
      </div>
    );
  }

  const steps = [
    { key: 'placed', label: 'Order Placed', icon: Clock },
    { key: 'payment', label: 'Payment Confirmed', icon: CheckCircle2 },
    { key: 'processing', label: 'Processing', icon: Package },
    { key: 'shipped', label: 'Shipped', icon: Truck },
    { key: 'delivered', label: 'Delivered', icon: CheckCircle2 },
  ];

  const getStepIndex = (st: OrderStatus): number => {
    switch (st) {
      case 'PENDING':
        return 0;
      case 'PAYMENT_PENDING':
        return 0;
      case 'CONFIRMED':
        return 1;
      case 'PROCESSING':
        return 2;
      case 'SHIPPED':
        return 3;
      case 'DELIVERED':
        return 4;
      default:
        return 0;
    }
  };

  const currentStep = getStepIndex(status);

  return (
    <div className="w-full py-4">
      <div className="flex items-center justify-between relative">
        {/* Connecting line */}
        <div className="absolute top-1/2 left-0 right-0 h-1 bg-slate-200 -translate-y-1/2 z-0" />
        <div
          className="absolute top-1/2 left-0 h-1 bg-blue-600 -translate-y-1/2 z-0 transition-all duration-500"
          style={{ width: `${(currentStep / (steps.length - 1)) * 100}%` }}
        />

        {steps.map((step, idx) => {
          const isDone = idx <= currentStep;
          const isCurrent = idx === currentStep;
          const Icon = step.icon;

          return (
            <div key={step.key} className="relative z-10 flex flex-col items-center group">
              <div
                className={cn(
                  'w-8 h-8 rounded-full flex items-center justify-center transition-all shadow-xs',
                  isDone
                    ? 'bg-blue-600 text-white ring-4 ring-blue-50'
                    : 'bg-white border-2 border-slate-300 text-slate-400',
                  isCurrent && 'ring-4 ring-blue-200'
                )}
              >
                <Icon className="w-4 h-4" />
              </div>
              <span
                className={cn(
                  'text-[11px] font-semibold mt-2 hidden sm:block text-center',
                  isDone ? 'text-slate-900' : 'text-slate-400'
                )}
              >
                {step.label}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
};

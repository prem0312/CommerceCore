import React, { useState } from 'react';
import { Button } from '../common/Button';
import { AlertCircle, X } from 'lucide-react';

interface CancelOrderModalProps {
  isOpen: boolean;
  orderId: number;
  onClose: () => void;
  onConfirm: (reason: string) => Promise<void>;
  isLoading?: boolean;
}

export const CancelOrderModal: React.FC<CancelOrderModalProps> = ({
  isOpen,
  orderId,
  onClose,
  onConfirm,
  isLoading = false,
}) => {
  const [reason, setReason] = useState<string>('Customer requested cancellation');

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await onConfirm(reason);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-in fade-in duration-150">
      <div className="bg-white rounded-3xl max-w-md w-full p-6 sm:p-8 shadow-2xl border border-slate-200 space-y-6 animate-in zoom-in-95 duration-150">
        <div className="flex items-start justify-between">
          <div className="w-12 h-12 rounded-2xl bg-rose-50 text-rose-600 flex items-center justify-center">
            <AlertCircle className="w-6 h-6" />
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isLoading}
            className="p-1 text-slate-400 hover:text-slate-600 rounded-lg"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="space-y-2">
          <h3 className="text-xl font-bold text-slate-900">
            Cancel Order #{orderId}?
          </h3>
          <p className="text-xs text-slate-500 leading-relaxed">
            Cancelling this order will release reserved inventory stock back to the catalogue and update payment status. This action cannot be undone.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="cancel-reason" className="block text-xs font-bold uppercase tracking-wider text-slate-700">
              Reason for Cancellation
            </label>
            <select
              id="cancel-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="w-full bg-white border border-slate-200 rounded-xl p-2.5 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900"
            >
              <option value="Customer requested cancellation">Changed my mind / No longer needed</option>
              <option value="Ordered by mistake">Ordered by mistake</option>
              <option value="Incorrect shipping address">Incorrect shipping details</option>
              <option value="Found better alternative">Found a better alternative</option>
              <option value="Other">Other</option>
            </select>
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <Button
              type="button"
              variant="outline"
              size="md"
              onClick={onClose}
              disabled={isLoading}
            >
              Keep Order
            </Button>
            <Button
              type="submit"
              variant="danger"
              size="md"
              isLoading={isLoading}
            >
              Confirm Cancellation
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};

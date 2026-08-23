import React from 'react';
import { Search } from 'lucide-react';
import { EmptyState } from '../../components/common/EmptyState';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { Alert } from '../../components/common/Alert';

export const AdminOrderLookupPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="bg-white p-6 sm:p-8 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
        <div className="flex items-center justify-between pb-6 border-b border-slate-100">
          <div>
            <h2 className="text-xl font-bold text-slate-900">Order Inspector</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Query individual orders across users via Order Service (`GET /api/v1/orders/:id`)
            </p>
          </div>
        </div>

        <Alert variant="info" title="Phase 1: Admin Structural Shell">
          Direct order lookup and administrative cancellation will be connected in Phase 6.
        </Alert>

        <div className="flex gap-3 max-w-md">
          <Input
            placeholder="Enter Order ID or User ID..."
            leftIcon={<Search className="w-4 h-4" />}
            disabled
          />
          <Button variant="primary" disabled>
            Search
          </Button>
        </div>

        <EmptyState
          icon={<Search className="w-8 h-8 text-slate-400" />}
          title="No Order Selected"
          description="Enter an Order ID to inspect state machine status, line items, and payment reconciliation."
        />
      </div>
    </div>
  );
};

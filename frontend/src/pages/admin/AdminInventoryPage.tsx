import React from 'react';
import { Layers } from 'lucide-react';
import { EmptyState } from '../../components/common/EmptyState';
import { Alert } from '../../components/common/Alert';

export const AdminInventoryPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="bg-white p-6 sm:p-8 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
        <div className="flex items-center justify-between pb-6 border-b border-slate-100">
          <div>
            <h2 className="text-xl font-bold text-slate-900">Inventory & Threshold Controls</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Optimistic locking stock adjustments via `PATCH /api/v1/inventory/:productId/adjust`
            </p>
          </div>
        </div>

        <Alert variant="info" title="Phase 1: Admin Structural Shell">
          Physical inventory adjustments and low-stock threshold triggers will be enabled in Phase 6.
        </Alert>

        <EmptyState
          icon={<Layers className="w-8 h-8 text-slate-400" />}
          title="Inventory Controls Shell"
          description="Available stock quantities, reserved allocations, and reorder alerts will be displayed here."
        />
      </div>
    </div>
  );
};

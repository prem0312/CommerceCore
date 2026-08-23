import React from 'react';
import { Package, Plus } from 'lucide-react';
import { EmptyState } from '../../components/common/EmptyState';
import { Button } from '../../components/common/Button';
import { Alert } from '../../components/common/Alert';

export const AdminProductListPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="bg-white p-6 sm:p-8 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-slate-100">
          <div>
            <h2 className="text-xl font-bold text-slate-900">Products Catalog Management</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Create, update, and soft-delete products with automated Redis cache eviction.
            </p>
          </div>
          <Button variant="primary" size="sm" leftIcon={<Plus className="w-3.5 h-3.5" />} disabled>
            Add New Product
          </Button>
        </div>

        <Alert variant="info" title="Phase 1: Admin Structural Shell">
          Full administrative actions (Create Product, Edit SKU, Soft Delete) communicating with Inventory Service (`/api/v1/products`) will be implemented in Phase 6.
        </Alert>

        <EmptyState
          icon={<Package className="w-8 h-8 text-slate-400" />}
          title="Product Catalog Management Shell"
          description="In Phase 6, administrative product listings, category filters, and live Redis cache invalidation actions will be active here."
        />
      </div>
    </div>
  );
};

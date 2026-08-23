import React from 'react';
import { MapPin, Plus } from 'lucide-react';
import { EmptyState } from '../../components/common/EmptyState';
import { Button } from '../../components/common/Button';

export const AddressBookPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="bg-white p-6 sm:p-8 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
        <div className="flex items-center justify-between pb-6 border-b border-slate-100">
          <div>
            <h2 className="text-xl font-bold text-slate-900">Address Book</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Managed via User Service (`GET /api/v1/users/:id/addresses`)
            </p>
          </div>
          <Button variant="outline" size="sm" leftIcon={<Plus className="w-3.5 h-3.5" />} disabled>
            Add Address
          </Button>
        </div>

        <EmptyState
          icon={<MapPin className="w-8 h-8 text-slate-400" />}
          title="No Shipping Addresses Saved"
          description="Save delivery addresses for faster checkout and order dispatch."
        />
      </div>
    </div>
  );
};

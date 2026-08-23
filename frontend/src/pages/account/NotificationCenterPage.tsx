import React from 'react';
import { Bell } from 'lucide-react';
import { EmptyState } from '../../components/common/EmptyState';

export const NotificationCenterPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="bg-white p-6 sm:p-8 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
        <div className="flex items-center justify-between pb-6 border-b border-slate-100">
          <div>
            <h2 className="text-xl font-bold text-slate-900">Notification Center</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Kafka event audit logs via Notification Service (`GET /api/v1/notifications/user/:userId`)
            </p>
          </div>
        </div>

        <EmptyState
          icon={<Bell className="w-8 h-8 text-slate-400" />}
          title="No Notifications"
          description="Real-time order confirmations, status transitions, and payment notices will appear here."
        />
      </div>
    </div>
  );
};

import React from 'react';
import { User, Mail, Phone, Calendar } from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Alert } from '../../components/common/Alert';
import { Button } from '../../components/common/Button';

export const UserProfilePage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="bg-white p-6 sm:p-8 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-slate-100">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-2xl bg-slate-900 text-white flex items-center justify-center text-xl font-bold shadow-sm">
              U
            </div>
            <div>
              <h2 className="text-xl font-bold text-slate-900">User Profile</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Managed via User Service (`GET /api/v1/users/me`)
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="primary" dot>
              ROLE: USER
            </Badge>
            <Badge variant="success" dot>
              STATUS: ACTIVE
            </Badge>
          </div>
        </div>

        <Alert variant="info" title="Phase 1 Structure">
          Profile data persistence with User Service (`GET /api/v1/users/me`, `PUT /api/v1/users/:id`) will be wired in Phase 5.
        </Alert>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 space-y-1">
            <span className="text-xs text-slate-400 font-medium flex items-center gap-1.5">
              <User className="w-3.5 h-3.5" /> Full Name
            </span>
            <p className="text-sm font-semibold text-slate-800">—</p>
          </div>

          <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 space-y-1">
            <span className="text-xs text-slate-400 font-medium flex items-center gap-1.5">
              <Mail className="w-3.5 h-3.5" /> Email Address
            </span>
            <p className="text-sm font-semibold text-slate-800">—</p>
          </div>

          <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 space-y-1">
            <span className="text-xs text-slate-400 font-medium flex items-center gap-1.5">
              <Phone className="w-3.5 h-3.5" /> Phone Number
            </span>
            <p className="text-sm font-semibold text-slate-800">—</p>
          </div>

          <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 space-y-1">
            <span className="text-xs text-slate-400 font-medium flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5" /> Member Since
            </span>
            <p className="text-sm font-semibold text-slate-800">—</p>
          </div>
        </div>

        <div className="pt-2 flex justify-end">
          <Button variant="outline" disabled>
            Edit Profile
          </Button>
        </div>
      </div>
    </div>
  );
};

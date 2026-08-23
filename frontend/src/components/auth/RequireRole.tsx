import React from 'react';
import { Navigate, useLocation, Outlet, Link } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import type { UserRole } from '../../types/auth';
import { Spinner } from '../common/Spinner';
import { ShieldAlert, ArrowLeft } from 'lucide-react';
import { Button } from '../common/Button';

export interface RequireRoleProps {
  role: UserRole;
  children?: React.ReactNode;
}

export const RequireRole: React.FC<RequireRoleProps> = ({ role, children }) => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="min-h-[50vh] flex items-center justify-center p-8">
        <Spinner size="lg" label="Checking permissions..." />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (user?.role !== role) {
    return (
      <div className="max-w-xl mx-auto my-16 p-8 bg-white rounded-2xl border border-rose-200 shadow-sm text-center space-y-6">
        <div className="w-14 h-14 rounded-2xl bg-rose-50 text-rose-600 flex items-center justify-center mx-auto">
          <ShieldAlert className="w-8 h-8" />
        </div>
        <div className="space-y-2">
          <span className="text-xs font-semibold text-rose-600 uppercase tracking-wider">
            403 Forbidden
          </span>
          <h2 className="text-2xl font-bold text-slate-900">
            Administrative Access Required
          </h2>
          <p className="text-sm text-slate-500 leading-relaxed">
            Your current account (<span className="font-mono font-medium text-slate-700">{user?.email}</span>) holds the <span className="font-semibold text-slate-900">{user?.role}</span> role. Access to this administrative section requires the <span className="font-semibold text-blue-600">{role}</span> role.
          </p>
        </div>
        <div className="pt-2 flex justify-center gap-3">
          <Link to="/">
            <Button variant="primary" leftIcon={<ArrowLeft className="w-4 h-4" />}>
              Back to Storefront
            </Button>
          </Link>
        </div>
      </div>
    );
  }

  return children ? <>{children}</> : <Outlet />;
};

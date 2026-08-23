import React from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { ShieldCheck, Package, Layers, Search } from 'lucide-react';
import { Badge } from '../common/Badge';

export const AdminLayout: React.FC = () => {
  const adminNavItems = [
    { name: 'Products Catalog', path: '/admin/products', icon: <Package className="w-4 h-4" /> },
    { name: 'Inventory & Stock', path: '/admin/inventory', icon: <Layers className="w-4 h-4" /> },
    { name: 'Order Inspector', path: '/admin/orders', icon: <Search className="w-4 h-4" /> },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-10">
      {/* Admin Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 p-6 bg-slate-900 text-white rounded-2xl shadow-sm mb-8">
        <div className="flex items-center gap-3.5">
          <div className="w-10 h-10 rounded-xl bg-white/10 flex items-center justify-center text-blue-400">
            <ShieldCheck className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-white">
                CommerceCore Admin Portal
              </h1>
              <Badge variant="primary" size="sm">
                Role: ADMIN
              </Badge>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Authoritative catalog management, Redis cache invalidation, and stock threshold adjustments.
            </p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
        {/* Sidebar */}
        <aside className="md:col-span-1">
          <nav className="space-y-1 bg-white p-3 rounded-2xl border border-slate-200/80 shadow-xs">
            <div className="px-3 py-2 text-[11px] font-bold uppercase tracking-wider text-slate-400">
              Management
            </div>
            {adminNavItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all ${
                    isActive
                      ? 'bg-slate-900 text-white shadow-xs'
                      : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                  }`
                }
              >
                {item.icon}
                <span>{item.name}</span>
              </NavLink>
            ))}
          </nav>
        </aside>

        {/* Content Area */}
        <div className="md:col-span-3">
          <Outlet />
        </div>
      </div>
    </div>
  );
};

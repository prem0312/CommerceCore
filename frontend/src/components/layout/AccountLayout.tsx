import React from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { User, Package, MapPin, Bell } from 'lucide-react';

export const AccountLayout: React.FC = () => {
  const accountNavItems = [
    { name: 'Profile Overview', path: '/account/profile', icon: <User className="w-4 h-4" /> },
    { name: 'Order History', path: '/account/orders', icon: <Package className="w-4 h-4" /> },
    { name: 'Address Book', path: '/account/addresses', icon: <MapPin className="w-4 h-4" /> },
    { name: 'Notifications', path: '/account/notifications', icon: <Bell className="w-4 h-4" /> },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-10">
      <div className="mb-8">
        <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
          Customer Account
        </h1>
        <p className="text-sm text-slate-500 mt-1">
          Manage your personal profile, shipping addresses, order status and notifications.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
        {/* Sidebar Nav */}
        <aside className="md:col-span-1">
          <nav className="space-y-1 bg-white p-3 rounded-2xl border border-slate-200/80 shadow-xs">
            {accountNavItems.map((item) => (
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

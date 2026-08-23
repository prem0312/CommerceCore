import React from 'react';
import { Outlet } from 'react-router-dom';

export const StoreLayout: React.FC = () => {
  return (
    <div className="w-full">
      <Outlet />
    </div>
  );
};

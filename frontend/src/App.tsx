import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { CartProvider } from './context/CartContext';
import { RequireAuth, RequireRole } from './components/auth';

// Layouts
import { RootLayout } from './components/layout/RootLayout';
import { AccountLayout } from './components/layout/AccountLayout';
import { AdminLayout } from './components/layout/AdminLayout';

// Pages
import { StoreFrontPage } from './pages/StoreFrontPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { NotFoundPage } from './pages/NotFoundPage';

// Account Pages
import { UserProfilePage } from './pages/account/UserProfilePage';
import { OrderHistoryPage } from './pages/account/OrderHistoryPage';
import { OrderDetailPage } from './pages/account/OrderDetailPage';
import { AddressBookPage } from './pages/account/AddressBookPage';
import { NotificationCenterPage } from './pages/account/NotificationCenterPage';

// Admin Pages
import { AdminProductListPage } from './pages/admin/AdminProductListPage';
import { AdminInventoryPage } from './pages/admin/AdminInventoryPage';
import { AdminOrderLookupPage } from './pages/admin/AdminOrderLookupPage';

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <CartProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<RootLayout />}>
              {/* Store & Public Routes */}
              <Route index element={<StoreFrontPage />} />
              <Route path="products/:id" element={<ProductDetailPage />} />
              <Route path="cart" element={<CartPage />} />
              <Route path="login" element={<LoginPage />} />
              <Route path="register" element={<RegisterPage />} />

              {/* Protected Customer Routes */}
              <Route
                path="checkout"
                element={
                  <RequireAuth>
                    <CheckoutPage />
                  </RequireAuth>
                }
              />

              <Route
                path="account"
                element={
                  <RequireAuth>
                    <AccountLayout />
                  </RequireAuth>
                }
              >
                <Route index element={<Navigate to="/account/profile" replace />} />
                <Route path="profile" element={<UserProfilePage />} />
                <Route path="orders" element={<OrderHistoryPage />} />
                <Route path="orders/:id" element={<OrderDetailPage />} />
                <Route path="addresses" element={<AddressBookPage />} />
                <Route path="notifications" element={<NotificationCenterPage />} />
              </Route>

              {/* Protected Admin Routes (ADMIN role required) */}
              <Route
                path="admin"
                element={
                  <RequireRole role="ADMIN">
                    <AdminLayout />
                  </RequireRole>
                }
              >
                <Route index element={<Navigate to="/admin/products" replace />} />
                <Route path="products" element={<AdminProductListPage />} />
                <Route path="inventory" element={<AdminInventoryPage />} />
                <Route path="orders" element={<AdminOrderLookupPage />} />
              </Route>

              {/* Fallback 404 Route */}
              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </CartProvider>
    </AuthProvider>
  );
};

export default App;

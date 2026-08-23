import { describe, it, expect, beforeAll, beforeEach, vi } from 'vitest';
import { render, screen, act, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { CartProvider, useCart } from './context/CartContext';
import { RequireAuth, RequireRole } from './components/auth';
import { setStoredAuth, clearStoredAuth } from './api/client';
import { authApi } from './api/authApi';
import { orderApi } from './api/orderApi';
import { paymentApi } from './api/paymentApi';
import { productApi } from './api/productApi';
import { userApi } from './api/userApi';

import { RootLayout } from './components/layout/RootLayout';
import { AccountLayout } from './components/layout/AccountLayout';
import { AdminLayout } from './components/layout/AdminLayout';

import { StoreFrontPage } from './pages/StoreFrontPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { NotFoundPage } from './pages/NotFoundPage';

import { UserProfilePage } from './pages/account/UserProfilePage';
import { OrderHistoryPage } from './pages/account/OrderHistoryPage';
import { OrderDetailPage } from './pages/account/OrderDetailPage';
import { AddressBookPage } from './pages/account/AddressBookPage';
import { NotificationCenterPage } from './pages/account/NotificationCenterPage';

import { AdminProductListPage } from './pages/admin/AdminProductListPage';
import { AdminInventoryPage } from './pages/admin/AdminInventoryPage';
import { AdminOrderLookupPage } from './pages/admin/AdminOrderLookupPage';

beforeAll(() => {
  window.scrollTo = () => {};
});

beforeEach(() => {
  clearStoredAuth();
  localStorage.clear();
  vi.restoreAllMocks();
});

function renderAppWithRouter(initialEntry: string) {
  return render(
    <AuthProvider>
      <CartProvider>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/" element={<RootLayout />}>
              <Route index element={<StoreFrontPage />} />
              <Route path="products/:id" element={<ProductDetailPage />} />
              <Route path="cart" element={<CartPage />} />
              <Route path="login" element={<LoginPage />} />
              <Route path="register" element={<RegisterPage />} />

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

              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </CartProvider>
    </AuthProvider>
  );
}

// Test harness for cart operations
const CartTestConsumer: React.FC = () => {
  const { items, totalItems, totalPrice, addItem, updateQuantity, removeItem, clearCart } = useCart();
  return (
    <div>
      <span data-testid="cart-total-items">{totalItems}</span>
      <span data-testid="cart-total-price">{totalPrice}</span>
      <span data-testid="cart-item-count">{items.length}</span>
      <button
        data-testid="add-btn"
        onClick={() =>
          addItem({ productId: 1, productName: 'Wireless Keyboard RGB', price: 149.99, maxAvailable: 5 }, 2)
        }
      >
        Add
      </button>
      <button data-testid="update-btn" onClick={() => updateQuantity(1, 3)}>
        Update
      </button>
      <button data-testid="remove-btn" onClick={() => removeItem(1)}>
        Remove
      </button>
      <button data-testid="clear-btn" onClick={() => clearCart()}>
        Clear
      </button>
    </div>
  );
};

describe('CommerceCore Phase 4 Authentication & Customer Flow Tests', () => {
  it('authenticates user and sets session on login form submit', async () => {
    vi.spyOn(authApi, 'login').mockResolvedValueOnce({
      accessToken: 'test-jwt-token',
      tokenType: 'Bearer',
      expiresIn: 86400,
      userId: 1,
      email: 'alice@example.com',
      role: 'USER',
      name: 'Alice Customer',
    });

    renderAppWithRouter('/login');

    fireEvent.change(screen.getByPlaceholderText('name@example.com'), {
      target: { value: 'alice@example.com' },
    });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), {
      target: { value: 'Password123!' },
    });

    act(() => {
      fireEvent.click(screen.getByRole('button', { name: /Sign In/i }));
    });

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith({
        email: 'alice@example.com',
        password: 'Password123!',
      });
    });
  });

  it('registers new customer account and stores session', async () => {
    vi.spyOn(authApi, 'register').mockResolvedValueOnce({
      accessToken: 'test-reg-token',
      tokenType: 'Bearer',
      expiresIn: 86400,
      userId: 2,
      email: 'bob@example.com',
      role: 'USER',
      name: 'Bob Smith',
    });

    renderAppWithRouter('/register');

    fireEvent.change(screen.getByPlaceholderText('Alice Johnson'), {
      target: { value: 'Bob Smith' },
    });
    fireEvent.change(screen.getByPlaceholderText('alice@example.com'), {
      target: { value: 'bob@example.com' },
    });
    const passwordInputs = screen.getAllByPlaceholderText('••••••••');
    fireEvent.change(passwordInputs[0], {
      target: { value: 'Secret123!' },
    });
    fireEvent.change(passwordInputs[1], {
      target: { value: 'Secret123!' },
    });

    act(() => {
      fireEvent.click(screen.getByRole('button', { name: /Create Account/i }));
    });

    await waitFor(() => {
      expect(authApi.register).toHaveBeenCalledWith({
        name: 'Bob Smith',
        email: 'bob@example.com',
        password: 'Secret123!',
      });
    });
  });

  it('executes checkout and calls orderApi.createOrder with idempotency key', async () => {
    setStoredAuth('test-token', {
      userId: 5,
      email: 'shopper@commercecore.io',
      name: 'Test Shopper',
      role: 'USER',
    });

    localStorage.setItem(
      'commercecore_cart',
      JSON.stringify([
        {
          productId: 10,
          productName: 'Mechanical RGB Keyboard',
          price: 149.99,
          quantity: 2,
          category: 'Electronics',
        },
      ])
    );

    vi.spyOn(userApi, 'getAddresses').mockResolvedValueOnce([
      {
        id: 1,
        street: '123 Main St',
        city: 'Seattle',
        state: 'WA',
        postalCode: '98101',
        country: 'USA',
        isDefault: true,
        createdAt: '2026-08-22T10:00:00Z',
      },
    ]);

    vi.spyOn(orderApi, 'createOrder').mockResolvedValueOnce({
      id: 99,
      userId: 5,
      status: 'CONFIRMED',
      totalAmount: 299.98,
      currency: 'USD',
      items: [
        {
          id: 1,
          productId: 10,
          productName: 'Mechanical RGB Keyboard',
          quantity: 2,
          unitPrice: 149.99,
          subtotal: 299.98,
        },
      ],
      createdAt: '2026-08-22T10:00:00Z',
      updatedAt: '2026-08-22T10:00:00Z',
    });

    renderAppWithRouter('/checkout');

    expect(screen.getByText('Mechanical RGB Keyboard')).toBeDefined();

    const placeOrderBtn = screen.getByRole('button', { name: /Place Order & Pay/i });
    act(() => {
      placeOrderBtn.click();
    });

    await waitFor(() => {
      expect(orderApi.createOrder).toHaveBeenCalledTimes(1);
      const callArgs = vi.mocked(orderApi.createOrder).mock.calls[0];
      expect(callArgs[0]).toEqual({
        items: [
          {
            productId: 10,
            productName: 'Mechanical RGB Keyboard',
            quantity: 2,
            unitPrice: 149.99,
          },
        ],
        currency: 'USD',
      });
      expect(callArgs[1]).toMatch(/^idemp-chk-/);
    });
  });

  it('renders Order Details page with line items, status timeline, and payment info', async () => {
    setStoredAuth('test-token', {
      userId: 5,
      email: 'shopper@commercecore.io',
      name: 'Test Shopper',
      role: 'USER',
    });

    vi.spyOn(orderApi, 'getOrderById').mockResolvedValueOnce({
      id: 77,
      userId: 5,
      status: 'CONFIRMED',
      totalAmount: 149.99,
      currency: 'USD',
      items: [
        {
          id: 1,
          productId: 10,
          productName: 'Mechanical RGB Keyboard',
          quantity: 1,
          unitPrice: 149.99,
          subtotal: 149.99,
        },
      ],
      createdAt: '2026-08-22T10:00:00Z',
      updatedAt: '2026-08-22T10:00:00Z',
    });

    vi.spyOn(paymentApi, 'getPaymentByOrderId').mockResolvedValueOnce({
      id: 1,
      orderId: 77,
      transactionId: 'TXN-999-SUCCESS',
      amount: 149.99,
      currency: 'USD',
      status: 'SUCCESS',
      idempotencyKey: 'idemp-pay-77',
      failureReason: null,
      createdAt: '2026-08-22T10:00:01Z',
    });

    renderAppWithRouter('/account/orders/77');

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 2, name: /Order #77/i })).toBeDefined();
      expect(screen.getByText('TXN-999-SUCCESS')).toBeDefined();
      expect(screen.getByText('SUCCESS')).toBeDefined();
      expect(screen.getByText(/Cancel Order/i)).toBeDefined();
    });
  });

  it('cancels order when user confirms in CancelOrderModal', async () => {
    setStoredAuth('test-token', {
      userId: 5,
      email: 'shopper@commercecore.io',
      name: 'Test Shopper',
      role: 'USER',
    });

    vi.spyOn(orderApi, 'getOrderById').mockResolvedValueOnce({
      id: 88,
      userId: 5,
      status: 'CONFIRMED',
      totalAmount: 149.99,
      currency: 'USD',
      items: [
        {
          id: 1,
          productId: 10,
          productName: 'Mechanical RGB Keyboard',
          quantity: 1,
          unitPrice: 149.99,
          subtotal: 149.99,
        },
      ],
      createdAt: '2026-08-22T10:00:00Z',
      updatedAt: '2026-08-22T10:00:00Z',
    });

    vi.spyOn(orderApi, 'cancelOrder').mockResolvedValueOnce({
      id: 88,
      userId: 5,
      status: 'CANCELLED',
      totalAmount: 149.99,
      currency: 'USD',
      items: [
        {
          id: 1,
          productId: 10,
          productName: 'Mechanical RGB Keyboard',
          quantity: 1,
          unitPrice: 149.99,
          subtotal: 149.99,
        },
      ],
      createdAt: '2026-08-22T10:00:00Z',
      updatedAt: '2026-08-22T10:05:00Z',
    });

    renderAppWithRouter('/account/orders/88');

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Cancel Order/i })).toBeDefined();
    });

    act(() => {
      screen.getByRole('button', { name: /Cancel Order/i }).click();
    });

    expect(screen.getByText(/Cancel Order #88\?/i)).toBeDefined();

    act(() => {
      screen.getByRole('button', { name: /Confirm Cancellation/i }).click();
    });

    await waitFor(() => {
      expect(orderApi.cancelOrder).toHaveBeenCalledWith(88, {
        reason: 'Customer requested cancellation',
      });
    });
  });

  it('renders Order History list with orders from Order Service', async () => {
    setStoredAuth('test-token', {
      userId: 5,
      email: 'shopper@commercecore.io',
      name: 'Test Shopper',
      role: 'USER',
    });

    vi.spyOn(orderApi, 'getUserOrders').mockResolvedValueOnce({
      content: [
        {
          id: 55,
          userId: 5,
          status: 'DELIVERED',
          totalAmount: 149.99,
          currency: 'USD',
          items: [
            {
              id: 1,
              productId: 10,
              productName: 'Mechanical RGB Keyboard',
              quantity: 1,
              unitPrice: 149.99,
              subtotal: 149.99,
            },
          ],
          createdAt: '2026-08-22T10:00:00Z',
          updatedAt: '2026-08-22T10:00:00Z',
        },
      ],
      totalPages: 1,
      totalElements: 1,
      size: 10,
      number: 0,
      first: true,
      last: true,
      empty: false,
      numberOfElements: 1,
      pageable: { pageNumber: 0, pageSize: 10, offset: 0, paged: true, unpaged: false, sort: { sorted: true, unsorted: false, empty: false } },
      sort: { sorted: true, unsorted: false, empty: false },
    });

    renderAppWithRouter('/account/orders');

    await waitFor(() => {
      expect(screen.getByText('Order #55')).toBeDefined();
      expect(screen.getByText('Delivered')).toBeDefined();
    });
  });

  it('renders Storefront Home and displays fetched products from Product API', async () => {
    vi.spyOn(productApi, 'listProducts').mockResolvedValueOnce({
      content: [
        {
          id: 1,
          name: 'Wireless Mechanical Keyboard RGB',
          description: 'Tactile switches',
          price: 149.99,
          category: 'Electronics',
          active: true,
          createdAt: '2026-08-22T10:00:00Z',
          updatedAt: '2026-08-22T10:00:00Z',
        },
      ],
      totalPages: 1,
      totalElements: 1,
      size: 12,
      number: 0,
      first: true,
      last: true,
      empty: false,
      numberOfElements: 1,
      pageable: { pageNumber: 0, pageSize: 12, offset: 0, paged: true, unpaged: false, sort: { sorted: true, unsorted: false, empty: false } },
      sort: { sorted: true, unsorted: false, empty: false },
    });

    renderAppWithRouter('/');
    expect(screen.getByRole('heading', { level: 1, name: /CommerceCore Product Catalogue/i })).toBeDefined();

    await waitFor(() => {
      expect(screen.getByText('Wireless Mechanical Keyboard RGB')).toBeDefined();
    });
  });

  it('manages cart state mutations correctly with CartProvider', () => {
    render(
      <CartProvider>
        <CartTestConsumer />
      </CartProvider>
    );

    expect(screen.getByTestId('cart-total-items').textContent).toBe('0');
    expect(screen.getByTestId('cart-total-price').textContent).toBe('0');

    // Add 2 items at $149.99
    act(() => {
      screen.getByTestId('add-btn').click();
    });

    expect(screen.getByTestId('cart-total-items').textContent).toBe('2');
    expect(Number(screen.getByTestId('cart-total-price').textContent)).toBeCloseTo(299.98);

    // Update quantity to 3
    act(() => {
      screen.getByTestId('update-btn').click();
    });

    expect(screen.getByTestId('cart-total-items').textContent).toBe('3');
    expect(Number(screen.getByTestId('cart-total-price').textContent)).toBeCloseTo(449.97);

    // Clear cart
    act(() => {
      screen.getByTestId('clear-btn').click();
    });

    expect(screen.getByTestId('cart-total-items').textContent).toBe('0');
    expect(screen.getByTestId('cart-total-price').textContent).toBe('0');
  });

  it('blocks non-admin customer from /admin/products with 403 Forbidden message', () => {
    setStoredAuth('mock-jwt-token-shopper', {
      userId: 10,
      email: 'shopper@commercecore.io',
      name: 'Alice Customer',
      role: 'USER',
    });

    renderAppWithRouter('/admin/products');
    expect(screen.getByText(/403 Forbidden/i)).toBeDefined();
    expect(screen.getByText(/Administrative Access Required/i)).toBeDefined();
  });
});

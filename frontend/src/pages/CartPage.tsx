import React from 'react';
import { Link } from 'react-router-dom';
import { 
  ShoppingCart, 
  Trash2, 
  Plus, 
  Minus, 
  ArrowRight, 
  ArrowLeft,
  ShieldCheck,
  ShoppingBag
} from 'lucide-react';
import { useCart } from '../hooks/useCart';
import { formatCurrency } from '../utils/formatters';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ProductImage } from '../components/store/ProductImage';

export const CartPage: React.FC = () => {
  const { items, totalItems, totalPrice, updateQuantity, removeItem, clearCart } = useCart();

  if (items.length === 0) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
        <EmptyState
          icon={<ShoppingCart className="w-10 h-10 text-slate-400" />}
          title="Your Shopping Cart is Empty"
          description="Explore our product catalogue to find items with real-time stock availability and instant checkout."
          action={
            <Link to="/">
              <Button variant="primary" size="md" rightIcon={<ArrowRight className="w-4 h-4" />}>
                Explore Products
              </Button>
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-8">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-slate-200">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
            Shopping Cart
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            {totalItems} item{totalItems > 1 ? 's' : ''} in your cart
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link to="/">
            <Button variant="outline" size="sm" leftIcon={<ArrowLeft className="w-4 h-4" />}>
              Continue Shopping
            </Button>
          </Link>
          <Button variant="ghost" size="sm" onClick={clearCart} className="text-slate-400 hover:text-rose-600 hover:bg-rose-50">
            Clear Cart
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-10 items-start">
        {/* Left: Cart Items List */}
        <div className="lg:col-span-2 bg-white rounded-3xl border border-slate-200/80 p-6 sm:p-8 shadow-xs divide-y divide-slate-100">
          {items.map((item) => {
            const lineTotal = item.price * item.quantity;

            return (
              <div key={item.productId} className="py-6 first:pt-0 last:pb-0 flex flex-col sm:flex-row items-start sm:items-center gap-5">
                {/* Product Thumbnail */}
                <Link
                  to={`/products/${item.productId}`}
                  className="w-20 h-20 rounded-2xl overflow-hidden bg-slate-100 shrink-0 border border-slate-200/60"
                >
                  <ProductImage
                    category={item.category}
                    name={item.productName}
                    aspectRatio="square"
                  />
                </Link>

                {/* Details */}
                <div className="flex-1 space-y-1">
                  <Link
                    to={`/products/${item.productId}`}
                    className="text-base font-bold text-slate-900 hover:text-blue-600 transition-colors"
                  >
                    {item.productName}
                  </Link>
                  <div className="text-xs text-slate-400">
                    Unit Price: <span className="font-semibold text-slate-700">{formatCurrency(item.price)}</span>
                  </div>
                  {item.maxAvailable && (
                    <div className="text-[11px] text-emerald-600 font-medium">
                      {item.maxAvailable} units available in inventory
                    </div>
                  )}
                </div>

                {/* Quantity Controls & Line Price */}
                <div className="flex items-center justify-between w-full sm:w-auto sm:gap-6 pt-2 sm:pt-0">
                  {/* Quantity Counter */}
                  <div className="flex items-center border border-slate-200 rounded-xl bg-slate-50 p-1">
                    <button
                      type="button"
                      onClick={() => updateQuantity(item.productId, item.quantity - 1)}
                      className="p-1.5 text-slate-600 hover:text-slate-900"
                      aria-label="Decrease quantity"
                    >
                      <Minus className="w-3.5 h-3.5" />
                    </button>
                    <span className="w-8 text-center text-xs font-bold text-slate-900">
                      {item.quantity}
                    </span>
                    <button
                      type="button"
                      onClick={() => updateQuantity(item.productId, item.quantity + 1)}
                      disabled={item.maxAvailable !== undefined && item.quantity >= item.maxAvailable}
                      className="p-1.5 text-slate-600 hover:text-slate-900 disabled:opacity-30 disabled:cursor-not-allowed"
                      aria-label="Increase quantity"
                    >
                      <Plus className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  {/* Line Total */}
                  <div className="text-right min-w-[5rem]">
                    <span className="text-base font-bold text-slate-900">
                      {formatCurrency(lineTotal)}
                    </span>
                  </div>

                  {/* Remove Button */}
                  <button
                    type="button"
                    onClick={() => removeItem(item.productId)}
                    className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-xl transition-colors"
                    title="Remove item"
                    aria-label="Remove item"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {/* Right: Order Summary Card */}
        <div className="lg:col-span-1 bg-white rounded-3xl border border-slate-200/80 p-6 sm:p-8 shadow-xs space-y-6">
          <h3 className="text-lg font-bold text-slate-900 pb-4 border-b border-slate-100">
            Order Summary
          </h3>

          <div className="space-y-3 text-sm">
            <div className="flex justify-between text-slate-600">
              <span>Items Subtotal ({totalItems})</span>
              <span className="font-semibold text-slate-900">{formatCurrency(totalPrice)}</span>
            </div>

            <div className="flex justify-between text-slate-600">
              <span>Estimated Shipping</span>
              <span className="font-semibold text-emerald-600">Free</span>
            </div>

            <div className="flex justify-between text-slate-600">
              <span>Taxes</span>
              <span className="font-semibold text-slate-900">$0.00</span>
            </div>

            <div className="pt-3 border-t border-slate-100 flex justify-between items-baseline">
              <span className="text-base font-bold text-slate-900">Total</span>
              <div className="text-right">
                <span className="text-2xl font-extrabold text-slate-900">
                  {formatCurrency(totalPrice)}
                </span>
                <span className="text-xs text-slate-400 block">USD</span>
              </div>
            </div>
          </div>

          <div className="pt-2">
            <Link to="/checkout" className="block w-full">
              <Button
                variant="primary"
                size="lg"
                className="w-full"
                rightIcon={<ShoppingBag className="w-4 h-4" />}
              >
                Proceed to Checkout
              </Button>
            </Link>
          </div>

          <div className="p-4 rounded-2xl bg-slate-50 border border-slate-100 text-xs text-slate-500 space-y-2">
            <div className="flex items-center gap-1.5 font-semibold text-slate-800">
              <ShieldCheck className="w-4 h-4 text-emerald-600" />
              CommerceCore Checkout Guarantee
            </div>
            <p className="text-[11px] leading-relaxed">
              Synchronous inventory reservation and distributed idempotency safeguard your order against duplicate charges.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

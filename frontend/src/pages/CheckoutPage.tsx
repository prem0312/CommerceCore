import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { 
  ShieldCheck, 
  CreditCard, 
  MapPin, 
  ArrowLeft, 
  ShoppingBag, 
  Plus, 
  Check
} from 'lucide-react';
import { useCart } from '../hooks/useCart';
import { useAuth } from '../hooks/useAuth';
import { orderApi } from '../api/orderApi';
import { userApi } from '../api/userApi';
import { generateUUID } from '../utils/idempotency';
import { extractApiErrorMessage } from '../api/client';
import { formatCurrency } from '../utils/formatters';
import type { AddressResponse } from '../types/user';
import { Button } from '../components/common/Button';
import { Input } from '../components/common/Input';
import { Alert } from '../components/common/Alert';
import { EmptyState } from '../components/common/EmptyState';
import { Spinner } from '../components/common/Spinner';

export const CheckoutPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { items, totalItems, totalPrice, clearCart } = useCart();

  // Stable Idempotency Key per checkout attempt session
  const [idempotencyKey] = useState<string>(() => `idemp-chk-${generateUUID()}`);

  const [savedAddresses, setSavedAddresses] = useState<AddressResponse[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [isLoadingAddresses, setIsLoadingAddresses] = useState<boolean>(true);

  // New address form state
  const [showNewAddressForm, setShowNewAddressForm] = useState<boolean>(false);
  const [street, setStreet] = useState('');
  const [city, setCity] = useState('');
  const [state, setState] = useState('');
  const [postalCode, setPostalCode] = useState('');
  const [country, setCountry] = useState('USA');
  const [isSavingAddress, setIsSavingAddress] = useState(false);

  // Checkout submission states
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);

  // Load saved addresses for user
  useEffect(() => {
    const fetchAddresses = async () => {
      if (!user?.userId) return;
      setIsLoadingAddresses(true);
      try {
        const list = await userApi.getAddresses(user.userId);
        setSavedAddresses(list || []);
        if (list && list.length > 0) {
          const defaultAddr = list.find((a) => a.isDefault) || list[0];
          setSelectedAddressId(defaultAddr.id);
        } else {
          setShowNewAddressForm(true);
        }
      } catch {
        setShowNewAddressForm(true);
      } finally {
        setIsLoadingAddresses(false);
      }
    };

    fetchAddresses();
  }, [user?.userId]);

  if (items.length === 0) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
        <EmptyState
          icon={<ShoppingBag className="w-10 h-10 text-slate-400" />}
          title="No Items to Checkout"
          description="Your shopping cart is currently empty. Add items from the product catalogue to proceed."
          action={
            <Link to="/">
              <Button variant="primary">Browse Catalogue</Button>
            </Link>
          }
        />
      </div>
    );
  }

  const handleSaveAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user?.userId) return;
    if (!street.trim() || !city.trim() || !state.trim() || !postalCode.trim() || !country.trim()) {
      setCheckoutError('Please provide all address details.');
      return;
    }

    setIsSavingAddress(true);
    setCheckoutError(null);
    try {
      const newAddress = await userApi.addAddress(user.userId, {
        street: street.trim(),
        city: city.trim(),
        state: state.trim(),
        postalCode: postalCode.trim(),
        country: country.trim(),
        isDefault: savedAddresses.length === 0,
      });

      setSavedAddresses((prev) => [...prev, newAddress]);
      setSelectedAddressId(newAddress.id);
      setShowNewAddressForm(false);
      // Reset form
      setStreet('');
      setCity('');
      setState('');
      setPostalCode('');
    } catch (err) {
      setCheckoutError(extractApiErrorMessage(err, 'Failed to save shipping address.'));
    } finally {
      setIsSavingAddress(false);
    }
  };

  const handlePlaceOrder = async () => {
    // 1. Prevent duplicate submission if already processing
    if (isSubmitting) return;

    setIsSubmitting(true);
    setCheckoutError(null);

    try {
      // 2. Prepare payload matching backend CreateOrderRequest
      const orderPayload = {
        items: items.map((item) => ({
          productId: item.productId,
          productName: item.productName,
          quantity: item.quantity,
          unitPrice: item.price,
        })),
        currency: 'USD',
      };

      // 3. Dispatch to Order Service with distributed Idempotency-Key
      const createdOrder = await orderApi.createOrder(orderPayload, idempotencyKey);

      // 4. Clear client-side cart upon confirmed backend commit
      clearCart();

      // 5. Navigate directly to the Order Details page with success notification
      navigate(`/account/orders/${createdOrder.id}`, {
        state: { isNewOrder: true },
        replace: true,
      });
    } catch (err) {
      setCheckoutError(
        extractApiErrorMessage(
          err,
          'Order creation failed. Some items may have insufficient inventory stock.'
        )
      );
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between pb-6 border-b border-slate-200">
        <div>
          <Link
            to="/cart"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 mb-2 transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5" /> Back to Cart
          </Link>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
            Secure Checkout
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Authoritative order processing through Order & Payment microservices
          </p>
        </div>

        <div className="hidden sm:flex items-center gap-2 text-xs font-semibold text-slate-500 bg-slate-100 px-3 py-1.5 rounded-xl border border-slate-200">
          <ShieldCheck className="w-4 h-4 text-emerald-600" />
          <span>Idempotent Guard Active</span>
        </div>
      </div>

      {checkoutError && (
        <Alert variant="error" title="Checkout Error" onClose={() => setCheckoutError(null)}>
          {checkoutError}
        </Alert>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-10 items-start">
        {/* Left: Shipping & Payment Steps */}
        <div className="lg:col-span-2 space-y-8">
          {/* Step 1: Shipping Address Selection */}
          <div className="bg-white rounded-3xl border border-slate-200/80 p-6 sm:p-8 shadow-xs space-y-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center font-bold text-sm">
                  1
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">Shipping Address</h3>
                  <p className="text-xs text-slate-500">Select or add your destination delivery address</p>
                </div>
              </div>

              {!showNewAddressForm && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setShowNewAddressForm(true)}
                  leftIcon={<Plus className="w-3.5 h-3.5" />}
                >
                  Add Address
                </Button>
              )}
            </div>

            {isLoadingAddresses ? (
              <div className="py-6 flex items-center justify-center">
                <Spinner size="sm" label="Loading saved addresses..." />
              </div>
            ) : showNewAddressForm ? (
              <form onSubmit={handleSaveAddress} className="space-y-4 pt-2 border-t border-slate-100">
                <Input
                  label="Street Address"
                  placeholder="123 Commerce Way, Suite 400"
                  value={street}
                  onChange={(e) => setStreet(e.target.value)}
                  required
                />
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Input
                    label="City"
                    placeholder="Seattle"
                    value={city}
                    onChange={(e) => setCity(e.target.value)}
                    required
                  />
                  <Input
                    label="State / Province"
                    placeholder="WA"
                    value={state}
                    onChange={(e) => setState(e.target.value)}
                    required
                  />
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Input
                    label="Postal Code"
                    placeholder="98101"
                    value={postalCode}
                    onChange={(e) => setPostalCode(e.target.value)}
                    required
                  />
                  <Input
                    label="Country"
                    placeholder="USA"
                    value={country}
                    onChange={(e) => setCountry(e.target.value)}
                    required
                  />
                </div>

                <div className="flex items-center justify-end gap-3 pt-2">
                  {savedAddresses.length > 0 && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => setShowNewAddressForm(false)}
                    >
                      Cancel
                    </Button>
                  )}
                  <Button
                    type="submit"
                    variant="primary"
                    size="sm"
                    isLoading={isSavingAddress}
                  >
                    Save Address
                  </Button>
                </div>
              </form>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {savedAddresses.map((addr) => (
                  <div
                    key={addr.id}
                    onClick={() => setSelectedAddressId(addr.id)}
                    className={`p-4 rounded-2xl border cursor-pointer transition-all ${
                      selectedAddressId === addr.id
                        ? 'border-blue-600 bg-blue-50/40 ring-2 ring-blue-100'
                        : 'border-slate-200 hover:border-slate-300 bg-white'
                    }`}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex items-center gap-2">
                        <MapPin className="w-4 h-4 text-slate-500" />
                        <span className="text-xs font-bold text-slate-900">
                          {addr.street}
                        </span>
                      </div>
                      {selectedAddressId === addr.id && (
                        <Check className="w-4 h-4 text-blue-600 font-bold" />
                      )}
                    </div>
                    <p className="text-xs text-slate-500 mt-2 pl-6">
                      {addr.city}, {addr.state} {addr.postalCode}
                    </p>
                    <p className="text-xs text-slate-400 pl-6">{addr.country}</p>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Step 2: Payment Simulation & Verification */}
          <div className="bg-white rounded-3xl border border-slate-200/80 p-6 sm:p-8 shadow-xs space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center font-bold text-sm">
                2
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-900">Payment Authorization</h3>
                <p className="text-xs text-slate-500">Integrated Payment Service simulation via Outbox & Kafka</p>
              </div>
            </div>

            <div className="p-4 rounded-2xl bg-slate-50 border border-slate-200 text-xs text-slate-600 flex items-center gap-3">
              <CreditCard className="w-5 h-5 text-slate-500 shrink-0" />
              <div>
                <span className="font-semibold text-slate-900 block">
                  Simulated Instant Payment Gateway
                </span>
                <span className="text-[11px] text-slate-500">
                  Payment Service (`:8085`) will automatically execute synchronous authorization upon order placement.
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Right: Order Confirmation Card */}
        <div className="lg:col-span-1 bg-white rounded-3xl border border-slate-200/80 p-6 sm:p-8 shadow-xs space-y-6">
          <h3 className="text-lg font-bold text-slate-900 pb-4 border-b border-slate-100">
            Order Review
          </h3>

          {/* Itemized preview */}
          <div className="space-y-3 max-h-60 overflow-y-auto pr-1">
            {items.map((item) => (
              <div key={item.productId} className="flex items-center justify-between text-xs py-1">
                <div className="flex-1 pr-2">
                  <span className="font-bold text-slate-900 line-clamp-1">{item.productName}</span>
                  <span className="text-slate-400">Qty: {item.quantity} × {formatCurrency(item.price)}</span>
                </div>
                <span className="font-bold text-slate-900">
                  {formatCurrency(item.price * item.quantity)}
                </span>
              </div>
            ))}
          </div>

          <div className="pt-4 border-t border-slate-100 space-y-2 text-sm">
            <div className="flex justify-between text-slate-600 text-xs">
              <span>Items Total ({totalItems})</span>
              <span className="font-semibold text-slate-900">{formatCurrency(totalPrice)}</span>
            </div>
            <div className="flex justify-between text-slate-600 text-xs">
              <span>Shipping</span>
              <span className="font-semibold text-emerald-600">Free</span>
            </div>
            <div className="pt-3 border-t border-slate-100 flex justify-between items-baseline">
              <span className="text-base font-bold text-slate-900">Total Due</span>
              <div className="text-right">
                <span className="text-2xl font-extrabold text-slate-900">
                  {formatCurrency(totalPrice)}
                </span>
                <span className="text-xs text-slate-400 block font-mono">USD</span>
              </div>
            </div>
          </div>

          {/* Idempotency Info */}
          <div className="p-3 bg-slate-50 rounded-xl border border-slate-200 text-[11px] text-slate-500 font-mono flex items-center justify-between">
            <span>Idempotency-Key:</span>
            <span className="text-slate-700 font-semibold truncate max-w-[140px]">{idempotencyKey.slice(0, 18)}...</span>
          </div>

          {/* Place Order CTA Button */}
          <Button
            type="button"
            variant="primary"
            size="lg"
            className="w-full"
            disabled={isSubmitting}
            isLoading={isSubmitting}
            onClick={handlePlaceOrder}
            rightIcon={<CreditCard className="w-4 h-4" />}
          >
            {isSubmitting ? 'Placing Order & Reserving Stock...' : 'Place Order & Pay'}
          </Button>

          <div className="text-[11px] text-slate-400 text-center space-y-1">
            <p>Protected by Spring Cloud Gateway & Redis Cache</p>
          </div>
        </div>
      </div>
    </div>
  );
};

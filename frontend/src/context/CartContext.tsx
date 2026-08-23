import React, { createContext, useContext, useState, useEffect, useMemo } from 'react';
import type { CartItem } from '../types/cart';

export interface CartContextType {
  items: CartItem[];
  totalItems: number;
  totalPrice: number;
  addItem: (item: Omit<CartItem, 'quantity'>, quantity?: number) => void;
  removeItem: (productId: number) => void;
  updateQuantity: (productId: number, quantity: number) => void;
  clearCart: () => void;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

const CART_STORAGE_KEY = 'commercecore_cart';

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [items, setItems] = useState<CartItem[]>(() => {
    try {
      const stored = localStorage.getItem(CART_STORAGE_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
    } catch {
      // ignore storage quota errors
    }
  }, [items]);

  const addItem = (product: Omit<CartItem, 'quantity'>, quantityToAdd = 1) => {
    if (quantityToAdd <= 0) return;

    setItems((currentItems) => {
      const existingIndex = currentItems.findIndex((i) => i.productId === product.productId);

      if (existingIndex > -1) {
        const updated = [...currentItems];
        const existing = updated[existingIndex];
        const newQuantity = existing.quantity + quantityToAdd;

        updated[existingIndex] = {
          ...existing,
          quantity: product.maxAvailable ? Math.min(newQuantity, product.maxAvailable) : newQuantity,
          price: product.price, // ensure updated price
          productName: product.productName,
        };
        return updated;
      }

      return [
        ...currentItems,
        {
          ...product,
          quantity: product.maxAvailable ? Math.min(quantityToAdd, product.maxAvailable) : quantityToAdd,
        },
      ];
    });
  };

  const removeItem = (productId: number) => {
    setItems((currentItems) => currentItems.filter((item) => item.productId !== productId));
  };

  const updateQuantity = (productId: number, newQuantity: number) => {
    if (newQuantity <= 0) {
      removeItem(productId);
      return;
    }

    setItems((currentItems) =>
      currentItems.map((item) => {
        if (item.productId === productId) {
          const clamped = item.maxAvailable ? Math.min(newQuantity, item.maxAvailable) : newQuantity;
          return { ...item, quantity: clamped };
        }
        return item;
      })
    );
  };

  const clearCart = () => {
    setItems([]);
  };

  const totalItems = useMemo(() => {
    return items.reduce((sum, item) => sum + item.quantity, 0);
  }, [items]);

  const totalPrice = useMemo(() => {
    return items.reduce((sum, item) => sum + item.quantity * item.price, 0);
  }, [items]);

  return (
    <CartContext.Provider
      value={{
        items,
        totalItems,
        totalPrice,
        addItem,
        removeItem,
        updateQuantity,
        clearCart,
      }}
    >
      {children}
    </CartContext.Provider>
  );
};

export const useCart = (): CartContextType => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};

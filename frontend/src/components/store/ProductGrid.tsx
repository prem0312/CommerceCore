import React from 'react';
import type { ProductResponse } from '../../types/product';
import { ProductCard } from './ProductCard';
import { EmptyState } from '../common/EmptyState';
import { Alert } from '../common/Alert';
import { Button } from '../common/Button';
import { PackageSearch, RefreshCw } from 'lucide-react';

export interface ProductGridProps {
  products: ProductResponse[];
  isLoading: boolean;
  error: string | null;
  onRetry?: () => void;
  onResetFilter?: () => void;
}

export const ProductGrid: React.FC<ProductGridProps> = ({
  products,
  isLoading,
  error,
  onRetry,
  onResetFilter,
}) => {
  if (error) {
    return (
      <div className="space-y-4">
        <Alert variant="error" title="Failed to Load Product Catalogue">
          {error}
        </Alert>
        {onRetry && (
          <div className="flex justify-center">
            <Button variant="outline" size="sm" onClick={onRetry} leftIcon={<RefreshCw className="w-3.5 h-3.5" />}>
              Retry Loading
            </Button>
          </div>
        )}
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {Array.from({ length: 8 }).map((_, idx) => (
          <div
            key={idx}
            className="bg-white rounded-2xl border border-slate-200 p-5 space-y-4 animate-pulse shadow-xs"
          >
            <div className="aspect-square bg-slate-200 rounded-xl w-full" />
            <div className="space-y-2">
              <div className="h-4 bg-slate-200 rounded-md w-3/4" />
              <div className="h-3 bg-slate-100 rounded-md w-full" />
              <div className="h-3 bg-slate-100 rounded-md w-2/3" />
            </div>
            <div className="pt-4 border-t border-slate-100 flex items-center justify-between">
              <div className="h-5 bg-slate-200 rounded-md w-16" />
              <div className="h-8 bg-slate-200 rounded-lg w-24" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (products.length === 0) {
    return (
      <EmptyState
        icon={<PackageSearch className="w-8 h-8 text-slate-400" />}
        title="No Products Found"
        description="There are currently no products matching your selected category or search keywords in the active catalogue."
        action={
          onResetFilter && (
            <Button variant="secondary" size="sm" onClick={onResetFilter}>
              Clear Filter & Search
            </Button>
          )
        }
      />
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      {products.map((product) => (
        <ProductCard key={product.id} product={product} />
      ))}
    </div>
  );
};

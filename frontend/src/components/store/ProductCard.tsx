import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { ShoppingCart, Check } from 'lucide-react';
import type { ProductResponse } from '../../types/product';
import { formatCurrency } from '../../utils/formatters';
import { useCart } from '../../hooks/useCart';
import { Button } from '../common/Button';
import { Badge } from '../common/Badge';
import { ProductImage } from './ProductImage';

export interface ProductCardProps {
  product: ProductResponse;
}

export const ProductCard: React.FC<ProductCardProps> = ({ product }) => {
  const { addItem } = useCart();
  const [justAdded, setJustAdded] = useState(false);

  const handleAddToCart = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    addItem(
      {
        productId: product.id,
        productName: product.name,
        price: product.price,
        category: product.category,
      },
      1
    );

    setJustAdded(true);
    setTimeout(() => {
      setJustAdded(false);
    }, 1500);
  };

  return (
    <div className="group relative flex flex-col bg-white rounded-2xl border border-slate-200/80 overflow-hidden shadow-xs hover:shadow-md hover:border-slate-300 transition-all duration-200">
      {/* Image Container */}
      <Link to={`/products/${product.id}`} className="block relative overflow-hidden bg-slate-100">
        <ProductImage category={product.category} name={product.name} aspectRatio="square" />
        {product.category && (
          <div className="absolute top-3 left-3 z-10">
            <Badge variant="neutral" size="sm" className="bg-white/90 backdrop-blur-md shadow-xs text-slate-800 font-semibold">
              {product.category}
            </Badge>
          </div>
        )}
      </Link>

      {/* Details Container */}
      <div className="flex-1 flex flex-col p-5">
        <Link to={`/products/${product.id}`} className="block group-hover:text-blue-600 transition-colors">
          <h3 className="text-base font-bold text-slate-900 tracking-tight line-clamp-1">
            {product.name}
          </h3>
        </Link>

        {product.description && (
          <p className="mt-1.5 text-xs text-slate-500 line-clamp-2 leading-relaxed flex-1">
            {product.description}
          </p>
        )}

        {/* Price & Action */}
        <div className="mt-5 pt-4 border-t border-slate-100 flex items-center justify-between gap-3">
          <div>
            <span className="text-xs text-slate-400 font-medium block">Price</span>
            <span className="text-lg font-extrabold text-slate-900 tracking-tight">
              {formatCurrency(product.price)}
            </span>
          </div>

          <Button
            variant={justAdded ? 'secondary' : 'primary'}
            size="sm"
            onClick={handleAddToCart}
            className={justAdded ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : ''}
            leftIcon={
              justAdded ? (
                <Check className="w-3.5 h-3.5 text-emerald-600" />
              ) : (
                <ShoppingCart className="w-3.5 h-3.5" />
              )
            }
          >
            {justAdded ? 'Added' : 'Add to Cart'}
          </Button>
        </div>
      </div>
    </div>
  );
};

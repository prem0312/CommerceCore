import React, { useState } from 'react';
import { Package } from 'lucide-react';
import { cn } from '../../utils/cn';

interface ProductImageProps {
  category?: string;
  name: string;
  className?: string;
  aspectRatio?: 'square' | 'video' | 'portrait';
}

// Curated high-resolution Unsplash product image presets mapped to category keywords
const CATEGORY_IMAGES: Record<string, string> = {
  electronics: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&auto=format&fit=crop&q=80',
  keyboard: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=800&auto=format&fit=crop&q=80',
  audio: 'https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=800&auto=format&fit=crop&q=80',
  accessories: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800&auto=format&fit=crop&q=80',
  apparel: 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=800&auto=format&fit=crop&q=80',
  home: 'https://images.unsplash.com/photo-1513519245088-0e12902e5a38?w=800&auto=format&fit=crop&q=80',
};

export const ProductImage: React.FC<ProductImageProps> = ({
  category = '',
  name,
  className,
  aspectRatio = 'square',
}) => {
  const [imageError, setImageError] = useState(false);

  // Deterministically select an image based on category and name keywords
  const normalizedCategory = category.toLowerCase().trim();
  const normalizedName = name.toLowerCase();

  let imageUrl = 'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=800&auto=format&fit=crop&q=80';

  if (normalizedName.includes('keyboard')) {
    imageUrl = CATEGORY_IMAGES.keyboard;
  } else if (normalizedCategory.includes('electronic') || normalizedName.includes('rgb')) {
    imageUrl = CATEGORY_IMAGES.electronics;
  } else if (normalizedCategory.includes('audio') || normalizedName.includes('headphone') || normalizedName.includes('speaker')) {
    imageUrl = CATEGORY_IMAGES.audio;
  } else if (normalizedCategory.includes('access') || normalizedName.includes('watch')) {
    imageUrl = CATEGORY_IMAGES.accessories;
  } else if (normalizedCategory.includes('apparel') || normalizedCategory.includes('cloth')) {
    imageUrl = CATEGORY_IMAGES.apparel;
  } else if (normalizedCategory.includes('home')) {
    imageUrl = CATEGORY_IMAGES.home;
  }

  const aspectMap = {
    square: 'aspect-square',
    video: 'aspect-video',
    portrait: 'aspect-[3/4]',
  };

  if (imageError) {
    return (
      <div
        className={cn(
          'w-full bg-slate-100 flex flex-col items-center justify-center text-slate-400 p-6',
          aspectMap[aspectRatio],
          className
        )}
      >
        <Package className="w-12 h-12 stroke-1 text-slate-300 mb-2" />
        <span className="text-xs font-medium text-slate-400 text-center line-clamp-1">{name}</span>
      </div>
    );
  }

  return (
    <div className={cn('relative w-full overflow-hidden bg-slate-100', aspectMap[aspectRatio], className)}>
      <img
        src={imageUrl}
        alt={name}
        loading="lazy"
        onError={() => setImageError(true)}
        className="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-300 ease-out"
      />
    </div>
  );
};

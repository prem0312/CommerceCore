import React from 'react';
import { cn } from '../../utils/cn';

export interface SpinnerProps {
  size?: 'sm' | 'md' | 'lg' | 'xl';
  className?: string;
  label?: string;
}

export const Spinner: React.FC<SpinnerProps> = ({
  size = 'md',
  className,
  label,
}) => {
  const sizeMap = {
    sm: 'w-4 h-4 border-2',
    md: 'w-5 h-5 border-2',
    lg: 'w-8 h-8 border-3',
    xl: 'w-12 h-12 border-4',
  };

  return (
    <div className="inline-flex items-center gap-2" role="status" aria-label={label || 'Loading'}>
      <div
        className={cn(
          'animate-spin rounded-full border-current border-t-transparent text-slate-600',
          sizeMap[size],
          className
        )}
      />
      {label && <span className="text-sm text-slate-600 font-medium">{label}</span>}
      <span className="sr-only">{label || 'Loading...'}</span>
    </div>
  );
};

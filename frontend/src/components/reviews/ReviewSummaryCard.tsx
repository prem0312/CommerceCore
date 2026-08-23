import React from 'react';
import type { ReviewSummaryResponse } from '../../types/review';
import { RatingStars } from '../common/RatingStars';

export interface ReviewSummaryCardProps {
  summary: ReviewSummaryResponse | null;
  isLoading?: boolean;
}

export const ReviewSummaryCard: React.FC<ReviewSummaryCardProps> = ({ summary, isLoading }) => {
  if (isLoading) {
    return (
      <div className="bg-slate-50 p-6 rounded-2xl border border-slate-200/80 animate-pulse space-y-4">
        <div className="h-6 bg-slate-200 rounded w-1/3" />
        <div className="h-10 bg-slate-200 rounded w-1/4" />
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-3 bg-slate-200 rounded w-full" />
          ))}
        </div>
      </div>
    );
  }

  if (!summary || summary.totalReviews === 0) {
    return (
      <div className="bg-slate-50/80 p-6 rounded-2xl border border-slate-200 text-center space-y-2">
        <h4 className="text-sm font-bold text-slate-900">Customer Ratings</h4>
        <p className="text-xs text-slate-500">
          This product has no reviews yet. Be the first to share your feedback!
        </p>
      </div>
    );
  }

  const distribution = summary.ratingDistribution || {};
  const total = summary.totalReviews;

  return (
    <div className="bg-slate-50/80 p-6 rounded-2xl border border-slate-200 space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-slate-200">
        <div>
          <h4 className="text-sm font-bold uppercase tracking-wider text-slate-500">
            Customer Reviews
          </h4>
          <div className="flex items-baseline gap-3 mt-1">
            <span className="text-4xl font-extrabold text-slate-900 tracking-tight">
              {summary.averageRating.toFixed(1)}
            </span>
            <span className="text-sm font-medium text-slate-400">out of 5.0</span>
          </div>
          <div className="mt-1">
            <RatingStars rating={summary.averageRating} size="md" />
          </div>
        </div>

        <div className="text-xs font-semibold text-slate-600 sm:text-right">
          <span>Based on {total} verified customer review{total > 1 ? 's' : ''}</span>
        </div>
      </div>

      {/* Distribution Bars */}
      <div className="space-y-2">
        {[5, 4, 3, 2, 1].map((star) => {
          const count = distribution[star] || distribution[String(star)] || 0;
          const percentage = total > 0 ? Math.round((count / total) * 100) : 0;

          return (
            <div key={star} className="flex items-center gap-3 text-xs">
              <span className="w-10 font-semibold text-slate-600 text-right">{star} star</span>
              <div className="flex-1 h-2 bg-slate-200 rounded-full overflow-hidden">
                <div
                  className="h-full bg-amber-400 rounded-full transition-all duration-300"
                  style={{ width: `${percentage}%` }}
                />
              </div>
              <span className="w-12 text-slate-400 text-right font-mono text-[11px]">
                {percentage}% ({count})
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
};

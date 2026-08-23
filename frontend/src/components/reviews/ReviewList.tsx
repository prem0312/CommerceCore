import React from 'react';
import { Trash2, User } from 'lucide-react';
import type { ReviewResponse } from '../../types/review';
import { RatingStars } from '../common/RatingStars';
import { formatDate } from '../../utils/formatters';
import { useAuth } from '../../hooks/useAuth';
import { Spinner } from '../common/Spinner';

export interface ReviewListProps {
  reviews: ReviewResponse[];
  isLoading?: boolean;
  onDeleteReview?: (reviewId: number) => Promise<void>;
  deletingId?: number | null;
}

export const ReviewList: React.FC<ReviewListProps> = ({
  reviews,
  isLoading,
  onDeleteReview,
  deletingId,
}) => {
  const { user, isAdmin } = useAuth();

  if (isLoading) {
    return (
      <div className="py-8 flex items-center justify-center">
        <Spinner size="md" label="Loading reviews..." />
      </div>
    );
  }

  if (reviews.length === 0) {
    return (
      <div className="py-8 text-center text-slate-500 text-sm">
        No reviews written yet.
      </div>
    );
  }

  return (
    <div className="space-y-4 divide-y divide-slate-100">
      {reviews.map((review) => {
        const canDelete =
          (user && user.userId === review.userId) || isAdmin;

        return (
          <div key={review.id} className="pt-4 first:pt-0 space-y-2">
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center text-slate-600">
                  <User className="w-4 h-4" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-slate-900">
                      Customer #{review.userId}
                    </span>
                    <RatingStars rating={review.rating} size="sm" />
                  </div>
                  <span className="text-[11px] text-slate-400">
                    {formatDate(review.createdAt)}
                  </span>
                </div>
              </div>

              {canDelete && onDeleteReview && (
                <button
                  type="button"
                  onClick={() => onDeleteReview(review.id)}
                  disabled={deletingId === review.id}
                  className="p-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors"
                  title="Delete Review"
                  aria-label="Delete Review"
                >
                  {deletingId === review.id ? (
                    <Spinner size="sm" />
                  ) : (
                    <Trash2 className="w-4 h-4" />
                  )}
                </button>
              )}
            </div>

            <p className="text-xs sm:text-sm text-slate-700 leading-relaxed pl-11">
              {review.comment}
            </p>
          </div>
        );
      })}
    </div>
  );
};

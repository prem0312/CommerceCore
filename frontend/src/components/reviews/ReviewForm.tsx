import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { RatingStars } from '../common/RatingStars';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { useAuth } from '../../hooks/useAuth';
import { Send, LogIn } from 'lucide-react';

export interface ReviewFormProps {
  productId: number;
  onSubmit: (rating: number, comment: string) => Promise<void>;
  isSubmitting?: boolean;
  error?: string | null;
  success?: boolean;
}

export const ReviewForm: React.FC<ReviewFormProps> = ({
  productId: _productId,
  onSubmit,
  isSubmitting = false,
  error,
  success,
}) => {
  const { isAuthenticated } = useAuth();
  const [rating, setRating] = useState<number>(5);
  const [comment, setComment] = useState<string>('');
  const [validationError, setValidationError] = useState<string | null>(null);

  if (!isAuthenticated) {
    return (
      <div className="bg-slate-50 border border-slate-200 rounded-2xl p-6 text-center space-y-3">
        <h4 className="text-sm font-bold text-slate-900">Have You Purchased This Product?</h4>
        <p className="text-xs text-slate-500 max-w-sm mx-auto">
          Sign in to your customer account to submit a verified rating and review.
        </p>
        <div className="pt-1">
          <Link to="/login">
            <Button variant="primary" size="sm" leftIcon={<LogIn className="w-3.5 h-3.5" />}>
              Sign In to Write a Review
            </Button>
          </Link>
        </div>
      </div>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!comment.trim()) {
      setValidationError('Please write a comment before submitting your review.');
      return;
    }
    if (rating < 1 || rating > 5) {
      setValidationError('Please select a star rating from 1 to 5.');
      return;
    }

    setValidationError(null);
    await onSubmit(rating, comment.trim());
    setComment('');
  };

  return (
    <form onSubmit={handleSubmit} className="bg-white border border-slate-200 rounded-2xl p-6 shadow-xs space-y-4">
      <div>
        <h4 className="text-base font-bold text-slate-900">Write a Customer Review</h4>
        <p className="text-xs text-slate-500 mt-0.5">
          Share your experience with other shoppers.
        </p>
      </div>

      {success && (
        <Alert variant="success" title="Review Submitted">
          Thank you! Your product review has been published.
        </Alert>
      )}

      {(error || validationError) && (
        <Alert variant="error" title="Review Submission Failed">
          {validationError || error}
        </Alert>
      )}

      {/* Star Selector */}
      <div className="space-y-1.5">
        <label className="block text-xs font-bold uppercase tracking-wider text-slate-700">
          Your Rating
        </label>
        <div className="flex items-center gap-3">
          <RatingStars
            rating={rating}
            size="lg"
            interactive
            onChange={(newRating) => setRating(newRating)}
          />
          <span className="text-xs font-semibold text-slate-700">
            {rating} Star{rating > 1 ? 's' : ''}
          </span>
        </div>
      </div>

      {/* Comment Input */}
      <div className="space-y-1.5">
        <label htmlFor="review-comment" className="block text-xs font-bold uppercase tracking-wider text-slate-700">
          Review Comment
        </label>
        <textarea
          id="review-comment"
          rows={3}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="What did you like or dislike about this product?"
          className="w-full rounded-xl border border-slate-200 p-3 text-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-slate-900 focus:border-transparent transition-all shadow-xs"
          maxLength={1000}
        />
        <div className="flex justify-end">
          <span className="text-[11px] text-slate-400">{comment.length}/1000</span>
        </div>
      </div>

      <div className="flex justify-end pt-2">
        <Button
          type="submit"
          variant="primary"
          size="sm"
          isLoading={isSubmitting}
          leftIcon={<Send className="w-3.5 h-3.5" />}
        >
          Submit Review
        </Button>
      </div>
    </form>
  );
};

import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { 
  ArrowLeft, 
  ShoppingCart, 
  Check, 
  ShieldCheck, 
  RefreshCw, 
  Plus, 
  Minus,
  Clock,
  Layers
} from 'lucide-react';
import { productApi } from '../api/productApi';
import { inventoryApi } from '../api/inventoryApi';
import { reviewApi } from '../api/reviewApi';
import { extractApiErrorMessage } from '../api/client';
import type { ProductResponse } from '../types/product';
import type { InventoryResponse } from '../types/inventory';
import type { ReviewResponse, ReviewSummaryResponse } from '../types/review';
import { formatCurrency, formatDate } from '../utils/formatters';
import { useCart } from '../hooks/useCart';
import { useAuth } from '../hooks/useAuth';
import { Button } from '../components/common/Button';
import { Badge } from '../components/common/Badge';
import { Alert } from '../components/common/Alert';
import { EmptyState } from '../components/common/EmptyState';
import { ProductImage } from '../components/store/ProductImage';
import { ReviewSummaryCard } from '../components/reviews/ReviewSummaryCard';
import { ReviewList } from '../components/reviews/ReviewList';
import { ReviewForm } from '../components/reviews/ReviewForm';

export const ProductDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const productId = id ? parseInt(id, 10) : NaN;

  const { addItem } = useCart();
  const { isAuthenticated } = useAuth();

  const [product, setProduct] = useState<ProductResponse | null>(null);
  const [inventory, setInventory] = useState<InventoryResponse | null>(null);
  const [reviews, setReviews] = useState<ReviewResponse[]>([]);
  const [reviewSummary, setReviewSummary] = useState<ReviewSummaryResponse | null>(null);

  const [isLoadingProduct, setIsLoadingProduct] = useState<boolean>(true);
  const [isLoadingReviews, setIsLoadingReviews] = useState<boolean>(false);
  const [productError, setProductError] = useState<string | null>(null);

  const [quantity, setQuantity] = useState<number>(1);
  const [addedFeedback, setAddedFeedback] = useState<boolean>(false);

  // Review submission state
  const [isSubmittingReview, setIsSubmittingReview] = useState<boolean>(false);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [reviewSuccess, setReviewSuccess] = useState<boolean>(false);
  const [deletingReviewId, setDeletingReviewId] = useState<number | null>(null);

  const loadProductData = useCallback(async () => {
    if (isNaN(productId)) {
      setProductError('Invalid product ID');
      setIsLoadingProduct(false);
      return;
    }

    setIsLoadingProduct(true);
    setProductError(null);

    try {
      // 1. Fetch Product details from Product API
      const productData = await productApi.getProductById(productId);
      setProduct(productData);

      // 2. Fetch Live Stock Level (handled with fallback if unauthenticated)
      try {
        if (isAuthenticated) {
          const invData = await inventoryApi.getInventoryByProductId(productId);
          setInventory(invData);
        }
      } catch {
        // Stock lookup may require authorization or service sync
      }

      // 3. Fetch Reviews and Rating Summary
      loadReviews();
    } catch (err) {
      setProductError(extractApiErrorMessage(err, 'Unable to load product specifications.'));
    } finally {
      setIsLoadingProduct(false);
    }
  }, [productId, isAuthenticated]);

  const loadReviews = async () => {
    if (isNaN(productId)) return;
    setIsLoadingReviews(true);
    try {
      const [summaryRes, listRes] = await Promise.allSettled([
        reviewApi.getProductReviewSummary(productId),
        reviewApi.getProductReviews(productId, 0, 20),
      ]);

      if (summaryRes.status === 'fulfilled') {
        setReviewSummary(summaryRes.value);
      }
      if (listRes.status === 'fulfilled') {
        setReviews(listRes.value.content || []);
      }
    } catch {
      // Ignore reviews failure for guest
    } finally {
      setIsLoadingReviews(false);
    }
  };

  useEffect(() => {
    loadProductData();
  }, [loadProductData]);

  const handleAddToCart = () => {
    if (!product) return;

    addItem(
      {
        productId: product.id,
        productName: product.name,
        price: product.price,
        category: product.category,
        maxAvailable: inventory?.availableQuantity,
      },
      quantity
    );

    setAddedFeedback(true);
    setTimeout(() => setAddedFeedback(false), 2000);
  };

  const handleReviewSubmit = async (rating: number, comment: string) => {
    if (!product) return;
    setIsSubmittingReview(true);
    setReviewError(null);
    setReviewSuccess(false);

    try {
      await reviewApi.createReview({
        productId: product.id,
        rating,
        comment,
      });

      setReviewSuccess(true);
      // Reload reviews and summary to show newly submitted review
      await loadReviews();
      setTimeout(() => setReviewSuccess(false), 4000);
    } catch (err) {
      setReviewError(extractApiErrorMessage(err, 'Failed to submit review. You may have already reviewed this product.'));
    } finally {
      setIsSubmittingReview(false);
    }
  };

  const handleDeleteReview = async (reviewId: number) => {
    setDeletingReviewId(reviewId);
    try {
      await reviewApi.deleteReview(reviewId);
      await loadReviews();
    } catch (err) {
      alert(extractApiErrorMessage(err, 'Failed to delete review.'));
    } finally {
      setDeletingReviewId(null);
    }
  };

  if (isLoadingProduct) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 animate-pulse space-y-8">
        <div className="h-4 bg-slate-200 rounded w-48" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
          <div className="aspect-square bg-slate-200 rounded-3xl" />
          <div className="space-y-6">
            <div className="h-8 bg-slate-200 rounded w-3/4" />
            <div className="h-6 bg-slate-200 rounded w-1/4" />
            <div className="space-y-2">
              <div className="h-4 bg-slate-200 rounded w-full" />
              <div className="h-4 bg-slate-200 rounded w-5/6" />
            </div>
            <div className="h-12 bg-slate-200 rounded-xl w-1/2" />
          </div>
        </div>
      </div>
    );
  }

  if (productError || !product) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <EmptyState
          icon={<RefreshCw className="w-8 h-8 text-rose-500" />}
          title="Product Not Found"
          description={productError || "The requested product does not exist or is inactive in CommerceCore's catalog."}
          action={
            <div className="flex gap-3">
              <Link to="/">
                <Button variant="secondary" leftIcon={<ArrowLeft className="w-4 h-4" />}>
                  Back to Store
                </Button>
              </Link>
              <Button variant="primary" onClick={loadProductData} leftIcon={<RefreshCw className="w-4 h-4" />}>
                Retry
              </Button>
            </div>
          }
        />
      </div>
    );
  }

  const isAvailable = product.active && (inventory ? inventory.availableQuantity > 0 : true);
  const maxStock = inventory ? inventory.availableQuantity : 99;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-12">
      {/* Breadcrumb Navigation */}
      <div>
        <Link
          to="/"
          className="inline-flex items-center gap-2 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          Back to Product Catalogue
        </Link>
      </div>

      {/* Main Product Details Section */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-10 lg:gap-14 bg-white p-6 sm:p-10 rounded-3xl border border-slate-200/80 shadow-xs">
        {/* Left: Product Image */}
        <div className="space-y-4">
          <div className="rounded-2xl overflow-hidden border border-slate-100 shadow-inner bg-slate-50">
            <ProductImage
              category={product.category}
              name={product.name}
              aspectRatio="square"
            />
          </div>
          <div className="flex items-center justify-between text-xs text-slate-400 px-1">
            <span>SKU ID: #{product.id}</span>
            <span className="flex items-center gap-1">
              <Clock className="w-3.5 h-3.5" />
              Updated {formatDate(product.updatedAt || product.createdAt)}
            </span>
          </div>
        </div>

        {/* Right: Specifications & Purchasing Controls */}
        <div className="flex flex-col justify-between space-y-6">
          <div className="space-y-4">
            {/* Category & Status Badges */}
            <div className="flex items-center gap-2">
              {product.category && (
                <Badge variant="primary" size="sm">
                  {product.category}
                </Badge>
              )}
              {isAvailable ? (
                <Badge variant="success" size="sm" dot>
                  {inventory ? `${inventory.availableQuantity} Units In Stock` : 'In Stock'}
                </Badge>
              ) : (
                <Badge variant="danger" size="sm" dot>
                  Out of Stock
                </Badge>
              )}
            </div>

            {/* Product Title */}
            <h1 className="text-2xl sm:text-3xl lg:text-4xl font-extrabold text-slate-900 tracking-tight">
              {product.name}
            </h1>

            {/* Price Banner */}
            <div className="pt-2">
              <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                {formatCurrency(product.price)}
              </span>
              <span className="text-xs text-slate-400 ml-2 font-medium">USD</span>
            </div>

            {/* Description */}
            <div className="pt-2 border-t border-slate-100">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-2">
                Product Description
              </h4>
              <p className="text-sm text-slate-600 leading-relaxed">
                {product.description || 'No description provided for this product.'}
              </p>
            </div>

            {/* Inventory Meta Box (when authenticated) */}
            {inventory && (
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-xs space-y-2">
                <div className="font-bold text-slate-800 flex items-center gap-1.5">
                  <Layers className="w-4 h-4 text-blue-600" />
                  Inventory Service Status
                </div>
                <div className="grid grid-cols-2 gap-2 text-slate-600 font-mono text-[11px]">
                  <div>Available: <strong className="text-slate-900">{inventory.availableQuantity}</strong></div>
                  <div>Reserved: <strong className="text-slate-900">{inventory.reservedQuantity}</strong></div>
                </div>
              </div>
            )}
          </div>

          {/* Quantity Controls & Add to Cart */}
          <div className="space-y-4 pt-6 border-t border-slate-100">
            {addedFeedback && (
              <Alert variant="success" title="Added to Cart">
                {quantity} × {product.name} added to your shopping cart.
              </Alert>
            )}

            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-4">
              {/* Quantity Counter */}
              <div className="flex items-center border border-slate-200 rounded-xl bg-slate-50 p-1 self-start">
                <button
                  type="button"
                  onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                  disabled={quantity <= 1 || !isAvailable}
                  className="p-2 text-slate-600 hover:text-slate-900 disabled:opacity-30 disabled:cursor-not-allowed"
                  aria-label="Decrease quantity"
                >
                  <Minus className="w-4 h-4" />
                </button>
                <span className="w-12 text-center text-sm font-bold text-slate-900">
                  {quantity}
                </span>
                <button
                  type="button"
                  onClick={() => setQuantity((q) => Math.min(maxStock, q + 1))}
                  disabled={quantity >= maxStock || !isAvailable}
                  className="p-2 text-slate-600 hover:text-slate-900 disabled:opacity-30 disabled:cursor-not-allowed"
                  aria-label="Increase quantity"
                >
                  <Plus className="w-4 h-4" />
                </button>
              </div>

              {/* Add to Cart CTA */}
              <Button
                variant={addedFeedback ? 'secondary' : 'primary'}
                size="lg"
                disabled={!isAvailable}
                onClick={handleAddToCart}
                className={`flex-1 ${addedFeedback ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : ''}`}
                leftIcon={
                  addedFeedback ? (
                    <Check className="w-5 h-5 text-emerald-600" />
                  ) : (
                    <ShoppingCart className="w-5 h-5" />
                  )
                }
              >
                {addedFeedback ? 'Added to Cart!' : isAvailable ? 'Add to Cart' : 'Currently Unavailable'}
              </Button>
            </div>

            {/* Microservices Guarantee Badge */}
            <div className="flex items-center gap-2 text-xs text-slate-400 pt-2">
              <ShieldCheck className="w-4 h-4 text-emerald-500" />
              <span>Optimistic concurrency reservation on checkout</span>
            </div>
          </div>
        </div>
      </div>

      {/* Customer Reviews & Rating Section */}
      <div className="space-y-8">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900">
            Ratings & Customer Feedback
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Verified reviews powered by Review Service (`:8087`)
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
          {/* Rating Summary Breakdown */}
          <div className="lg:col-span-1">
            <ReviewSummaryCard summary={reviewSummary} isLoading={isLoadingReviews} />
          </div>

          {/* Reviews List & Submission Form */}
          <div className="lg:col-span-2 space-y-6">
            {/* Review Form for Authenticated Customer */}
            <ReviewForm
              productId={product.id}
              onSubmit={handleReviewSubmit}
              isSubmitting={isSubmittingReview}
              error={reviewError}
              success={reviewSuccess}
            />

            {/* List of Published Reviews */}
            <div className="bg-white p-6 sm:p-8 rounded-2xl border border-slate-200/80 shadow-xs">
              <h3 className="text-base font-bold text-slate-900 mb-4 pb-3 border-b border-slate-100">
                Customer Reviews ({reviews.length})
              </h3>
              <ReviewList
                reviews={reviews}
                isLoading={isLoadingReviews}
                onDeleteReview={handleDeleteReview}
                deletingId={deletingReviewId}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export interface CreateReviewRequest {
  productId: number;
  rating: number;
  comment: string;
}

export interface ReviewResponse {
  id: number;
  userId: number;
  productId: number;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface ReviewSummaryResponse {
  productId: number;
  averageRating: number;
  totalReviews: number;
  ratingDistribution: Record<string, number>;
}

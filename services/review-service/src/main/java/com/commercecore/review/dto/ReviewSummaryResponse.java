package com.commercecore.review.dto;

import java.util.Map;

public record ReviewSummaryResponse(
        Long productId,
        double averageRating,
        long totalReviews,
        Map<Integer, Long> ratingDistribution
) {
}

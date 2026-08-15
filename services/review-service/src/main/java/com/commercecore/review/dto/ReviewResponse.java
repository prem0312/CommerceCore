package com.commercecore.review.dto;

import com.commercecore.review.model.Review;
import java.time.Instant;
import java.util.Map;

public record ReviewResponse(
        Long id,
        Long userId,
        Long productId,
        Integer rating,
        String comment,
        Instant createdAt
) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(
                r.getId(),
                r.getUserId(),
                r.getProductId(),
                r.getRating(),
                r.getComment(),
                r.getCreatedAt()
        );
    }
}

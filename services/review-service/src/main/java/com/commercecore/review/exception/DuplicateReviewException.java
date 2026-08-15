package com.commercecore.review.exception;

public class DuplicateReviewException extends RuntimeException {
    public DuplicateReviewException(Long userId, Long productId) {
        super("User " + userId + " has already submitted a review for product " + productId);
    }
}

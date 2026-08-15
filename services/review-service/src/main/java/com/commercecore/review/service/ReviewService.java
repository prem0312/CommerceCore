package com.commercecore.review.service;

import com.commercecore.review.dto.CreateReviewRequest;
import com.commercecore.review.dto.ReviewResponse;
import com.commercecore.review.dto.ReviewSummaryResponse;
import com.commercecore.review.exception.DuplicateReviewException;
import com.commercecore.review.exception.ReviewNotFoundException;
import com.commercecore.review.exception.UnauthorizedReviewAccessException;
import com.commercecore.review.model.Review;
import com.commercecore.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public ReviewResponse createReview(Long userId, CreateReviewRequest request) {
        if (reviewRepository.existsByUserIdAndProductId(userId, request.productId())) {
            throw new DuplicateReviewException(userId, request.productId());
        }

        try {
            Review review = new Review(
                    userId,
                    request.productId(),
                    request.rating(),
                    request.comment().trim()
            );

            Review saved = reviewRepository.save(review);
            log.info("User {} submitted review {} for product {}", userId, saved.getId(), request.productId());
            return ReviewResponse.from(saved);
        } catch (DataIntegrityViolationException dive) {
            throw new DuplicateReviewException(userId, request.productId());
        }
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsForProduct(Long productId, Pageable pageable) {
        return reviewRepository.findByProductId(productId, pageable).map(ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse getProductReviewSummary(Long productId) {
        List<Review> reviews = reviewRepository.findByProductId(productId);
        if (reviews.isEmpty()) {
            return new ReviewSummaryResponse(productId, 0.0, 0, Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L));
        }

        double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        double roundedAvg = Math.round(avg * 10.0) / 10.0;

        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        reviews.stream().collect(Collectors.groupingBy(Review::getRating, Collectors.counting()))
                .forEach(distribution::put);

        return new ReviewSummaryResponse(productId, roundedAvg, reviews.size(), distribution);
    }

    @Transactional
    public void deleteReview(Long reviewId, Long requesterId, String requesterRole) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(requesterRole);
        boolean isOwner = review.getUserId().equals(requesterId);

        if (!isAdmin && !isOwner) {
            throw new UnauthorizedReviewAccessException("You are not authorized to delete review " + reviewId);
        }

        reviewRepository.delete(review);
        log.info("Review {} deleted by user {} (role: {})", reviewId, requesterId, requesterRole);
    }
}

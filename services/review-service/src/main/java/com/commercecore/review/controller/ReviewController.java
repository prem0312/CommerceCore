package com.commercecore.review.controller;

import com.commercecore.review.dto.CreateReviewRequest;
import com.commercecore.review.dto.ReviewResponse;
import com.commercecore.review.dto.ReviewSummaryResponse;
import com.commercecore.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "Reviews", description = "Customer reviews, ratings (1-5), duplicate prevention, and aggregate summary metrics")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @Operation(summary = "Submit a product review", description = "Enforces unique review per user per product.")
    public ResponseEntity<ReviewResponse> createReview(
            @Valid @RequestBody CreateReviewRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {

        ReviewResponse response = reviewService.createReview(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get reviews for product", description = "Returns a paginated list of reviews for a given product.")
    public ResponseEntity<Page<ReviewResponse>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReviewResponse> result = reviewService.getReviewsForProduct(productId, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/product/{productId}/summary")
    @Operation(summary = "Get product review summary", description = "Calculates average rating and rating star distribution.")
    public ResponseEntity<ReviewSummaryResponse> getProductReviewSummary(@PathVariable Long productId) {
        ReviewSummaryResponse summary = reviewService.getProductReviewSummary(productId);
        return ResponseEntity.ok(summary);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a review", description = "Review owner or ADMIN role can delete.")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {

        reviewService.deleteReview(id, userId, role);
        return ResponseEntity.noContent().build();
    }
}

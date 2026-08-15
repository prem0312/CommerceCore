package com.commercecore.review.service;

import com.commercecore.review.dto.CreateReviewRequest;
import com.commercecore.review.dto.ReviewResponse;
import com.commercecore.review.dto.ReviewSummaryResponse;
import com.commercecore.review.exception.DuplicateReviewException;
import com.commercecore.review.exception.UnauthorizedReviewAccessException;
import com.commercecore.review.model.Review;
import com.commercecore.review.repository.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTests {

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    @DisplayName("Should create review successfully")
    void createReview_Success() {
        CreateReviewRequest request = new CreateReviewRequest(10L, 5, "Outstanding product!");
        when(reviewRepository.existsByUserIdAndProductId(1L, 10L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> {
            Review r = i.getArgument(0);
            r.setId(100L);
            return r;
        });

        ReviewResponse response = reviewService.createReview(1L, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.rating()).isEqualTo(5);
        verify(reviewRepository, times(1)).save(any(Review.class));
    }

    @Test
    @DisplayName("Should reject duplicate review for same user and product")
    void createReview_Duplicate_ThrowsException() {
        CreateReviewRequest request = new CreateReviewRequest(10L, 4, "Second attempt");
        when(reviewRepository.existsByUserIdAndProductId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(1L, request))
                .isInstanceOf(DuplicateReviewException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should correctly calculate product review summary and distribution")
    void getProductReviewSummary() {
        List<Review> reviews = List.of(
                new Review(1L, 10L, 5, "Great"),
                new Review(2L, 10L, 4, "Good"),
                new Review(3L, 10L, 5, "Awesome")
        );

        when(reviewRepository.findByProductId(10L)).thenReturn(reviews);

        ReviewSummaryResponse summary = reviewService.getProductReviewSummary(10L);

        assertThat(summary.productId()).isEqualTo(10L);
        assertThat(summary.totalReviews()).isEqualTo(3);
        assertThat(summary.averageRating()).isEqualTo(4.7);
        assertThat(summary.ratingDistribution().get(5)).isEqualTo(2L);
        assertThat(summary.ratingDistribution().get(4)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should throw UnauthorizedReviewAccessException when non-owner non-admin tries to delete")
    void deleteReview_Forbidden() {
        Review review = new Review(1L, 10L, 5, "Great");
        review.setId(100L);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.deleteReview(100L, 2L, "USER"))
                .isInstanceOf(UnauthorizedReviewAccessException.class);

        verify(reviewRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should allow ADMIN or owner to delete review")
    void deleteReview_Admin_Success() {
        Review review = new Review(1L, 10L, 5, "Great");
        review.setId(100L);
        when(reviewRepository.findById(100L)).thenReturn(Optional.of(review));

        reviewService.deleteReview(100L, 999L, "ADMIN");

        verify(reviewRepository, times(1)).delete(review);
    }
}

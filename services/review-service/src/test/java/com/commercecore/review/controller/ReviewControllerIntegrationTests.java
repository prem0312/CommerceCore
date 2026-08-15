package com.commercecore.review.controller;

import com.commercecore.review.dto.CreateReviewRequest;
import com.commercecore.review.model.Review;
import com.commercecore.review.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewRepository reviewRepository;

    @BeforeEach
    void setup() {
        reviewRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/reviews creates review and rejects duplicate")
    void testCreateReviewAndDuplicateRejection() throws Exception {
        CreateReviewRequest request = new CreateReviewRequest(10L, 5, "Excellent build quality!");

        // First review -> 201 Created
        mockMvc.perform(post("/api/v1/reviews")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(10))
                .andExpect(jsonPath("$.rating").value(5));

        // Duplicate review by same user -> 409 Conflict
        mockMvc.perform(post("/api/v1/reviews")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_REVIEW"));
    }

    @Test
    @DisplayName("GET /api/v1/reviews/product/{productId}/summary returns aggregated stats")
    void testGetProductReviewSummary() throws Exception {
        reviewRepository.save(new Review(1L, 10L, 5, "Awesome"));
        reviewRepository.save(new Review(2L, 10L, 3, "Average"));

        mockMvc.perform(get("/api/v1/reviews/product/10/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(10))
                .andExpect(jsonPath("$.totalReviews").value(2))
                .andExpect(jsonPath("$.averageRating").value(4.0));
    }
}

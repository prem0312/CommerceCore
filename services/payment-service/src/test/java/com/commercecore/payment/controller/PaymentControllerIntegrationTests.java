package com.commercecore.payment.controller;

import com.commercecore.payment.dto.InitiatePaymentRequest;
import com.commercecore.payment.model.PaymentStatus;
import com.commercecore.payment.repository.IdempotencyRecordRepository;
import com.commercecore.payment.repository.PaymentRepository;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRepository;

    @BeforeEach
    void setup() {
        paymentRepository.deleteAll();
        idempotencyRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/payments processes payment and replays duplicate idempotency key")
    void testProcessPaymentAndIdempotencyReplay() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest(50L, new BigDecimal("75.50"), "USD");
        String idempotencyKey = "pay-key-mock-" + java.util.UUID.randomUUID();

        // First call
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-User-Id", "1")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replay"))
                .andExpect(jsonPath("$.orderId").value(50))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());

        // Repeated call with same idempotency key
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-User-Id", "1")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.orderId").value(50))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}

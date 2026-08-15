package com.commercecore.order.controller;

import com.commercecore.order.client.InventoryClient;
import com.commercecore.order.client.PaymentClient;
import com.commercecore.order.dto.CreateOrderRequest;
import com.commercecore.order.dto.OrderItemRequest;
import com.commercecore.order.dto.PaymentInitiationDto;
import com.commercecore.order.repository.IdempotencyRecordRepository;
import com.commercecore.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRepository;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private PaymentClient paymentClient;

    @BeforeEach
    void setup() {
        orderRepository.deleteAll();
        idempotencyRepository.deleteAll();
        doNothing().when(inventoryClient).batchReserve(any(), any());
        doNothing().when(inventoryClient).batchRelease(any(), any());
        when(paymentClient.initiatePayment(any(), any(), any()))
                .thenReturn(new PaymentInitiationDto.PaymentResponse(1L, 1L, "tx-mock", BigDecimal.TEN, "INITIATED", "key-1"));
    }

    @Test
    @DisplayName("POST /api/v1/orders creates order and repeated request replays result with Idempotent-Replay header")
    void testOrderPlacementAndIdempotencyReplay() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(101L, "Mechanical Keyboard", 1, new BigDecimal("120.00"))),
                "USD"
        );

        String idempotencyKey = "order-idemp-key-" + java.util.UUID.randomUUID();

        // First request -> 201 Created
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", "1")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replay"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(120.00));

        // Second request with SAME idempotency key -> 200 OK with Idempotent-Replay: true header
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", "1")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(120.00));
    }

    @Test
    @DisplayName("GET /api/v1/orders/user/{userId} returns orders list")
    void testGetUserOrders_Success() throws Exception {
        mockMvc.perform(get("/api/v1/orders/user/1")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}

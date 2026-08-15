package com.commercecore.order.service;

import com.commercecore.order.dto.CreateOrderRequest;
import com.commercecore.order.dto.OrderItemRequest;
import com.commercecore.order.model.Order;
import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.repository.OrderRepository;
import com.commercecore.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPersistenceServiceTests {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OrderMetricsService metricsService;

    @InjectMocks
    private OrderPersistenceService orderPersistenceService;

    private CreateOrderRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateOrderRequest(
                List.of(
                        new OrderItemRequest(101L, "Keychron Q1", 1, new BigDecimal("149.99")),
                        new OrderItemRequest(102L, "Keycaps", 2, new BigDecimal("25.00"))
                ),
                "USD"
        );
    }

    @Test
    @DisplayName("Should successfully persist Order, OrderItems and OutboxEvent atomically")
    void persistOrderAndOutbox_Success() throws Exception {
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventType\":\"OrderCreated\"}");

        Order saved = orderPersistenceService.persistOrderAndOutbox(1L, validRequest, "corr-abc");

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(100L);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("199.99"));
        assertThat(saved.getItems()).hasSize(2);

        verify(orderRepository, times(1)).save(any(Order.class));
        verify(outboxRepository, times(1)).save(any(OutboxEvent.class));
        verify(metricsService, times(1)).recordOutboxPending();
    }

    @Test
    @DisplayName("Should throw RuntimeException when OutboxEvent serialization fails")
    void persistOrderAndOutbox_SerializationFailure_ThrowsException() throws Exception {
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization error") {});

        assertThatThrownBy(() -> orderPersistenceService.persistOrderAndOutbox(1L, validRequest, "corr-abc"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create outbox event");

        verify(orderRepository, times(1)).save(any(Order.class));
        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }
}

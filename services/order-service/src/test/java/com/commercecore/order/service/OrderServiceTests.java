package com.commercecore.order.service;

import com.commercecore.order.client.InventoryClient;
import com.commercecore.order.client.PaymentClient;
import com.commercecore.order.dto.CancelOrderRequest;
import com.commercecore.order.dto.CreateOrderRequest;
import com.commercecore.order.dto.OrderItemRequest;
import com.commercecore.order.dto.OrderResponse;
import com.commercecore.order.model.Order;
import com.commercecore.order.model.OrderStatus;
import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.repository.OrderRepository;
import com.commercecore.order.repository.OutboxEventRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderPersistenceService orderPersistenceService;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private OrderMetricsService metricsService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderService orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order(1L, new BigDecimal("199.97"), "USD");
        sampleOrder.setId(10L);
    }

    @Test
    @DisplayName("Should create order, reserve stock, delegate persistence to OrderPersistenceService and initiate payment")
    void createOrder_Success() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(
                        new OrderItemRequest(101L, "Item 1", 2, new BigDecimal("49.99")),
                        new OrderItemRequest(102L, "Item 2", 1, new BigDecimal("99.99"))
                ),
                "USD"
        );

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"items\":[]}");
        when(idempotencyService.acquireOwnership(eq(1L), eq("key-123"), anyString(), eq(OrderResponse.class)))
                .thenReturn(Optional.empty());
        when(orderPersistenceService.persistOrderAndOutbox(eq(1L), eq(request), eq("corr-1")))
                .thenReturn(sampleOrder);

        OrderService.OrderCreationResult result =
                orderService.createOrder(1L, request, "key-123", "corr-1");

        assertThat(result).isNotNull();
        assertThat(result.isReplay()).isFalse();
        assertThat(result.response().id()).isEqualTo(10L);
        assertThat(result.response().totalAmount()).isEqualByComparingTo(new BigDecimal("199.97"));

        verify(inventoryClient, times(1)).batchReserve(any(), eq("corr-1"));
        verify(orderPersistenceService, times(1)).persistOrderAndOutbox(eq(1L), eq(request), eq("corr-1"));
        verify(paymentClient, times(1)).initiatePayment(any(), eq("key-123"), eq("corr-1"));
        verify(idempotencyService, times(1)).recordSuccess(eq(1L), eq("key-123"), eq(10L), any());
        verify(metricsService, times(1)).recordOrderCreated();
    }

    @Test
    @DisplayName("Should replay existing response if idempotency key was previously completed")
    void createOrder_IdempotentReplay() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(101L, "Item 1", 1, new BigDecimal("50"))), "USD");
        OrderResponse cachedResponse = new OrderResponse(10L, 1L, OrderStatus.PAYMENT_PENDING, new BigDecimal("50"), "USD", List.of(), null, null);

        when(idempotencyService.acquireOwnership(eq(1L), eq("key-123"), any(), eq(OrderResponse.class)))
                .thenReturn(Optional.of(new IdempotencyService.IdempotencyCheckResult<>(true, cachedResponse)));

        OrderService.OrderCreationResult result =
                orderService.createOrder(1L, request, "key-123", "corr-1");

        assertThat(result.isReplay()).isTrue();
        assertThat(result.response().id()).isEqualTo(10L);
        verify(inventoryClient, never()).batchReserve(any(), any());
        verify(orderPersistenceService, never()).persistOrderAndOutbox(any(), any(), any());
    }

    @Test
    @DisplayName("Should cancel order, release inventory stock and generate OrderCancelled outbox event")
    void cancelOrder_Success() {
        sampleOrder.setStatus(OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);

        OrderResponse response = orderService.cancelOrder(10L, new CancelOrderRequest("Customer changed mind"), 1L, "USER", "corr-2");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryClient, times(1)).batchRelease(any(), eq("corr-2"));
        verify(outboxRepository, times(1)).save(any(OutboxEvent.class));
    }
}

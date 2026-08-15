package com.commercecore.order.service;

import com.commercecore.order.client.InventoryClient;
import com.commercecore.order.event.EventEnvelope;
import com.commercecore.order.event.OrderEventPayloads;
import com.commercecore.order.model.Order;
import com.commercecore.order.model.OrderItem;
import com.commercecore.order.model.OrderStatus;
import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.repository.OrderRepository;
import com.commercecore.order.repository.OutboxEventRepository;
import com.commercecore.order.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPaymentEventListenerTests {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderPaymentEventListener listener;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = new Order(1L, new BigDecimal("100.00"), "USD");
        testOrder.setId(10L);
        testOrder.setStatus(OrderStatus.PAYMENT_PENDING);
        testOrder.addItem(new OrderItem(101L, "Widget", 2, new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("Should skip duplicate event when already in processed_events table")
    void handlePaymentEvent_DuplicateEvent_Skipped() throws Exception {
        EventEnvelope<OrderEventPayloads.PaymentEventPayload> envelope = new EventEnvelope<>(
                "evt-123", "PaymentCompleted", "2026-08-15T00:00:00Z", "corr-1", "10",
                new OrderEventPayloads.PaymentEventPayload(1L, 10L, "tx-1", BigDecimal.TEN, "SUCCESS", null)
        );

        when(objectMapper.readValue(eq("raw-msg"), any(TypeReference.class))).thenReturn(envelope);
        when(processedEventRepository.existsByEventIdAndConsumerName("evt-123", "order-service-payment-consumer"))
                .thenReturn(true);

        listener.handlePaymentEvent("raw-msg");

        verify(orderRepository, never()).findById(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should confirm order on PaymentCompleted event")
    void handlePaymentEvent_PaymentCompleted_OrderConfirmed() throws Exception {
        EventEnvelope<OrderEventPayloads.PaymentEventPayload> envelope = new EventEnvelope<>(
                "evt-456", "PaymentCompleted", "2026-08-15T00:00:00Z", "corr-1", "10",
                new OrderEventPayloads.PaymentEventPayload(1L, 10L, "tx-1", BigDecimal.TEN, "SUCCESS", null)
        );

        when(objectMapper.readValue(eq("raw-msg"), any(TypeReference.class))).thenReturn(envelope);
        when(processedEventRepository.existsByEventIdAndConsumerName("evt-456", "order-service-payment-consumer"))
                .thenReturn(false);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(testOrder));

        listener.handlePaymentEvent("raw-msg");

        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, times(1)).save(testOrder);
        verify(outboxRepository, times(1)).save(any(OutboxEvent.class));
        verify(processedEventRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should set PAYMENT_FAILED and release inventory on PaymentFailed event")
    void handlePaymentEvent_PaymentFailed_ReleasesInventory() throws Exception {
        EventEnvelope<OrderEventPayloads.PaymentEventPayload> envelope = new EventEnvelope<>(
                "evt-789", "PaymentFailed", "2026-08-15T00:00:00Z", "corr-1", "10",
                new OrderEventPayloads.PaymentEventPayload(1L, 10L, "tx-1", BigDecimal.TEN, "FAILED", "Card declined")
        );

        when(objectMapper.readValue(eq("raw-msg"), any(TypeReference.class))).thenReturn(envelope);
        when(processedEventRepository.existsByEventIdAndConsumerName("evt-789", "order-service-payment-consumer"))
                .thenReturn(false);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(testOrder));

        listener.handlePaymentEvent("raw-msg");

        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(inventoryClient, times(1)).batchRelease(any(), eq("corr-1"));
        verify(outboxRepository, times(1)).save(any(OutboxEvent.class));
    }
}

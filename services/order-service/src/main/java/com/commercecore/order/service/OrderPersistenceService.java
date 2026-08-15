package com.commercecore.order.service;

import com.commercecore.order.dto.CreateOrderRequest;
import com.commercecore.order.dto.OrderItemRequest;
import com.commercecore.order.event.EventEnvelope;
import com.commercecore.order.event.OrderEventPayloads;
import com.commercecore.order.model.Order;
import com.commercecore.order.model.OrderItem;
import com.commercecore.order.model.OrderStatus;
import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.repository.OrderRepository;
import com.commercecore.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(OrderPersistenceService.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OrderMetricsService metricsService;

    public OrderPersistenceService(OrderRepository orderRepository,
                                   OutboxEventRepository outboxRepository,
                                   ObjectMapper objectMapper,
                                   OrderMetricsService metricsService) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    /**
     * Atomically creates and persists the Order, OrderItems, and OutboxEvent
     * within a single guaranteed PostgreSQL transaction.
     * Any serialization or persistence failure in this boundary rolls back the entire transaction.
     */
    @Transactional
    public Order persistOrderAndOutbox(Long userId, CreateOrderRequest request, String correlationId) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        Order order = new Order(userId, BigDecimal.ZERO, request.currency());
        order.setStatus(OrderStatus.PAYMENT_PENDING);

        for (OrderItemRequest itemReq : request.items()) {
            OrderItem item = new OrderItem(
                    itemReq.productId(),
                    itemReq.productName(),
                    itemReq.quantity(),
                    itemReq.unitPrice()
            );
            order.addItem(item);
            totalAmount = totalAmount.add(item.getSubtotal());
        }
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        // Build and persist OutboxEvent for OrderCreated
        try {
            List<OrderEventPayloads.OrderItemDto> eventItems = savedOrder.getItems().stream()
                    .map(i -> new OrderEventPayloads.OrderItemDto(
                            i.getProductId(),
                            i.getProductName(),
                            i.getQuantity(),
                            i.getUnitPrice(),
                            i.getSubtotal()))
                    .toList();

            OrderEventPayloads.OrderCreatedPayload payload = new OrderEventPayloads.OrderCreatedPayload(
                    savedOrder.getId(),
                    savedOrder.getUserId(),
                    savedOrder.getTotalAmount(),
                    savedOrder.getCurrency(),
                    eventItems,
                    savedOrder.getCreatedAt()
            );

            EventEnvelope<OrderEventPayloads.OrderCreatedPayload> envelope =
                    EventEnvelope.of("OrderCreated", String.valueOf(savedOrder.getId()), correlationId, payload);

            OutboxEvent outboxEvent = new OutboxEvent(
                    "OrderCreated",
                    String.valueOf(savedOrder.getId()),
                    envelope.correlationId(),
                    objectMapper.writeValueAsString(envelope)
            );

            outboxRepository.save(outboxEvent);
            metricsService.recordOutboxPending();
        } catch (Exception e) {
            log.error("Failed to serialize or save OutboxEvent for order {}: ", savedOrder.getId(), e);
            throw new RuntimeException("Failed to create outbox event", e);
        }

        return savedOrder;
    }
}

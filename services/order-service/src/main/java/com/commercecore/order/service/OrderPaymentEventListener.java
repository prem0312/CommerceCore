package com.commercecore.order.service;

import com.commercecore.order.client.InventoryClient;
import com.commercecore.order.dto.InventoryReservationDto;
import com.commercecore.order.event.EventEnvelope;
import com.commercecore.order.event.OrderEventPayloads;
import com.commercecore.order.model.Order;
import com.commercecore.order.model.OrderStatus;
import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.model.ProcessedEvent;
import com.commercecore.order.repository.OrderRepository;
import com.commercecore.order.repository.OutboxEventRepository;
import com.commercecore.order.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OrderPaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentEventListener.class);
    private static final String CONSUMER_NAME = "order-service-payment-consumer";

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final InventoryClient inventoryClient;
    private final ObjectMapper objectMapper;

    public OrderPaymentEventListener(OrderRepository orderRepository,
                                     OutboxEventRepository outboxRepository,
                                     ProcessedEventRepository processedEventRepository,
                                     InventoryClient inventoryClient,
                                     ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.processedEventRepository = processedEventRepository;
        this.inventoryClient = inventoryClient;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "commercecore.payments.v1", groupId = "order-service-group")
    @Transactional
    public void handlePaymentEvent(String rawMessage) {
        try {
            EventEnvelope<OrderEventPayloads.PaymentEventPayload> envelope =
                    objectMapper.readValue(rawMessage, new TypeReference<>() {});

            String eventId = envelope.eventId();
            String eventType = envelope.eventType();

            // 1. Consumer Idempotency Guard (Section 17)
            if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
                log.info("Duplicate event {} already processed by {}, skipping", eventId, CONSUMER_NAME);
                return;
            }

            OrderEventPayloads.PaymentEventPayload payload = envelope.payload();
            Long orderId = payload.orderId();

            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                log.error("Received payment event for non-existent order ID: {}", orderId);
                return;
            }

            if ("PaymentCompleted".equalsIgnoreCase(eventType)) {
                log.info("Processing PaymentCompleted for Order ID: {}", orderId);
                order.transitionTo(OrderStatus.CONFIRMED);
                orderRepository.save(order);

                // Queue Outbox Event for OrderConfirmed
                OrderEventPayloads.OrderConfirmedPayload confirmedPayload =
                        new OrderEventPayloads.OrderConfirmedPayload(order.getId(), order.getUserId(), Instant.now());
                EventEnvelope<OrderEventPayloads.OrderConfirmedPayload> outboxEnvelope =
                        EventEnvelope.of("OrderConfirmed", String.valueOf(order.getId()), envelope.correlationId(), confirmedPayload);

                OutboxEvent outboxEvent = new OutboxEvent(
                        "OrderConfirmed",
                        String.valueOf(order.getId()),
                        envelope.correlationId(),
                        objectMapper.writeValueAsString(outboxEnvelope)
                );
                outboxRepository.save(outboxEvent);

            } else if ("PaymentFailed".equalsIgnoreCase(eventType)) {
                log.warn("Processing PaymentFailed for Order ID: {}. Reason: {}", orderId, payload.failureReason());
                order.transitionTo(OrderStatus.PAYMENT_FAILED);
                orderRepository.save(order);

                // Release reserved inventory items
                List<InventoryReservationDto.ReservationItem> releaseItems = order.getItems().stream()
                        .map(i -> new InventoryReservationDto.ReservationItem(i.getProductId(), i.getQuantity()))
                        .toList();
                inventoryClient.batchRelease(new InventoryReservationDto.BatchReleaseRequest(releaseItems), envelope.correlationId());

                // Queue Outbox Event for OrderCancelled
                OrderEventPayloads.OrderCancelledPayload cancelledPayload =
                        new OrderEventPayloads.OrderCancelledPayload(order.getId(), order.getUserId(), "PAYMENT_FAILED", Instant.now());
                EventEnvelope<OrderEventPayloads.OrderCancelledPayload> outboxEnvelope =
                        EventEnvelope.of("OrderCancelled", String.valueOf(order.getId()), envelope.correlationId(), cancelledPayload);

                OutboxEvent outboxEvent = new OutboxEvent(
                        "OrderCancelled",
                        String.valueOf(order.getId()),
                        envelope.correlationId(),
                        objectMapper.writeValueAsString(outboxEnvelope)
                );
                outboxRepository.save(outboxEvent);
            }

            // 2. Mark event as processed in durable DB
            processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER_NAME));
            log.info("Recorded event {} as processed by {}", eventId, CONSUMER_NAME);

        } catch (Exception ex) {
            log.error("Failed to process payment event message: {}", rawMessage, ex);
            throw new RuntimeException("Kafka payment listener error", ex);
        }
    }
}

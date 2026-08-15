package com.commercecore.order.service;

import com.commercecore.order.client.InventoryClient;
import com.commercecore.order.client.PaymentClient;
import com.commercecore.order.dto.*;
import com.commercecore.order.event.EventEnvelope;
import com.commercecore.order.event.OrderEventPayloads;
import com.commercecore.order.exception.OrderNotFoundException;
import com.commercecore.order.exception.UnauthorizedOrderAccessException;
import com.commercecore.order.model.Order;
import com.commercecore.order.model.OrderStatus;
import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.repository.OrderRepository;
import com.commercecore.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderPersistenceService orderPersistenceService;
    private final OutboxEventRepository outboxRepository;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final IdempotencyService idempotencyService;
    private final OrderMetricsService metricsService;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OrderPersistenceService orderPersistenceService,
                        OutboxEventRepository outboxRepository,
                        InventoryClient inventoryClient,
                        PaymentClient paymentClient,
                        IdempotencyService idempotencyService,
                        OrderMetricsService metricsService,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderPersistenceService = orderPersistenceService;
        this.outboxRepository = outboxRepository;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.idempotencyService = idempotencyService;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
    }

    public static record OrderCreationResult(
            boolean isReplay,
            OrderResponse response
    ) {}

    public OrderCreationResult createOrder(
            Long userId,
            CreateOrderRequest request,
            String idempotencyKey,
            String correlationId) {

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            requestJson = request.toString();
        }

        // 1. Idempotency Check: Establish ownership before side effects
        Optional<IdempotencyService.IdempotencyCheckResult<OrderResponse>> replay =
                idempotencyService.acquireOwnership(userId, idempotencyKey, requestJson, OrderResponse.class);

        if (replay.isPresent()) {
            return new OrderCreationResult(true, replay.get().payload());
        }

        List<InventoryReservationDto.ReservationItem> reservationItems = request.items().stream()
                .map(i -> new InventoryReservationDto.ReservationItem(i.productId(), i.quantity()))
                .toList();

        try {
            // 2. Synchronous Inventory Reservation via REST
            log.info("Reserving inventory for order placement by user {}", userId);
            inventoryClient.batchReserve(new InventoryReservationDto.BatchReserveRequest(reservationItems), correlationId);

            // 3. Atomically persist Order + OrderItems + OutboxEvent in dedicated transactional bean
            Order savedOrder = orderPersistenceService.persistOrderAndOutbox(userId, request, correlationId);

            // 4. Synchronous Payment Initiation via REST
            try {
                PaymentInitiationDto.PaymentRequest paymentRequest = new PaymentInitiationDto.PaymentRequest(
                        savedOrder.getId(),
                        savedOrder.getTotalAmount(),
                        savedOrder.getCurrency()
                );
                String paymentIdempotencyKey = (idempotencyKey != null ? idempotencyKey : "order-pay-" + savedOrder.getId());
                paymentClient.initiatePayment(paymentRequest, paymentIdempotencyKey, correlationId);
            } catch (Exception paymentEx) {
                log.warn("Payment initiation request encountered error: {}. Downstream outbox/Kafka will coordinate final status.", paymentEx.getMessage());
            }

            OrderResponse response = OrderResponse.from(savedOrder);

            // 5. Commit completed idempotency record
            idempotencyService.recordSuccess(userId, idempotencyKey, savedOrder.getId(), response);
            metricsService.recordOrderCreated();
            log.info("Order ID {} created successfully for user {}", savedOrder.getId(), userId);

            return new OrderCreationResult(false, response);

        } catch (Exception ex) {
            log.error("Order creation failed for user {}: {}", userId, ex.getMessage());
            // Compensate: release reserved inventory
            inventoryClient.batchRelease(new InventoryReservationDto.BatchReleaseRequest(reservationItems), correlationId);
            idempotencyService.recordFailure(userId, idempotencyKey);
            metricsService.recordOrderFailed();
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long requesterId, String requesterRole) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        validateAccess(order.getUserId(), requesterId, requesterRole);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(Long userId, Long requesterId, String requesterRole, Pageable pageable) {
        validateAccess(userId, requesterId, requesterRole);
        return orderRepository.findByUserId(userId, pageable).map(OrderResponse::from);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, CancelOrderRequest request, Long requesterId, String requesterRole, String correlationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        validateAccess(order.getUserId(), requesterId, requesterRole);

        order.transitionTo(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        // Release reserved stock back to inventory
        List<InventoryReservationDto.ReservationItem> releaseItems = savedOrder.getItems().stream()
                .map(i -> new InventoryReservationDto.ReservationItem(i.getProductId(), i.getQuantity()))
                .toList();
        inventoryClient.batchRelease(new InventoryReservationDto.BatchReleaseRequest(releaseItems), correlationId);

        // Enqueue Outbox event
        try {
            OrderEventPayloads.OrderCancelledPayload payload = new OrderEventPayloads.OrderCancelledPayload(
                    savedOrder.getId(),
                    savedOrder.getUserId(),
                    request != null && request.reason() != null ? request.reason() : "USER_REQUESTED",
                    Instant.now()
            );

            EventEnvelope<OrderEventPayloads.OrderCancelledPayload> envelope =
                    EventEnvelope.of("OrderCancelled", String.valueOf(savedOrder.getId()), correlationId, payload);

            OutboxEvent outboxEvent = new OutboxEvent(
                    "OrderCancelled",
                    String.valueOf(savedOrder.getId()),
                    envelope.correlationId(),
                    objectMapper.writeValueAsString(envelope)
            );
            outboxRepository.save(outboxEvent);
            metricsService.recordOutboxPending();
        } catch (Exception ex) {
            log.error("Failed to generate OrderCancelled outbox event: ", ex);
        }

        log.info("Order ID {} cancelled by user {}", orderId, requesterId);
        return OrderResponse.from(savedOrder);
    }

    private void validateAccess(Long targetUserId, Long requesterId, String requesterRole) {
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requesterRole);
        boolean isOwner = requesterId != null && requesterId.equals(targetUserId);

        if (!isAdmin && !isOwner) {
            throw new UnauthorizedOrderAccessException("You do not have permission to view or manage this order.");
        }
    }
}

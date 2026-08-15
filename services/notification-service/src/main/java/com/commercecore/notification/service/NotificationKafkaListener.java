package com.commercecore.notification.service;

import com.commercecore.notification.model.NotificationChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaListener.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationKafkaListener(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "commercecore.orders.v1", groupId = "notification-service-order-group")
    public void handleOrderEvents(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.path("eventId").asText();
            String eventType = root.path("eventType").asText();
            String correlationId = root.path("correlationId").asText();
            JsonNode payload = root.path("payload");

            Long orderId = payload.path("orderId").asLong();
            Long userId = payload.path("userId").asLong();
            String userEmail = "user" + userId + "@commercecore.io";

            switch (eventType) {
                case "OrderCreated" -> {
                    String total = payload.path("totalAmount").asText();
                    notificationService.createNotificationIfNew(
                            eventId, correlationId, userId, NotificationChannel.EMAIL,
                            "order-created-template", userEmail,
                            "Order Placed #" + orderId,
                            "Your order #" + orderId + " totaling $" + total + " has been placed and is awaiting payment confirmation."
                    );
                }
                case "OrderConfirmed" -> {
                    notificationService.createNotificationIfNew(
                            eventId, correlationId, userId, NotificationChannel.EMAIL,
                            "order-confirmed-template", userEmail,
                            "Order Confirmed #" + orderId,
                            "Your payment for order #" + orderId + " was verified! We are preparing your items for shipment."
                    );
                }
                case "OrderCancelled" -> {
                    String reason = payload.path("reason").asText("Unknown");
                    notificationService.createNotificationIfNew(
                            eventId, correlationId, userId, NotificationChannel.EMAIL,
                            "order-cancelled-template", userEmail,
                            "Order Cancelled #" + orderId,
                            "Your order #" + orderId + " has been cancelled. Reason: " + reason
                    );
                }
                case "OrderShipped" -> {
                    String tracking = payload.path("trackingNumber").asText();
                    notificationService.createNotificationIfNew(
                            eventId, correlationId, userId, NotificationChannel.SMS,
                            "order-shipped-template", userEmail,
                            "Order Shipped #" + orderId,
                            "Great news! Order #" + orderId + " is on its way. Tracking number: " + tracking
                    );
                }
                default -> log.debug("Unhandled order event type: {}", eventType);
            }
        } catch (Exception ex) {
            log.error("Failed to parse order Kafka message: {}", message, ex);
        }
    }

    @KafkaListener(topics = "commercecore.payments.v1", groupId = "notification-service-payment-group")
    public void handlePaymentEvents(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.path("eventId").asText();
            String eventType = root.path("eventType").asText();
            String correlationId = root.path("correlationId").asText();
            JsonNode payload = root.path("payload");

            Long orderId = payload.path("orderId").asLong();
            Long paymentId = payload.path("paymentId").asLong();
            String amount = payload.path("amount").asText();
            String recipient = "billing@commercecore.io";

            if ("PaymentCompleted".equalsIgnoreCase(eventType)) {
                String txn = payload.path("transactionId").asText();
                notificationService.createNotificationIfNew(
                        eventId, correlationId, 1L, NotificationChannel.EMAIL,
                        "payment-receipt-template", recipient,
                        "Payment Receipt - Order #" + orderId,
                        "Payment of $" + amount + " succeeded for order #" + orderId + ". Transaction ID: " + txn
                );
            } else if ("PaymentFailed".equalsIgnoreCase(eventType)) {
                String reason = payload.path("failureReason").asText("Card declined");
                notificationService.createNotificationIfNew(
                        eventId, correlationId, 1L, NotificationChannel.IN_APP,
                        "payment-failed-template", recipient,
                        "Payment Failed - Order #" + orderId,
                        "Payment for order #" + orderId + " of amount $" + amount + " failed: " + reason
                );
            }
        } catch (Exception ex) {
            log.error("Failed to parse payment Kafka message: {}", message, ex);
        }
    }
}

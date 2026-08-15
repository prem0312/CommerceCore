package com.commercecore.notification.service;

import com.commercecore.notification.model.NotificationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaListenerTests {

    @Mock
    private NotificationService notificationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NotificationKafkaListener listener;

    @Test
    @DisplayName("Should parse OrderCreated Kafka message and dispatch notification")
    void handleOrderEvents_OrderCreated() {
        String json = """
                {
                    "eventId": "evt-ord-1",
                    "eventType": "OrderCreated",
                    "correlationId": "corr-ord-1",
                    "aggregateId": "100",
                    "payload": {
                        "orderId": 100,
                        "userId": 1,
                        "totalAmount": 150.00
                    }
                }
                """;

        listener.handleOrderEvents(json);

        verify(notificationService, times(1)).createNotificationIfNew(
                eq("evt-ord-1"), eq("corr-ord-1"), eq(1L), eq(NotificationChannel.EMAIL),
                eq("order-created-template"), eq("user1@commercecore.io"),
                eq("Order Placed #100"), contains("150")
        );
    }

    @Test
    @DisplayName("Should parse PaymentCompleted Kafka message and dispatch receipt")
    void handlePaymentEvents_PaymentCompleted() {
        String json = """
                {
                    "eventId": "evt-pay-1",
                    "eventType": "PaymentCompleted",
                    "correlationId": "corr-pay-1",
                    "aggregateId": "100",
                    "payload": {
                        "paymentId": 50,
                        "orderId": 100,
                        "amount": 150.00,
                        "transactionId": "TXN-12345"
                    }
                }
                """;

        listener.handlePaymentEvents(json);

        verify(notificationService, times(1)).createNotificationIfNew(
                eq("evt-pay-1"), eq("corr-pay-1"), eq(1L), eq(NotificationChannel.EMAIL),
                eq("payment-receipt-template"), eq("billing@commercecore.io"),
                eq("Payment Receipt - Order #100"), contains("TXN-12345")
        );
    }
}

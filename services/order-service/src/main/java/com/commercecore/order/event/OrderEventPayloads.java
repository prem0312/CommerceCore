package com.commercecore.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderEventPayloads {

    public record OrderItemDto(
            Long productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}

    public record OrderCreatedPayload(
            Long orderId,
            Long userId,
            BigDecimal totalAmount,
            String currency,
            List<OrderItemDto> items,
            Instant createdAt
    ) {}

    public record OrderConfirmedPayload(
            Long orderId,
            Long userId,
            Instant confirmedAt
    ) {}

    public record OrderCancelledPayload(
            Long orderId,
            Long userId,
            String reason, // PAYMENT_FAILED, USER_REQUESTED
            Instant cancelledAt
    ) {}

    public record OrderShippedPayload(
            Long orderId,
            Long userId,
            String trackingNumber,
            Instant shippedAt
    ) {}

    public record PaymentEventPayload(
            Long paymentId,
            Long orderId,
            String transactionId,
            BigDecimal amount,
            String status, // SUCCESS, FAILED
            String failureReason
    ) {}
}

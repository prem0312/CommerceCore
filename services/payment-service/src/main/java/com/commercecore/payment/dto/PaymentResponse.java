package com.commercecore.payment.dto;

import com.commercecore.payment.model.Payment;
import com.commercecore.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        String transactionId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String idempotencyKey,
        String failureReason,
        Instant createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getTransactionId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                payment.getFailureReason(),
                payment.getCreatedAt()
        );
    }
}

package com.commercecore.payment.event;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentEventPayloads {

    public record PaymentCompletedPayload(
            Long paymentId,
            Long orderId,
            String transactionId,
            BigDecimal amount,
            String currency,
            Instant completedAt
    ) {}

    public record PaymentFailedPayload(
            Long paymentId,
            Long orderId,
            String transactionId,
            BigDecimal amount,
            String failureReason,
            Instant failedAt
    ) {}
}

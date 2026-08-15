package com.commercecore.order.dto;

import java.math.BigDecimal;

public class PaymentInitiationDto {

    public record PaymentRequest(
            Long orderId,
            BigDecimal amount,
            String currency
    ) {}

    public record PaymentResponse(
            Long id,
            Long orderId,
            String transactionId,
            BigDecimal amount,
            String status, // INITIATED, SUCCESS, FAILED
            String idempotencyKey
    ) {}
}

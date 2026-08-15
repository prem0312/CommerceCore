package com.commercecore.order.model;

import java.util.Set;

public enum OrderStatus {
    PENDING,
    PAYMENT_PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    PAYMENT_FAILED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        if (this == next) return true;
        return switch (this) {
            case PENDING -> Set.of(PAYMENT_PENDING, PAYMENT_FAILED, CANCELLED).contains(next);
            case PAYMENT_PENDING -> Set.of(CONFIRMED, PAYMENT_FAILED, CANCELLED).contains(next);
            case CONFIRMED -> Set.of(PROCESSING, CANCELLED).contains(next);
            case PROCESSING -> Set.of(SHIPPED, CANCELLED).contains(next);
            case SHIPPED -> Set.of(DELIVERED).contains(next);
            case DELIVERED, PAYMENT_FAILED, CANCELLED -> false; // terminal states
        };
    }
}

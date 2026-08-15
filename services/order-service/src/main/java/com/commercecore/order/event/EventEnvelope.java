package com.commercecore.order.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard Event Envelope for all CommerceCore Kafka domain events.
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String occurredAt,
        String correlationId,
        String aggregateId,
        T payload
) {
    public static <T> EventEnvelope<T> of(String eventType, String aggregateId, String correlationId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now().toString(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                aggregateId,
                payload
        );
    }
}

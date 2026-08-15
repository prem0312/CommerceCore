package com.commercecore.notification.event;

import java.time.Instant;
import java.util.Map;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String occurredAt,
        String correlationId,
        String aggregateId,
        T payload
) {
}

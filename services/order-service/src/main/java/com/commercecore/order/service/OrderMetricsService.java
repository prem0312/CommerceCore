package com.commercecore.order.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class OrderMetricsService {

    private final Counter ordersCreatedCounter;
    private final Counter ordersFailedCounter;
    private final Counter idempotencyReplaysCounter;
    private final Counter idempotencyConflictsCounter;
    private final Counter outboxPendingCounter;
    private final Counter outboxFailedCounter;

    public OrderMetricsService(MeterRegistry meterRegistry) {
        this.ordersCreatedCounter = Counter.builder("commerce_orders_created_total")
                .description("Total number of successfully created orders")
                .tag("service", "order-service")
                .register(meterRegistry);

        this.ordersFailedCounter = Counter.builder("commerce_orders_failed_total")
                .description("Total number of failed order creations")
                .tag("service", "order-service")
                .register(meterRegistry);

        this.idempotencyReplaysCounter = Counter.builder("commerce_idempotency_replays_total")
                .description("Total number of replayed responses due to idempotent duplicate requests")
                .tag("service", "order-service")
                .register(meterRegistry);

        this.idempotencyConflictsCounter = Counter.builder("commerce_idempotency_conflicts_total")
                .description("Total number of 409 conflicts due to in-flight or mismatched duplicate requests")
                .tag("service", "order-service")
                .register(meterRegistry);

        this.outboxPendingCounter = Counter.builder("commerce_outbox_pending_total")
                .description("Total number of outbox events queued")
                .tag("service", "order-service")
                .register(meterRegistry);

        this.outboxFailedCounter = Counter.builder("commerce_outbox_failed_total")
                .description("Total number of outbox publishing permanent failures")
                .tag("service", "order-service")
                .register(meterRegistry);
    }

    public void recordOrderCreated() {
        ordersCreatedCounter.increment();
    }

    public void recordOrderFailed() {
        ordersFailedCounter.increment();
    }

    public void recordIdempotencyReplay() {
        idempotencyReplaysCounter.increment();
    }

    public void recordIdempotencyConflict() {
        idempotencyConflictsCounter.increment();
    }

    public void recordOutboxPending() {
        outboxPendingCounter.increment();
    }

    public void recordOutboxFailed() {
        outboxFailedCounter.increment();
    }
}

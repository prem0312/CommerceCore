package com.commercecore.payment.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class PaymentMetricsService {

    private final Counter paymentSuccessCounter;
    private final Counter paymentFailureCounter;
    private final Counter outboxPendingCounter;
    private final Counter outboxFailedCounter;

    public PaymentMetricsService(MeterRegistry meterRegistry) {
        this.paymentSuccessCounter = Counter.builder("commerce_payment_success_total")
                .description("Total number of successful payment transactions")
                .tag("service", "payment-service")
                .register(meterRegistry);

        this.paymentFailureCounter = Counter.builder("commerce_payment_failure_total")
                .description("Total number of failed payment transactions")
                .tag("service", "payment-service")
                .register(meterRegistry);

        this.outboxPendingCounter = Counter.builder("commerce_outbox_pending_total")
                .description("Total number of outbox events queued")
                .tag("service", "payment-service")
                .register(meterRegistry);

        this.outboxFailedCounter = Counter.builder("commerce_outbox_failed_total")
                .description("Total number of outbox publishing failures")
                .tag("service", "payment-service")
                .register(meterRegistry);
    }

    public void recordPaymentSuccess() {
        paymentSuccessCounter.increment();
    }

    public void recordPaymentFailure() {
        paymentFailureCounter.increment();
    }

    public void recordOutboxPending() {
        outboxPendingCounter.increment();
    }

    public void recordOutboxFailed() {
        outboxFailedCounter.increment();
    }
}

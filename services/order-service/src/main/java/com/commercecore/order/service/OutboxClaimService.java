package com.commercecore.order.service;

import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.model.OutboxStatus;
import com.commercecore.order.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Dedicated service managing transactional boundaries for Outbox Event claiming,
 * status transitions, and retry scheduling.
 *
 * Isolating this in a separate Spring bean ensures @Transactional proxy interceptors
 * are invoked properly when called by OutboxPublisherService, avoiding self-invocation bypass.
 */
@Service
public class OutboxClaimService {

    private static final Logger log = LoggerFactory.getLogger(OutboxClaimService.class);
    private static final int MAX_RETRIES = 5;
    private static final int DEFAULT_BATCH_SIZE = 20;

    private final OutboxEventRepository outboxRepository;
    private final OrderMetricsService metricsService;

    @Value("${outbox.publisher.claim-lease-seconds:30}")
    private long claimLeaseSeconds = 30L;

    public OutboxClaimService(OutboxEventRepository outboxRepository,
                              OrderMetricsService metricsService) {
        this.outboxRepository = outboxRepository;
        this.metricsService = metricsService;
    }

    public void setClaimLeaseSeconds(long claimLeaseSeconds) {
        this.claimLeaseSeconds = claimLeaseSeconds;
    }

    /**
     * Atomically selects and claims eligible pending, due retry, or abandoned outbox events using
     * SELECT ... FOR UPDATE SKIP LOCKED.
     * Transitions claimed events to PROCESSING and records the claim owner and timestamp.
     * Transaction commits before returning, releasing DB row locks before network I/O.
     */
    @Transactional
    public List<OutboxEvent> claimEventsSafely(String claimOwner, int batchSize) {
        Instant now = Instant.now();
        Instant claimCutoff = now.minus(Duration.ofSeconds(claimLeaseSeconds));
        List<OutboxEvent> events = outboxRepository.claimPendingEventsForUpdate(now, claimCutoff, batchSize);

        for (OutboxEvent event : events) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setClaimedAt(now);
            event.setClaimOwner(claimOwner);
        }

        return outboxRepository.saveAll(events);
    }

    @Transactional
    public List<OutboxEvent> claimEventsSafely(String claimOwner) {
        return claimEventsSafely(claimOwner, DEFAULT_BATCH_SIZE);
    }

    @Transactional
    public List<OutboxEvent> claimEventsSafely() {
        return claimEventsSafely("order-publisher", DEFAULT_BATCH_SIZE);
    }

    /**
     * Marks an event as PUBLISHED in a distinct transaction upon successful Kafka transmission.
     */
    @Transactional
    public void markPublished(Long eventId) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            outboxRepository.save(event);
            log.info("Outbox event {} marked as PUBLISHED", eventId);
        });
    }

    /**
     * Schedules an event for retry with exponential backoff or marks it as FAILED
     * if max retries are exceeded. Executed in a distinct transaction.
     */
    @Transactional
    public void markRetryOrFailed(Long eventId, String errorMessage) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            int newRetryCount = event.getRetryCount() + 1;
            event.setRetryCount(newRetryCount);

            if (newRetryCount >= MAX_RETRIES) {
                event.setStatus(OutboxStatus.FAILED);
                metricsService.recordOutboxFailed();
                log.error("Outbox event {} permanently failed after {} retries: {}", eventId, MAX_RETRIES, errorMessage);
            } else {
                long backoffSeconds = (long) Math.pow(2, newRetryCount);
                event.setNextRetryAt(Instant.now().plus(Duration.ofSeconds(backoffSeconds)));
                event.setStatus(OutboxStatus.PROCESSING);
                log.warn("Scheduled retry #{} for outbox event {} in {} seconds due to error: {}",
                        newRetryCount, eventId, backoffSeconds, errorMessage);
            }
            outboxRepository.save(event);
        });
    }
}

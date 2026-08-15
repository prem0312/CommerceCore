package com.commercecore.order.service;

import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.model.OutboxStatus;
import com.commercecore.order.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Transactional Outbox Publisher for Order Service.
 * Implements safe multi-instance polling using PostgreSQL SELECT ... FOR UPDATE SKIP LOCKED.
 * Never holds DB locks across Kafka network I/O calls.
 */
@Service
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);
    private static final String TOPIC_ORDERS = "commercecore.orders.v1";
    private static final int MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 20;

    private final String instanceId = "order-publisher-" + UUID.randomUUID().toString().substring(0, 8);
    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OrderMetricsService metricsService;

    @Value("${outbox.publisher.enabled:true}")
    private boolean enabled;

    public OutboxPublisherService(OutboxEventRepository outboxRepository,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  OrderMetricsService metricsService) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.metricsService = metricsService;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:2000}")
    public void pollAndPublishEvents() {
        if (!enabled) return;

        List<OutboxEvent> claimedEvents = claimEventsSafely();
        if (claimedEvents.isEmpty()) {
            return;
        }

        log.debug("Instance {} claimed {} outbox events for publishing", instanceId, claimedEvents.size());

        for (OutboxEvent event : claimedEvents) {
            publishSingleEvent(event);
        }
    }

    @Transactional
    public List<OutboxEvent> claimEventsSafely() {
        Instant now = Instant.now();
        List<OutboxEvent> events = outboxRepository.claimPendingEventsForUpdate(now, BATCH_SIZE);

        for (OutboxEvent event : events) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setClaimedAt(now);
            event.setClaimOwner(instanceId);
        }

        return outboxRepository.saveAll(events);
    }

    public void publishSingleEvent(OutboxEvent event) {
        try {
            // Partition by Aggregate ID (e.g. orderId) to guarantee ordering per order
            kafkaTemplate.send(TOPIC_ORDERS, event.getAggregateId(), event.getPayload())
                    .get(3, TimeUnit.SECONDS);

            markPublished(event.getId());
            log.info("Successfully published outbox event {} of type {} to {}",
                    event.getId(), event.getEventType(), TOPIC_ORDERS);
        } catch (Exception ex) {
            log.error("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage());
            markRetryOrFailed(event.getId(), ex.getMessage());
        }
    }

    @Transactional
    public void markPublished(Long eventId) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            outboxRepository.save(event);
        });
    }

    @Transactional
    public void markRetryOrFailed(Long eventId, String errorMessage) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            int newRetryCount = event.getRetryCount() + 1;
            event.setRetryCount(newRetryCount);

            if (newRetryCount >= MAX_RETRIES) {
                event.setStatus(OutboxStatus.FAILED);
                metricsService.recordOutboxFailed();
                log.error("Outbox event {} permanently failed after {} retries", eventId, MAX_RETRIES);
            } else {
                // Exponential backoff: 2^retryCount seconds (2s, 4s, 8s, 16s...)
                long backoffSeconds = (long) Math.pow(2, newRetryCount);
                event.setNextRetryAt(Instant.now().plus(Duration.ofSeconds(backoffSeconds)));
                event.setStatus(OutboxStatus.PROCESSING);
                log.warn("Scheduled retry #{} for outbox event {} in {} seconds",
                        newRetryCount, eventId, backoffSeconds);
            }
            outboxRepository.save(event);
        });
    }
}

package com.commercecore.payment.service;

import com.commercecore.payment.model.OutboxEvent;
import com.commercecore.payment.model.OutboxStatus;
import com.commercecore.payment.repository.OutboxEventRepository;
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

@Service
public class PaymentOutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxPublisherService.class);
    private static final String TOPIC_PAYMENTS = "commercecore.payments.v1";
    private static final int MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 20;

    private final String instanceId = "payment-publisher-" + UUID.randomUUID().toString().substring(0, 8);
    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentMetricsService metricsService;

    @Value("${outbox.publisher.enabled:true}")
    private boolean enabled;

    public PaymentOutboxPublisherService(OutboxEventRepository outboxRepository,
                                         KafkaTemplate<String, String> kafkaTemplate,
                                         PaymentMetricsService metricsService) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.metricsService = metricsService;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:2000}")
    public void pollAndPublishEvents() {
        if (!enabled) return;

        List<OutboxEvent> claimedEvents = claimEventsSafely();
        if (claimedEvents.isEmpty()) return;

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
            kafkaTemplate.send(TOPIC_PAYMENTS, event.getAggregateId(), event.getPayload())
                    .get(3, TimeUnit.SECONDS);

            markPublished(event.getId());
            log.info("Published payment outbox event {} ({}) to {}",
                    event.getId(), event.getEventType(), TOPIC_PAYMENTS);
        } catch (Exception ex) {
            log.error("Failed to publish payment outbox event {}: {}", event.getId(), ex.getMessage());
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
            } else {
                long backoffSeconds = (long) Math.pow(2, newRetryCount);
                event.setNextRetryAt(Instant.now().plus(Duration.ofSeconds(backoffSeconds)));
                event.setStatus(OutboxStatus.PROCESSING);
            }
            outboxRepository.save(event);
        });
    }
}

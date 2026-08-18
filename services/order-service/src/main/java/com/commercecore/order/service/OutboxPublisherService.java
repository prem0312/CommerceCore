package com.commercecore.order.service;

import com.commercecore.order.model.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Transactional Outbox Publisher for Order Service.
 * Implements safe multi-instance polling using PostgreSQL SELECT ... FOR UPDATE SKIP LOCKED
 * via dedicated OutboxClaimService.
 * Never holds DB locks across Kafka network I/O calls.
 */
@Service
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);
    private static final String TOPIC_ORDERS = "commercecore.orders.v1";
    private static final int BATCH_SIZE = 20;

    private final String instanceId = "order-publisher-" + UUID.randomUUID().toString().substring(0, 8);
    private final OutboxClaimService outboxClaimService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.publisher.enabled:true}")
    private boolean enabled;

    public OutboxPublisherService(OutboxClaimService outboxClaimService,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxClaimService = outboxClaimService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:2000}")
    public void pollAndPublishEvents() {
        if (!enabled) return;

        List<OutboxEvent> claimedEvents = outboxClaimService.claimEventsSafely(instanceId, BATCH_SIZE);
        if (claimedEvents.isEmpty()) {
            return;
        }

        log.debug("Instance {} claimed {} outbox events for publishing", instanceId, claimedEvents.size());

        for (OutboxEvent event : claimedEvents) {
            publishSingleEvent(event);
        }
    }

    public void publishSingleEvent(OutboxEvent event) {
        try {
            // Partition by Aggregate ID (e.g. orderId) to guarantee ordering per order
            kafkaTemplate.send(TOPIC_ORDERS, event.getAggregateId(), event.getPayload())
                    .get(3, TimeUnit.SECONDS);

            outboxClaimService.markPublished(event.getId());
            log.info("Successfully published outbox event {} of type {} to {}",
                    event.getId(), event.getEventType(), TOPIC_ORDERS);
        } catch (Exception ex) {
            log.error("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage());
            outboxClaimService.markRetryOrFailed(event.getId(), ex.getMessage());
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}

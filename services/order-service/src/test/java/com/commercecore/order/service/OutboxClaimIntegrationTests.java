package com.commercecore.order.service;

import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.model.OutboxStatus;
import com.commercecore.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DirtiesContext
class OutboxClaimIntegrationTests {

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @SpyBean
    private OrderMetricsService metricsService;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        reset(metricsService);
    }

    @Test
    @DisplayName("PENDING claim: Pending event is claimed and transitioned to PROCESSING with owner and timestamp")
    void testPendingClaim() {
        OutboxEvent event = new OutboxEvent("OrderCreated", "1", "corr-1", "{\"orderId\":1}");
        outboxRepository.save(event);

        assertThat(outboxRepository.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);

        List<OutboxEvent> claimed = outboxClaimService.claimEventsSafely("instance-pending-test", 10);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getId()).isEqualTo(event.getId());
        assertThat(claimed.get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(claimed.get(0).getClaimOwner()).isEqualTo("instance-pending-test");
        assertThat(claimed.get(0).getClaimedAt()).isNotNull();

        OutboxEvent updated = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(updated.getClaimOwner()).isEqualTo("instance-pending-test");
        assertThat(updated.getClaimedAt()).isNotNull();
    }

    @Test
    @DisplayName("future retry not claimable: PROCESSING event with future nextRetryAt is not claimed")
    void testFutureRetryNotClaimable() {
        OutboxEvent futureRetry = new OutboxEvent("OrderCreated", "2", "corr-2", "{\"orderId\":2}");
        futureRetry.setStatus(OutboxStatus.PROCESSING);
        futureRetry.setRetryCount(1);
        futureRetry.setNextRetryAt(Instant.now().plus(Duration.ofMinutes(5)));
        outboxRepository.save(futureRetry);

        List<OutboxEvent> claimed = outboxClaimService.claimEventsSafely("test-poller", 10);
        assertThat(claimed).isEmpty();
    }

    @Test
    @DisplayName("due retry claimable: PROCESSING event with due nextRetryAt (<= now) is claimed")
    void testDueRetryClaimable() {
        OutboxEvent dueRetry = new OutboxEvent("OrderCreated", "3", "corr-3", "{\"orderId\":3}");
        dueRetry.setStatus(OutboxStatus.PROCESSING);
        dueRetry.setRetryCount(1);
        dueRetry.setNextRetryAt(Instant.now().minus(Duration.ofSeconds(10)));
        outboxRepository.save(dueRetry);

        List<OutboxEvent> claimed = outboxClaimService.claimEventsSafely("retry-claimer", 10);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getId()).isEqualTo(dueRetry.getId());
        assertThat(claimed.get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(claimed.get(0).getClaimOwner()).isEqualTo("retry-claimer");
        assertThat(claimed.get(0).getClaimedAt()).isNotNull();
    }

    @Test
    @DisplayName("expired PROCESSING claim with null nextRetryAt reclaimable: Abandoned claim with claimedAt <= claimCutoff is reclaimed")
    void testExpiredProcessingClaimWithNullNextRetryAtReclaimable() {
        // Default lease is 30s; event was claimed 60s ago with nextRetryAt == null (abandoned/crashed publisher)
        OutboxEvent abandoned = new OutboxEvent("OrderCreated", "4", "corr-4", "{\"orderId\":4}");
        abandoned.setStatus(OutboxStatus.PROCESSING);
        abandoned.setClaimOwner("crashed-publisher-node");
        abandoned.setClaimedAt(Instant.now().minus(Duration.ofSeconds(60)));
        abandoned.setNextRetryAt(null);
        outboxRepository.save(abandoned);

        List<OutboxEvent> claimed = outboxClaimService.claimEventsSafely("recovering-node", 10);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getId()).isEqualTo(abandoned.getId());
        assertThat(claimed.get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(claimed.get(0).getClaimOwner()).isEqualTo("recovering-node");
        assertThat(claimed.get(0).getClaimedAt()).isAfter(Instant.now().minusSeconds(5));

        OutboxEvent inDb = outboxRepository.findById(abandoned.getId()).orElseThrow();
        assertThat(inDb.getClaimOwner()).isEqualTo("recovering-node");
    }

    @Test
    @DisplayName("active PROCESSING claim with null nextRetryAt not reclaimable: Active claim with claimedAt > claimCutoff is not reclaimed")
    void testActiveProcessingClaimWithNullNextRetryAtNotReclaimable() {
        // Default lease is 30s; event was claimed only 5s ago with nextRetryAt == null (active publisher)
        OutboxEvent active = new OutboxEvent("OrderCreated", "5", "corr-5", "{\"orderId\":5}");
        active.setStatus(OutboxStatus.PROCESSING);
        active.setClaimOwner("active-publisher-node");
        active.setClaimedAt(Instant.now().minus(Duration.ofSeconds(5)));
        active.setNextRetryAt(null);
        outboxRepository.save(active);

        List<OutboxEvent> claimed = outboxClaimService.claimEventsSafely("another-node", 10);
        assertThat(claimed).isEmpty();

        OutboxEvent inDb = outboxRepository.findById(active.getId()).orElseThrow();
        assertThat(inDb.getClaimOwner()).isEqualTo("active-publisher-node");
    }

    @Test
    @DisplayName("retryCount unchanged on abandoned claim recovery: Reclaiming abandoned PROCESSING event preserves existing retryCount")
    void testRetryCountUnchangedOnAbandonedClaimRecovery() {
        OutboxEvent abandonedWithRetries = new OutboxEvent("OrderCreated", "6", "corr-6", "{\"orderId\":6}");
        abandonedWithRetries.setStatus(OutboxStatus.PROCESSING);
        abandonedWithRetries.setRetryCount(2);
        abandonedWithRetries.setClaimOwner("old-publisher");
        abandonedWithRetries.setClaimedAt(Instant.now().minus(Duration.ofSeconds(60)));
        abandonedWithRetries.setNextRetryAt(null);
        outboxRepository.save(abandonedWithRetries);

        List<OutboxEvent> claimed = outboxClaimService.claimEventsSafely("recovering-node", 10);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getId()).isEqualTo(abandonedWithRetries.getId());
        assertThat(claimed.get(0).getRetryCount()).isEqualTo(2);

        OutboxEvent inDb = outboxRepository.findById(abandonedWithRetries.getId()).orElseThrow();
        assertThat(inDb.getRetryCount()).isEqualTo(2);
        assertThat(inDb.getClaimOwner()).isEqualTo("recovering-node");
    }

    @Test
    @DisplayName("concurrent SKIP LOCKED claims remain unique: Concurrent threads claim disjoint subsets of outbox events")
    void testConcurrentSkipLockedClaimsRemainUnique() throws Exception {
        int totalEvents = 20;
        List<OutboxEvent> events = new ArrayList<>();
        for (int i = 1; i <= totalEvents; i++) {
            events.add(new OutboxEvent("OrderCreated", String.valueOf(i), "corr-" + i, "{\"orderId\":" + i + "}"));
        }
        outboxRepository.saveAll(events);

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<List<OutboxEvent>>> tasks = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final String nodeName = "concurrent-node-" + t;
            tasks.add(() -> {
                List<OutboxEvent> claimedByThread = new ArrayList<>();
                List<OutboxEvent> batch;
                while (!(batch = outboxClaimService.claimEventsSafely(nodeName, 5)).isEmpty()) {
                    claimedByThread.addAll(batch);
                }
                return claimedByThread;
            });
        }

        List<Future<List<OutboxEvent>>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        Set<Long> allClaimedIds = new HashSet<>();
        int totalClaimedCount = 0;

        for (Future<List<OutboxEvent>> future : futures) {
            List<OutboxEvent> claimed = future.get();
            totalClaimedCount += claimed.size();
            for (OutboxEvent evt : claimed) {
                assertThat(allClaimedIds.add(evt.getId()))
                        .as("Event ID %d was claimed by multiple instances!", evt.getId())
                        .isTrue();
            }
        }

        assertThat(totalClaimedCount).isEqualTo(totalEvents);
        assertThat(allClaimedIds).hasSize(totalEvents);
    }

    @Test
    @DisplayName("PUBLISHED events are not reclaimed by publisher instances")
    void testPublishedEventsAreNotReclaimed() {
        OutboxEvent publishedEvent = new OutboxEvent("OrderCreated", "10", "corr-10", "{\"orderId\":10}");
        publishedEvent.setStatus(OutboxStatus.PUBLISHED);
        publishedEvent.setPublishedAt(Instant.now().minus(Duration.ofMinutes(5)));
        outboxRepository.save(publishedEvent);

        List<OutboxEvent> claimed = outboxClaimService.claimEventsSafely("test-publisher", 10);
        assertThat(claimed).isEmpty();
    }

    @Test
    @DisplayName("Failed Kafka publish does not leave event permanently PROCESSING and transitions to FAILED after max retries")
    void testFailedKafkaPublishDoesNotLeavePermanentlyProcessing() {
        OutboxEvent event = new OutboxEvent("OrderCreated", "50", "corr-50", "{\"orderId\":50}");
        outboxRepository.save(event);

        // Mock Kafka failure
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka Broker Connection Refused"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

        // 1. Claim and publish (First attempt)
        List<OutboxEvent> claimed = outboxClaimService.claimEventsSafely("test-publisher", 10);
        assertThat(claimed).hasSize(1);
        outboxPublisherService.publishSingleEvent(claimed.get(0));

        // Verify scheduled for retry, nextRetryAt is assigned, and retryCount incremented
        OutboxEvent afterFail1 = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(afterFail1.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(afterFail1.getRetryCount()).isEqualTo(1);
        assertThat(afterFail1.getNextRetryAt()).isNotNull();

        // Simulate reaching MAX_RETRIES (5 retries total)
        afterFail1.setRetryCount(4);
        outboxRepository.save(afterFail1);

        outboxPublisherService.publishSingleEvent(afterFail1);

        // Verify permanent FAILED status and metric recorded
        OutboxEvent permanentlyFailed = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(permanentlyFailed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(permanentlyFailed.getRetryCount()).isEqualTo(5);
        verify(metricsService, times(1)).recordOutboxFailed();
    }

    @Test
    @DisplayName("Successful Kafka publishing results in PUBLISHED status and timestamp")
    void testSuccessfulPublishingResultsInPublished() {
        OutboxEvent event = new OutboxEvent("OrderCreated", "99", "corr-99", "{\"orderId\":99}");
        outboxRepository.save(event);

        CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("commercecore.orders.v1"), eq("99"), anyString())).thenReturn(successFuture);

        // Claim and publish
        List<OutboxEvent> claimed = outboxClaimService.claimEventsSafely("test-publisher", 10);
        assertThat(claimed).hasSize(1);

        outboxPublisherService.publishSingleEvent(claimed.get(0));

        // Verify database state
        OutboxEvent published = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();

        // Subsequent poll claims nothing
        List<OutboxEvent> nextClaim = outboxClaimService.claimEventsSafely("test-publisher", 10);
        assertThat(nextClaim).isEmpty();
    }
}

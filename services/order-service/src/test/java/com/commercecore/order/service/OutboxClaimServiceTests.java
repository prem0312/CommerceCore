package com.commercecore.order.service;

import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.model.OutboxStatus;
import com.commercecore.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxClaimServiceTests {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private OrderMetricsService metricsService;

    @InjectMocks
    private OutboxClaimService outboxClaimService;

    private OutboxEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new OutboxEvent("OrderCreated", "100", "corr-100", "{\"orderId\":100}");
        testEvent.setId(1L);
    }

    @Test
    @DisplayName("claimEventsSafely should transition claimed events to PROCESSING, compute claimCutoff, set owner/claimedAt, and preserve retryCount")
    void claimEventsSafely_Success() {
        testEvent.setRetryCount(2);
        List<OutboxEvent> mockEvents = new ArrayList<>(List.of(testEvent));
        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);

        when(outboxRepository.claimPendingEventsForUpdate(nowCaptor.capture(), cutoffCaptor.capture(), eq(10)))
                .thenReturn(mockEvents);
        when(outboxRepository.saveAll(mockEvents)).thenAnswer(inv -> inv.getArgument(0));

        Instant beforeCall = Instant.now();
        List<OutboxEvent> claimed = outboxClaimService.claimEventsSafely("instance-A", 10);
        Instant afterCall = Instant.now();

        assertThat(claimed).hasSize(1);
        OutboxEvent event = claimed.get(0);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(event.getClaimOwner()).isEqualTo("instance-A");
        assertThat(event.getClaimedAt()).isNotNull();
        // retryCount must remain unchanged during claim / recovery
        assertThat(event.getRetryCount()).isEqualTo(2);

        Instant capturedNow = nowCaptor.getValue();
        Instant capturedCutoff = cutoffCaptor.getValue();
        assertThat(capturedNow).isAfterOrEqualTo(beforeCall).isBeforeOrEqualTo(afterCall);
        // Cutoff should be 30 seconds before captured now
        assertThat(capturedCutoff).isEqualTo(capturedNow.minusSeconds(30));

        verify(outboxRepository, times(1)).claimPendingEventsForUpdate(any(Instant.class), any(Instant.class), eq(10));
        verify(outboxRepository, times(1)).saveAll(mockEvents);
    }

    @Test
    @DisplayName("markPublished should transition event to PUBLISHED and record published timestamp")
    void markPublished_Success() {
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        outboxClaimService.markPublished(1L);

        assertThat(testEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(testEvent.getPublishedAt()).isNotNull();
        verify(outboxRepository, times(1)).save(testEvent);
    }

    @Test
    @DisplayName("markRetryOrFailed should schedule exponential backoff retry when under max retries")
    void markRetryOrFailed_UnderMaxRetries() {
        testEvent.setRetryCount(1);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        outboxClaimService.markRetryOrFailed(1L, "Kafka timeout error");

        assertThat(testEvent.getRetryCount()).isEqualTo(2);
        assertThat(testEvent.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(testEvent.getNextRetryAt()).isNotNull();
        assertThat(testEvent.getNextRetryAt()).isAfter(Instant.now());
        verify(metricsService, never()).recordOutboxFailed();
        verify(outboxRepository, times(1)).save(testEvent);
    }

    @Test
    @DisplayName("markRetryOrFailed should mark event as FAILED and record failure metric when max retries reached")
    void markRetryOrFailed_MaxRetriesReached() {
        testEvent.setRetryCount(4); // Next will be 5 (MAX_RETRIES)
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        outboxClaimService.markRetryOrFailed(1L, "Permanent Kafka Error");

        assertThat(testEvent.getRetryCount()).isEqualTo(5);
        assertThat(testEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(metricsService, times(1)).recordOutboxFailed();
        verify(outboxRepository, times(1)).save(testEvent);
    }
}

package com.commercecore.order.service;

import com.commercecore.order.model.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTests {

    @Mock
    private OutboxClaimService outboxClaimService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPublisherService outboxPublisherService;

    private OutboxEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new OutboxEvent("OrderCreated", "10", "corr-1", "{\"orderId\":10}");
        testEvent.setId(1L);
    }

    @Test
    @DisplayName("Should publish outbox event to Kafka and delegate marking as PUBLISHED to OutboxClaimService")
    void publishSingleEvent_Success() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("commercecore.orders.v1"), eq("10"), eq("{\"orderId\":10}"))).thenReturn(future);

        outboxPublisherService.publishSingleEvent(testEvent);

        verify(kafkaTemplate, times(1)).send("commercecore.orders.v1", "10", "{\"orderId\":10}");
        verify(outboxClaimService, times(1)).markPublished(1L);
        verify(outboxClaimService, never()).markRetryOrFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("Should delegate retry/failure marking to OutboxClaimService on Kafka publish failure")
    void publishSingleEvent_Failure_Retries() {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka Broker Unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        outboxPublisherService.publishSingleEvent(testEvent);

        verify(outboxClaimService, times(1)).markRetryOrFailed(eq(1L), contains("Kafka Broker Unavailable"));
        verify(outboxClaimService, never()).markPublished(anyLong());
    }

    @Test
    @DisplayName("pollAndPublishEvents should claim events from OutboxClaimService and publish them when enabled")
    void pollAndPublishEvents_ClaimsAndPublishes() {
        ReflectionTestUtils.setField(outboxPublisherService, "enabled", true);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(outboxClaimService.claimEventsSafely(eq(outboxPublisherService.getInstanceId()), eq(20)))
                .thenReturn(List.of(testEvent));

        outboxPublisherService.pollAndPublishEvents();

        verify(outboxClaimService, times(1)).claimEventsSafely(eq(outboxPublisherService.getInstanceId()), eq(20));
        verify(kafkaTemplate, times(1)).send("commercecore.orders.v1", "10", "{\"orderId\":10}");
        verify(outboxClaimService, times(1)).markPublished(1L);
    }

    @Test
    @DisplayName("pollAndPublishEvents should do nothing when disabled")
    void pollAndPublishEvents_Disabled() {
        ReflectionTestUtils.setField(outboxPublisherService, "enabled", false);

        outboxPublisherService.pollAndPublishEvents();

        verify(outboxClaimService, never()).claimEventsSafely(anyString(), anyInt());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("pollAndPublishEvents should do nothing when no events claimed")
    void pollAndPublishEvents_EmptyList() {
        ReflectionTestUtils.setField(outboxPublisherService, "enabled", true);
        when(outboxClaimService.claimEventsSafely(eq(outboxPublisherService.getInstanceId()), eq(20)))
                .thenReturn(List.of());

        outboxPublisherService.pollAndPublishEvents();

        verify(outboxClaimService, times(1)).claimEventsSafely(eq(outboxPublisherService.getInstanceId()), eq(20));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}

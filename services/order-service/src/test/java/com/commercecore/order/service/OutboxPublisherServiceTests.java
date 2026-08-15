package com.commercecore.order.service;

import com.commercecore.order.model.OutboxEvent;
import com.commercecore.order.model.OutboxStatus;
import com.commercecore.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTests {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OrderMetricsService metricsService;

    @InjectMocks
    private OutboxPublisherService outboxPublisherService;

    private OutboxEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new OutboxEvent("OrderCreated", "10", "corr-1", "{\"orderId\":10}");
        testEvent.setId(1L);
    }

    @Test
    @DisplayName("Should publish outbox event to Kafka and mark as PUBLISHED")
    void publishSingleEvent_Success() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("commercecore.orders.v1"), eq("10"), eq("{\"orderId\":10}"))).thenReturn(future);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(testEvent);

        outboxPublisherService.publishSingleEvent(testEvent);

        assertThat(testEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(testEvent.getPublishedAt()).isNotNull();
        verify(outboxRepository, times(1)).save(testEvent);
    }

    @Test
    @DisplayName("Should increment retry count on Kafka publish failure")
    void publishSingleEvent_Failure_Retries() {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka Broker Unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(testEvent);

        outboxPublisherService.publishSingleEvent(testEvent);

        assertThat(testEvent.getRetryCount()).isEqualTo(1);
        assertThat(testEvent.getNextRetryAt()).isNotNull();
        verify(outboxRepository, times(1)).save(testEvent);
    }
}

package com.commercecore.payment.service;

import com.commercecore.payment.model.OutboxEvent;
import com.commercecore.payment.model.OutboxStatus;
import com.commercecore.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxPublisherServiceTests {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private PaymentMetricsService metricsService;

    @InjectMocks
    private PaymentOutboxPublisherService outboxPublisher;

    private OutboxEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new OutboxEvent("PaymentCompleted", "10", "corr-1", "{\"paymentId\":100}");
        testEvent.setId(1L);
    }

    @Test
    @DisplayName("Should publish event to commercecore.payments.v1 and mark as PUBLISHED")
    void publishSingleEvent_Success() {
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("commercecore.payments.v1"), eq("10"), anyString())).thenReturn(future);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(testEvent);

        outboxPublisher.publishSingleEvent(testEvent);

        assertThat(testEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(testEvent.getPublishedAt()).isNotNull();
    }
}

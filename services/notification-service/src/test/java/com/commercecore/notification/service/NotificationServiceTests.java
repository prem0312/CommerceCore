package com.commercecore.notification.service;

import com.commercecore.notification.model.Notification;
import com.commercecore.notification.model.NotificationChannel;
import com.commercecore.notification.model.ProcessedEvent;
import com.commercecore.notification.repository.NotificationRepository;
import com.commercecore.notification.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("Should create notification and record processed event for new event")
    void createNotification_NewEvent_Success() {
        when(processedEventRepository.existsByEventIdAndConsumerName("evt-100", "notification-service-consumer"))
                .thenReturn(false);

        notificationService.createNotificationIfNew(
                "evt-100", "corr-1", 1L, NotificationChannel.EMAIL,
                "order-template", "user@example.com", "Order Placed", "Your order has been placed"
        );

        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Consumer Idempotency: Should ignore duplicate event redelivery")
    void createNotification_DuplicateEvent_Skipped() {
        when(processedEventRepository.existsByEventIdAndConsumerName("evt-100", "notification-service-consumer"))
                .thenReturn(true);

        notificationService.createNotificationIfNew(
                "evt-100", "corr-1", 1L, NotificationChannel.EMAIL,
                "order-template", "user@example.com", "Order Placed", "Your order has been placed"
        );

        verify(notificationRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }
}

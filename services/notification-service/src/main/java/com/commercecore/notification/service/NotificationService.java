package com.commercecore.notification.service;

import com.commercecore.notification.dto.NotificationResponse;
import com.commercecore.notification.model.Notification;
import com.commercecore.notification.model.NotificationChannel;
import com.commercecore.notification.model.ProcessedEvent;
import com.commercecore.notification.repository.NotificationRepository;
import com.commercecore.notification.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    public static final String CONSUMER_NAME = "notification-service-consumer";

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               ProcessedEventRepository processedEventRepository) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void createNotificationIfNew(
            String eventId,
            String correlationId,
            Long userId,
            NotificationChannel channel,
            String template,
            String recipient,
            String subject,
            String content) {

        // 1. Consumer Idempotency Guard (Section 17)
        if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
            log.info("Notification Service: Duplicate event {} already processed, skipping", eventId);
            return;
        }

        Notification notification = new Notification(
                userId != null ? userId : 0L,
                channel,
                template,
                recipient,
                subject,
                content,
                eventId,
                correlationId
        );

        notificationRepository.save(notification);
        processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER_NAME));

        log.info("Dispatched {} notification for user {} on event {}: {}", channel, userId, eventId, subject);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserId(userId, pageable).map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAllUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(NotificationResponse::from).toList();
    }
}

package com.commercecore.notification.dto;

import com.commercecore.notification.model.Notification;
import com.commercecore.notification.model.NotificationChannel;
import com.commercecore.notification.model.NotificationStatus;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long userId,
        NotificationChannel channel,
        String template,
        String recipient,
        String subject,
        String content,
        NotificationStatus status,
        String eventId,
        String correlationId,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUserId(),
                n.getChannel(),
                n.getTemplate(),
                n.getRecipient(),
                n.getSubject(),
                n.getContent(),
                n.getStatus(),
                n.getEventId(),
                n.getCorrelationId(),
                n.getCreatedAt()
        );
    }
}

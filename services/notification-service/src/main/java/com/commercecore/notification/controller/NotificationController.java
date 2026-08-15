package com.commercecore.notification.controller;

import com.commercecore.notification.dto.NotificationResponse;
import com.commercecore.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationResponse> result = notificationService.getUserNotifications(userId, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/user/{userId}/all")
    public ResponseEntity<List<NotificationResponse>> getAllUserNotifications(@PathVariable Long userId) {
        List<NotificationResponse> result = notificationService.getAllUserNotifications(userId);
        return ResponseEntity.ok(result);
    }
}

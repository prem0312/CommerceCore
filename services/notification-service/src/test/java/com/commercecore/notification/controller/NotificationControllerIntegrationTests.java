package com.commercecore.notification.controller;

import com.commercecore.notification.model.Notification;
import com.commercecore.notification.model.NotificationChannel;
import com.commercecore.notification.repository.NotificationRepository;
import com.commercecore.notification.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAll();
        processedEventRepository.deleteAll();

        Notification n = new Notification(
                1L, NotificationChannel.EMAIL, "order-created",
                "user1@commercecore.io", "Order Confirmation",
                "Your order #10 has been placed", "evt-1", "corr-1"
        );
        notificationRepository.save(n);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/user/{userId} returns notifications")
    void testGetUserNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subject").value("Order Confirmation"))
                .andExpect(jsonPath("$.content[0].channel").value("EMAIL"));
    }
}

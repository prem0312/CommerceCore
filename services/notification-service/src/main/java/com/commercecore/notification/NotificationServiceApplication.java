package com.commercecore.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * CommerceCore Notification Service
 * Asynchronously consumes Kafka events from orders and payments topics,
 * guarantees deduplication via consumer idempotency (processed_events),
 * and dispatches simulated multi-channel notifications (EMAIL, SMS, IN_APP).
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

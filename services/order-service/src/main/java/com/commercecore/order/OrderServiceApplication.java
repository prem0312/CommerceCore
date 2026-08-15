package com.commercecore.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CommerceCore Order Service
 * Manages order creation, distributed idempotency, inventory/payment coordination,
 * state machine transitions, Kafka event consumption, and Transactional Outbox publishing.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

package com.commercecore.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CommerceCore Payment Service
 * Simulated payment gateway with distributed idempotency, atomic outbox persistence,
 * and Kafka event publishing to commercecore.payments.v1.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

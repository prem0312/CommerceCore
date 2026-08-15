package com.commercecore.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * CommerceCore Review Service
 * Manages customer reviews, ratings (1-5), duplicate prevention, and aggregate summary metrics.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ReviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}

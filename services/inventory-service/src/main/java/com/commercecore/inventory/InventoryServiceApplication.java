package com.commercecore.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * CommerceCore Inventory & Product Service
 * Source of truth for product catalog and inventory state.
 * Features optimistic locking (@Version) for race-free stock reservation and Redis Cache-Aside.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}

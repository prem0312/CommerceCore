package com.commercecore.inventory.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

/**
 * Manages Prometheus metrics for Redis Cache-Aside hit and miss tracking.
 */
@Service
public class CacheMetricsService {

    private final Counter cacheHitsCounter;
    private final Counter cacheMissesCounter;

    public CacheMetricsService(MeterRegistry meterRegistry) {
        this.cacheHitsCounter = Counter.builder("commerce_cache_hits_total")
                .description("Total number of cache hits in CommerceCore")
                .tag("service", "inventory-service")
                .register(meterRegistry);

        this.cacheMissesCounter = Counter.builder("commerce_cache_misses_total")
                .description("Total number of cache misses in CommerceCore")
                .tag("service", "inventory-service")
                .register(meterRegistry);
    }

    public void recordHit(String cacheName) {
        cacheHitsCounter.increment();
    }

    public void recordMiss(String cacheName) {
        cacheMissesCounter.increment();
    }
}

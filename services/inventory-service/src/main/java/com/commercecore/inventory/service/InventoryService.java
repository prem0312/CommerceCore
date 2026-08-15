package com.commercecore.inventory.service;

import com.commercecore.inventory.dto.*;
import com.commercecore.inventory.exception.InsufficientStockException;
import com.commercecore.inventory.exception.InventoryNotFoundException;
import com.commercecore.inventory.exception.UnauthorizedAdminException;
import com.commercecore.inventory.model.Inventory;
import com.commercecore.inventory.repository.InventoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final String INVENTORY_CACHE_PREFIX = "inventory:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final InventoryRepository inventoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheMetricsService cacheMetricsService;
    private final ObjectMapper objectMapper;

    public InventoryService(InventoryRepository inventoryRepository,
                            RedisTemplate<String, Object> redisTemplate,
                            CacheMetricsService cacheMetricsService,
                            ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.redisTemplate = redisTemplate;
        this.cacheMetricsService = cacheMetricsService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryByProductId(Long productId) {
        String cacheKey = INVENTORY_CACHE_PREFIX + productId;

        // 1. Check Redis Cache
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                cacheMetricsService.recordHit("inventory");
                log.debug("Cache HIT for inventory of product ID: {}", productId);
                if (cached instanceof InventoryResponse response) {
                    return response;
                }
                return objectMapper.convertValue(cached, InventoryResponse.class);
            }
        } catch (Exception e) {
            log.warn("Redis read failure for inventory key {}: {}", cacheKey, e.getMessage());
        }

        // 2. Cache MISS -> Query PostgreSQL
        cacheMetricsService.recordMiss("inventory");
        log.debug("Cache MISS for inventory of product ID: {}", productId);

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        InventoryResponse response = InventoryResponse.from(inventory);

        // 3. Populate Redis
        try {
            redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis write failure for inventory key {}: {}", cacheKey, e.getMessage());
        }

        return response;
    }

    @Transactional
    public InventoryResponse reserveStock(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        if (inventory.getAvailableQuantity() < quantity) {
            log.warn("Stock reservation failed for product ID {}: Available {}, Requested {}",
                    productId, inventory.getAvailableQuantity(), quantity);
            throw new InsufficientStockException(productId, inventory.getAvailableQuantity(), quantity);
        }

        inventory.reserve(quantity);
        Inventory saved = inventoryRepository.save(inventory);

        evictInventoryCache(productId);
        log.info("Reserved {} units for product ID {}. Available remaining: {}, Reserved total: {}",
                quantity, productId, saved.getAvailableQuantity(), saved.getReservedQuantity());

        return InventoryResponse.from(saved);
    }

    @Transactional
    public InventoryResponse releaseStock(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        inventory.release(quantity);
        Inventory saved = inventoryRepository.save(inventory);

        evictInventoryCache(productId);
        log.info("Released {} reserved units for product ID {}. Available: {}, Reserved remaining: {}",
                quantity, productId, saved.getAvailableQuantity(), saved.getReservedQuantity());

        return InventoryResponse.from(saved);
    }

    @Transactional
    public void batchReserve(BatchReserveRequest request) {
        // Sort by Product ID to prevent concurrent database lock ordering deadlocks
        List<StockReservationItem> sortedItems = request.items().stream()
                .sorted(Comparator.comparing(StockReservationItem::productId))
                .toList();

        for (StockReservationItem item : sortedItems) {
            reserveStock(item.productId(), item.quantity());
        }
        log.info("Successfully completed batch reservation for {} items", sortedItems.size());
    }

    @Transactional
    public void batchRelease(BatchReleaseRequest request) {
        for (StockReservationItem item : request.items()) {
            releaseStock(item.productId(), item.quantity());
        }
        log.info("Successfully completed batch release for {} items", request.items().size());
    }

    @Transactional
    public InventoryResponse adjustStock(Long productId, AdjustStockRequest request, String requesterRole) {
        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            throw new UnauthorizedAdminException("Administrative privileges required to adjust inventory stock.");
        }

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        inventory.setAvailableQuantity(request.availableQuantity());
        inventory.setReorderThreshold(request.reorderThreshold());

        Inventory saved = inventoryRepository.save(inventory);
        evictInventoryCache(productId);
        log.info("Adjusted inventory for product ID {}: available={}, threshold={}",
                productId, request.availableQuantity(), request.reorderThreshold());

        return InventoryResponse.from(saved);
    }

    private void evictInventoryCache(Long productId) {
        try {
            redisTemplate.delete(INVENTORY_CACHE_PREFIX + productId);
        } catch (Exception e) {
            log.warn("Failed to evict Redis cache for inventory of product ID {}: {}", productId, e.getMessage());
        }
    }
}

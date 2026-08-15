package com.commercecore.inventory.service;

import com.commercecore.inventory.dto.ProductRequest;
import com.commercecore.inventory.dto.ProductResponse;
import com.commercecore.inventory.exception.ProductNotFoundException;
import com.commercecore.inventory.exception.UnauthorizedAdminException;
import com.commercecore.inventory.model.Inventory;
import com.commercecore.inventory.model.Product;
import com.commercecore.inventory.repository.InventoryRepository;
import com.commercecore.inventory.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final String PRODUCT_CACHE_PREFIX = "product:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheMetricsService cacheMetricsService;
    private final ObjectMapper objectMapper;

    public ProductService(ProductRepository productRepository,
                          InventoryRepository inventoryRepository,
                          RedisTemplate<String, Object> redisTemplate,
                          CacheMetricsService cacheMetricsService,
                          ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.redisTemplate = redisTemplate;
        this.cacheMetricsService = cacheMetricsService;
        this.objectMapper = objectMapper;
    }

    private void requireAdmin(String requesterRole) {
        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            throw new UnauthorizedAdminException("Administrative privileges required for product modification.");
        }
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        String cacheKey = PRODUCT_CACHE_PREFIX + id;

        // 1. Check Redis Cache
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                cacheMetricsService.recordHit("product");
                log.debug("Cache HIT for product ID: {}", id);
                if (cached instanceof ProductResponse response) {
                    return response;
                }
                return objectMapper.convertValue(cached, ProductResponse.class);
            }
        } catch (Exception e) {
            log.warn("Redis read failure for key {}: {}", cacheKey, e.getMessage());
        }

        // 2. Cache MISS -> Query PostgreSQL
        cacheMetricsService.recordMiss("product");
        log.debug("Cache MISS for product ID: {}", id);

        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        ProductResponse response = ProductResponse.from(product);

        // 3. Populate Redis Cache
        try {
            redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis write failure for key {}: {}", cacheKey, e.getMessage());
        }

        return response;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(String category, Pageable pageable) {
        Page<Product> page;
        if (category != null && !category.isBlank()) {
            page = productRepository.findByCategoryAndActiveTrue(category.trim(), pageable);
        } else {
            page = productRepository.findByActiveTrue(pageable);
        }
        return page.map(ProductResponse::from);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request, String requesterRole) {
        requireAdmin(requesterRole);

        Product product = new Product(
                request.name().trim(),
                request.description(),
                request.price(),
                request.category().trim(),
                true
        );

        Product savedProduct = productRepository.save(product);

        // Initialize inventory for the new product
        Inventory inventory = new Inventory(
                savedProduct.getId(),
                request.initialStock(),
                0,
                request.reorderThreshold()
        );
        inventoryRepository.save(inventory);

        log.info("Created product ID: {} with initial stock: {}", savedProduct.getId(), request.initialStock());
        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request, String requesterRole) {
        requireAdmin(requesterRole);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(request.category().trim());

        Product savedProduct = productRepository.save(product);

        // Invalidate Redis cache to prevent stale reads
        evictProductCache(id);

        log.info("Updated product ID: {}", id);
        return ProductResponse.from(savedProduct);
    }

    @Transactional
    public void deleteProduct(Long id, String requesterRole) {
        requireAdmin(requesterRole);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setActive(false);
        productRepository.save(product);

        // Invalidate cache
        evictProductCache(id);
        log.info("Deactivated product ID: {}", id);
    }

    private void evictProductCache(Long productId) {
        try {
            redisTemplate.delete(PRODUCT_CACHE_PREFIX + productId);
            log.debug("Evicted cache for product ID: {}", productId);
        } catch (Exception e) {
            log.warn("Failed to evict Redis cache for product ID {}: {}", productId, e.getMessage());
        }
    }
}

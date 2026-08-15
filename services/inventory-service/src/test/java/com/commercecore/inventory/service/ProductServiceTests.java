package com.commercecore.inventory.service;

import com.commercecore.inventory.dto.ProductRequest;
import com.commercecore.inventory.dto.ProductResponse;
import com.commercecore.inventory.exception.ProductNotFoundException;
import com.commercecore.inventory.exception.UnauthorizedAdminException;
import com.commercecore.inventory.model.Product;
import com.commercecore.inventory.repository.InventoryRepository;
import com.commercecore.inventory.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTests {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private CacheMetricsService cacheMetricsService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product("Ergonomic Keyboard", "Mechanical split keyboard", new BigDecimal("149.99"), "Electronics", true);
        sampleProduct.setId(1L);
    }

    @Test
    @DisplayName("Should serve from Redis cache when Cache HIT occurs")
    void getProductById_CacheHit() {
        ProductResponse cachedResponse = ProductResponse.from(sampleProduct);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("product:1")).thenReturn(cachedResponse);

        ProductResponse response = productService.getProductById(1L);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Ergonomic Keyboard");
        verify(cacheMetricsService, times(1)).recordHit("product");
        verify(productRepository, never()).findByIdAndActiveTrue(any());
    }

    @Test
    @DisplayName("Should query database and populate Redis on Cache MISS")
    void getProductById_CacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("product:1")).thenReturn(null);
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(sampleProduct));

        ProductResponse response = productService.getProductById(1L);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Ergonomic Keyboard");
        verify(cacheMetricsService, times(1)).recordMiss("product");
        verify(productRepository, times(1)).findByIdAndActiveTrue(1L);
        verify(valueOperations, times(1)).set(eq("product:1"), any(ProductResponse.class), any(Duration.class));
    }

    @Test
    @DisplayName("Should create product and initial inventory when requester is ADMIN")
    void createProduct_ByAdmin_Success() {
        ProductRequest request = new ProductRequest("Wireless Mouse", "Ergonomic mouse", new BigDecimal("49.99"), "Electronics", 100, 10);
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductResponse response = productService.createProduct(request, "ADMIN");

        assertThat(response).isNotNull();
        verify(productRepository, times(1)).save(any(Product.class));
        verify(inventoryRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should reject product creation when requester is regular USER")
    void createProduct_ByUser_ThrowsUnauthorized() {
        ProductRequest request = new ProductRequest("Wireless Mouse", "Ergonomic mouse", new BigDecimal("49.99"), "Electronics", 100, 10);

        assertThatThrownBy(() -> productService.createProduct(request, "USER"))
                .isInstanceOf(UnauthorizedAdminException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should evict Redis cache when product is updated")
    void updateProduct_EvictsCache() {
        ProductRequest request = new ProductRequest("Updated Mouse", "New description", new BigDecimal("59.99"), "Electronics", 100, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        productService.updateProduct(1L, request, "ADMIN");

        verify(redisTemplate, times(1)).delete("product:1");
    }
}

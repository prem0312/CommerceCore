package com.commercecore.inventory.service;

import com.commercecore.inventory.dto.*;
import com.commercecore.inventory.exception.InsufficientStockException;
import com.commercecore.inventory.exception.InventoryNotFoundException;
import com.commercecore.inventory.model.Inventory;
import com.commercecore.inventory.repository.InventoryRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTests {

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
    private InventoryService inventoryService;

    private Inventory sampleInventory;

    @BeforeEach
    void setUp() {
        sampleInventory = new Inventory(101L, 10, 0, 5);
        sampleInventory.setId(1L);
        sampleInventory.setVersion(0L);
    }

    @Test
    @DisplayName("Should successfully reserve requested stock")
    void reserveStock_Success() {
        when(inventoryRepository.findByProductId(101L)).thenReturn(Optional.of(sampleInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(sampleInventory);

        InventoryResponse response = inventoryService.reserveStock(101L, 4);

        assertThat(response).isNotNull();
        assertThat(sampleInventory.getAvailableQuantity()).isEqualTo(6);
        assertThat(sampleInventory.getReservedQuantity()).isEqualTo(4);
        verify(inventoryRepository, times(1)).save(sampleInventory);
    }

    @Test
    @DisplayName("Should throw InsufficientStockException when reservation exceeds available stock")
    void reserveStock_InsufficientStock_ThrowsException() {
        when(inventoryRepository.findByProductId(101L)).thenReturn(Optional.of(sampleInventory));

        assertThatThrownBy(() -> inventoryService.reserveStock(101L, 15))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should release reserved stock back to available pool")
    void releaseStock_Success() {
        sampleInventory.setAvailableQuantity(5);
        sampleInventory.setReservedQuantity(5);

        when(inventoryRepository.findByProductId(101L)).thenReturn(Optional.of(sampleInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(sampleInventory);

        InventoryResponse response = inventoryService.releaseStock(101L, 3);

        assertThat(response).isNotNull();
        assertThat(sampleInventory.getAvailableQuantity()).isEqualTo(8);
        assertThat(sampleInventory.getReservedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should execute batch reservation for multiple products")
    void batchReserve_Success() {
        Inventory inv2 = new Inventory(102L, 20, 0, 5);
        inv2.setId(2L);
        inv2.setVersion(0L);

        when(inventoryRepository.findByProductId(101L)).thenReturn(Optional.of(sampleInventory));
        when(inventoryRepository.findByProductId(102L)).thenReturn(Optional.of(inv2));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(i -> i.getArgument(0));

        BatchReserveRequest request = new BatchReserveRequest(List.of(
                new StockReservationItem(101L, 2),
                new StockReservationItem(102L, 5)
        ));

        inventoryService.batchReserve(request);

        assertThat(sampleInventory.getAvailableQuantity()).isEqualTo(8);
        assertThat(sampleInventory.getReservedQuantity()).isEqualTo(2);
        assertThat(inv2.getAvailableQuantity()).isEqualTo(15);
        assertThat(inv2.getReservedQuantity()).isEqualTo(5);
    }
}

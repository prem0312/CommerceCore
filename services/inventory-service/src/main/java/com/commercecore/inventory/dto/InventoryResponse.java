package com.commercecore.inventory.dto;

import com.commercecore.inventory.model.Inventory;
import java.io.Serializable;
import java.time.Instant;

public record InventoryResponse(
        Long id,
        Long productId,
        int availableQuantity,
        int reservedQuantity,
        int reorderThreshold,
        Long version,
        Instant updatedAt
) implements Serializable {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity(),
                inventory.getReorderThreshold(),
                inventory.getVersion(),
                inventory.getUpdatedAt()
        );
    }
}

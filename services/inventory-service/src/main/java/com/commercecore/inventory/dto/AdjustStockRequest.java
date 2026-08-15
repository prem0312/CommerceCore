package com.commercecore.inventory.dto;

import jakarta.validation.constraints.Min;

public record AdjustStockRequest(
        @Min(value = 0, message = "Available quantity cannot be negative")
        int availableQuantity,

        @Min(value = 0, message = "Reorder threshold cannot be negative")
        int reorderThreshold
) {
}

package com.commercecore.inventory.dto;

import jakarta.validation.constraints.Min;

public record ReserveStockRequest(
        @Min(value = 1, message = "Reservation quantity must be at least 1")
        int quantity
) {
}

package com.commercecore.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Product name must not be blank")
        String name,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal price,

        @NotBlank(message = "Category must not be blank")
        String category,

        @Min(value = 0, message = "Initial stock cannot be negative")
        int initialStock,

        @Min(value = 0, message = "Reorder threshold cannot be negative")
        int reorderThreshold
) {
}

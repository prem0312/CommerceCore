package com.commercecore.inventory.dto;

import com.commercecore.inventory.model.Product;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String category,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}

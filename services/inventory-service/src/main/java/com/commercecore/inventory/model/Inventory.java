package com.commercecore.inventory.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity = 0;

    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold = 10;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Inventory() {
    }

    public Inventory(Long productId, int availableQuantity, int reservedQuantity, int reorderThreshold) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.reorderThreshold = reorderThreshold;
    }

    @PrePersist
    @PreUpdate
    protected void onPersist() {
        this.updatedAt = Instant.now();
    }

    /**
     * Atomically reserves the requested quantity.
     * Throws IllegalStateException if insufficient stock is available.
     */
    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be greater than zero");
        }
        if (this.availableQuantity < quantity) {
            throw new IllegalStateException("Insufficient stock. Available: " + this.availableQuantity + ", requested: " + quantity);
        }
        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;
    }

    /**
     * Atomically releases previously reserved stock back to available stock.
     */
    public void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be greater than zero");
        }
        int releaseAmount = Math.min(quantity, this.reservedQuantity);
        this.reservedQuantity -= releaseAmount;
        this.availableQuantity += releaseAmount;
    }

    /**
     * Confirms the sale, permanently deducting from reserved stock.
     */
    public void deductReserved(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Deduct quantity must be greater than zero");
        }
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public int getReorderThreshold() {
        return reorderThreshold;
    }

    public void setReorderThreshold(int reorderThreshold) {
        this.reorderThreshold = reorderThreshold;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

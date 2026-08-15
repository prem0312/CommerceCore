package com.commercecore.inventory.exception;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(Long productId) {
        super("Inventory record not found for Product ID: " + productId);
    }
}

package com.commercecore.inventory.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(Long productId, int available, int requested) {
        super("Insufficient stock for Product ID " + productId + ": Available " + available + ", Requested " + requested);
    }
}

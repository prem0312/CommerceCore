package com.commercecore.inventory.exception;

public class UnauthorizedAdminException extends RuntimeException {
    public UnauthorizedAdminException(String message) {
        super(message);
    }
}

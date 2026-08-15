package com.commercecore.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userId) {
        super("User profile not found for ID: " + userId);
    }
    public UserNotFoundException(String message) {
        super(message);
    }
}

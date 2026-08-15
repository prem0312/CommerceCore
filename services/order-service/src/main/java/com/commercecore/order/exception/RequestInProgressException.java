package com.commercecore.order.exception;

public class RequestInProgressException extends RuntimeException {
    public RequestInProgressException(String idempotencyKey) {
        super("A request with idempotency key '" + idempotencyKey + "' is currently being processed. Please retry shortly.");
    }
}

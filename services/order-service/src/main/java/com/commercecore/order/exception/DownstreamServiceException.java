package com.commercecore.order.exception;

public class DownstreamServiceException extends RuntimeException {
    public DownstreamServiceException(String serviceName, String reason) {
        super("Downstream call to " + serviceName + " failed: " + reason);
    }
}

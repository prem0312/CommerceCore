package com.commercecore.notification.dto;

import java.time.Instant;

public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId
) {
    public static ErrorResponse of(int status, String error, String message, String path, String traceId) {
        return new ErrorResponse(
                Instant.now().toString(),
                status,
                error,
                message,
                path,
                traceId != null ? traceId : "trace-" + System.currentTimeMillis()
        );
    }
}

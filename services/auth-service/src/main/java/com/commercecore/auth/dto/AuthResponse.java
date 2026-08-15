package com.commercecore.auth.dto;

import com.commercecore.auth.model.UserRole;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String email,
        UserRole role,
        String name
) {
    public static AuthResponse of(String token, long expiresInSeconds, Long userId, String email, UserRole role, String name) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, userId, email, role, name);
    }
}

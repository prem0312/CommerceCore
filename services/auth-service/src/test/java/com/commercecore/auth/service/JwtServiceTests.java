package com.commercecore.auth.service;

import com.commercecore.auth.config.JwtProperties;
import com.commercecore.auth.model.User;
import com.commercecore.auth.model.UserRole;
import com.commercecore.auth.model.UserStatus;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("CommerceCoreSuperSecretKeyForJWTGenerationAndValidation2026");
        properties.setExpirationMinutes(60);
        jwtService = new JwtService(properties);

        testUser = new User("Alice Smith", "alice@example.com", "hash", UserRole.ADMIN, UserStatus.ACTIVE);
        testUser.setId(42L);
    }

    @Test
    @DisplayName("Should generate a valid JWT with correct claims")
    void generateToken_ValidClaims() {
        String token = jwtService.generateToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();

        Claims claims = jwtService.extractClaims(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("userId", String.class)).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("alice@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("name", String.class)).isEqualTo("Alice Smith");
    }

    @Test
    @DisplayName("Should reject tampered tokens")
    void validateToken_TamperedToken_ReturnsFalse() {
        String token = jwtService.generateToken(testUser);
        String tampered = token.substring(0, token.length() - 5) + "abcde";

        assertThat(jwtService.validateToken(tampered)).isFalse();
    }
}

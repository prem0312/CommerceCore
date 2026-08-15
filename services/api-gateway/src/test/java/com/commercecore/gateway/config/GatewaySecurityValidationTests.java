package com.commercecore.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewaySecurityValidationTests {

    @Test
    @DisplayName("ApiGateway JwtProperties: Startup fails when JWT_SECRET is missing or too short")
    void testGatewayJwtSecretValidation() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(null);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be set");

        properties.setSecret("short");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }
}

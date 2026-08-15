package com.commercecore.auth.config;

import com.commercecore.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SecurityValidationTests {

    @Test
    @DisplayName("JwtProperties: Startup fails when JWT_SECRET is missing or blank")
    void testJwtSecretMissing_FailsStartup() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(null);

        assertThatThrownBy(jwtProperties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be set");

        jwtProperties.setSecret("   ");
        assertThatThrownBy(jwtProperties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be set");
    }

    @Test
    @DisplayName("JwtProperties: Startup fails when JWT_SECRET is too short (< 32 chars)")
    void testJwtSecretTooShort_FailsStartup() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("short-secret");

        assertThatThrownBy(jwtProperties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    @DisplayName("DataInitializer: Startup fails when COMMERCECORE_ADMIN_PASSWORD is missing or blank")
    void testAdminPasswordMissing_FailsStartup() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        DataInitializer initializer = new DataInitializer(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@commercecore.io");
        ReflectionTestUtils.setField(initializer, "adminPassword", "");

        assertThatThrownBy(initializer::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMMERCECORE_ADMIN_PASSWORD must be provided");
    }
}

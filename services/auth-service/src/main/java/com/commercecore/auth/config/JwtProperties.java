package com.commercecore.auth.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long expirationMinutes = 1440; // 24 hours

    @PostConstruct
    public void validate() {
        if (secret == null || secret.trim().isBlank()) {
            throw new IllegalStateException(
                    "Missing required configuration: JWT_SECRET must be set via environment variable or configuration."
            );
        }
        if (secret.trim().length() < 32) {
            throw new IllegalStateException(
                    "Invalid configuration: JWT_SECRET must be at least 32 characters (256 bits) for HMAC-SHA256 signing."
            );
        }
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}

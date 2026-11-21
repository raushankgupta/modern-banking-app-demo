package com.modernbank.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. {@code secret} is the HS256 signing key (at least 32 bytes); set it with the
 * {@code BANK_JWT_SECRET} environment variable. {@code expiryMinutes} is the token lifetime.
 */
@ConfigurationProperties(prefix = "bank.jwt")
public record JwtProperties(String secret, long expiryMinutes) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "bank.jwt.secret (BANK_JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes long.");
        }
        if (expiryMinutes <= 0) {
            throw new IllegalStateException("bank.jwt.expiry-minutes must be greater than zero.");
        }
    }
}

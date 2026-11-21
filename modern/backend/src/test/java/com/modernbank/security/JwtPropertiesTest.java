package com.modernbank.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void acceptsSecretOfAtLeast32Bytes() {
        JwtProperties p = new JwtProperties("0123456789abcdef0123456789abcdef", 60);

        assertThat(p.expiryMinutes()).isEqualTo(60);
    }

    @Test
    void rejectsShortOrMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", 60))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> new JwtProperties(null, 60)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonPositiveExpiry() {
        assertThatThrownBy(() -> new JwtProperties("0123456789abcdef0123456789abcdef", 0))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("expiry-minutes");
    }
}

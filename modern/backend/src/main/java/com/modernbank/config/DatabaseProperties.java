package com.modernbank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Location of the modern SQLite database file.
 * Configure with the {@code BANK_DB_PATH} environment variable or {@code bank.db.path}.
 */
@ConfigurationProperties(prefix = "bank.db")
public record DatabaseProperties(String path) {
}

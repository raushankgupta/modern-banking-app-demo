package com.modernbank.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fail-fast checks on the configured database file.
 * The legacy {@code legacybank.db} must never be opened by the modern app.
 */
public final class DatabasePathGuard {

    static final String LEGACY_DB_NAME = "legacybank.db";

    private DatabasePathGuard() {
    }

    public static Path validate(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalStateException(
                    "No database path configured. Set bank.db.path or the BANK_DB_PATH environment variable.");
        }
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        if (LEGACY_DB_NAME.equalsIgnoreCase(String.valueOf(path.getFileName()))) {
            throw new IllegalStateException(
                    "Refusing to start: " + path + " is the legacy database (" + LEGACY_DB_NAME
                            + "), which must stay read-only. Create the modern DB with modern/database/init.sh "
                            + "and point bank.db.path / BANK_DB_PATH at it.");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                    "Database file not found: " + path + ". Create it with modern/database/init.sh "
                            + "or set bank.db.path / BANK_DB_PATH.");
        }
        return path;
    }
}

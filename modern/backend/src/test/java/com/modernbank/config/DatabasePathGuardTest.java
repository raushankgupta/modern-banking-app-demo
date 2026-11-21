package com.modernbank.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabasePathGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsLegacyDatabaseByName() throws IOException {
        Path legacy = Files.createFile(tempDir.resolve("legacybank.db"));

        assertThatThrownBy(() -> DatabasePathGuard.validate(legacy.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy database")
                .hasMessageContaining("init.sh");
    }

    @Test
    void rejectsLegacyDatabaseNameCaseInsensitively() throws IOException {
        Path legacy = Files.createFile(tempDir.resolve("LegacyBank.DB"));

        assertThatThrownBy(() -> DatabasePathGuard.validate(legacy.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy database");
    }

    @Test
    void rejectsBlankPath() {
        assertThatThrownBy(() -> DatabasePathGuard.validate(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No database path configured");
    }

    @Test
    void rejectsMissingFile() {
        Path missing = tempDir.resolve("missing.db");

        assertThatThrownBy(() -> DatabasePathGuard.validate(missing.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database file not found");
    }

    @Test
    void acceptsExistingModernDatabase() throws IOException {
        Path modern = Files.createFile(tempDir.resolve("modernbank.db"));

        assertThat(DatabasePathGuard.validate(modern.toString())).isEqualTo(modern.toAbsolutePath().normalize());
    }
}

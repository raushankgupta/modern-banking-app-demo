package com.modernbank;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Builds a throwaway SQLite database from the modern schema and seed scripts.
 * Like {@code init.sh}, it replaces the admin password placeholder with a BCrypt hash of {@code admin123}.
 */
public final class TestDatabase {

    public static final String ADMIN_PASSWORD = "admin123";
    private static final String PLACEHOLDER = "__ADMIN_PASSWORD_BCRYPT__";

    private TestDatabase() {
    }

    public static Path create(Path dir) throws IOException, SQLException {
        Path dbFile = dir.resolve("modernbank.db");
        Files.createFile(dbFile);

        String seed = Files.readString(Path.of("../database/seed.sql"));
        String hash = new BCryptPasswordEncoder().encode(ADMIN_PASSWORD);
        ByteArrayResource seedResource = new ByteArrayResource(seed.replace(PLACEHOLDER, hash)
                .getBytes(StandardCharsets.UTF_8));

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            ScriptUtils.executeSqlScript(c, new FileSystemResource("../database/schema.sql"));
            ScriptUtils.executeSqlScript(c, seedResource);
        }
        return dbFile;
    }
}

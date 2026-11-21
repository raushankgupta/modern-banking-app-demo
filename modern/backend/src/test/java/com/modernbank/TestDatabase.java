package com.modernbank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Builds a throwaway SQLite database from the modern schema and seed scripts. */
public final class TestDatabase {

    private TestDatabase() {
    }

    public static Path create(Path dir) throws IOException, SQLException {
        Path dbFile = dir.resolve("modernbank.db");
        Files.createFile(dbFile);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            ScriptUtils.executeSqlScript(c, new FileSystemResource("../database/schema.sql"));
            ScriptUtils.executeSqlScript(c, new FileSystemResource("../database/seed.sql"));
        }
        return dbFile;
    }
}

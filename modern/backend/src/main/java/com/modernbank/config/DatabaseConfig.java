package com.modernbank.config;

import java.nio.file.Path;

import javax.sql.DataSource;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SQLite data source mirroring the legacy connection settings:
 * foreign keys ON, 10 s busy timeout and BEGIN IMMEDIATE write transactions.
 */
@Configuration
public class DatabaseConfig {

    static final int BUSY_TIMEOUT_MS = 10_000;

    @Bean
    public DataSource dataSource(DatabaseProperties properties) {
        Path dbFile = DatabasePathGuard.validate(properties.path());

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(BUSY_TIMEOUT_MS);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + dbFile);
        return dataSource;
    }
}

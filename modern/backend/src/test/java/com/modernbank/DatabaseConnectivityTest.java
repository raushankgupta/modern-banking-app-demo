package com.modernbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the app against a temp SQLite DB built from the modern schema and seed scripts. */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseConnectivityTest {

    private static Path dbFile;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void createDatabase() throws IOException, SQLException {
        dbFile = tempDir.resolve("modernbank.db");
        Files.createFile(dbFile);
        try (Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            ScriptUtils.executeSqlScript(c, new FileSystemResource("../database/schema.sql"));
            ScriptUtils.executeSqlScript(c, new FileSystemResource("../database/seed.sql"));
        }
    }

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("bank.db.path", () -> dbFile.toString());
    }

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoadsAndReadsSeedData() {
        assertThat(jdbc.queryForObject("select count(*) from CUSTOMERS", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from ACCOUNTS", Integer.class)).isEqualTo(15);
    }

    @Test
    void foreignKeysAreEnforcedOnConnections() throws SQLException {
        try (Connection c = dataSource.getConnection();
                var rs = c.createStatement().executeQuery("PRAGMA foreign_keys")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void foreignKeyViolationIsRejected() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into TRANSACTIONS (ACCOUNT_NUMBER, TRANSACTION_TYPE, AMOUNT, REFERENCE_NUMBER) "
                        + "values ('NOSUCHACCOUNT', 'DEPOSIT', 10, 'DEP9999999999')"))
                // Spring does not map SQLite error code 19 to DataIntegrityViolationException.
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("FOREIGN KEY constraint failed");
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

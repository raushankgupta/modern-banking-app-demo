package com.modernbank.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.modernbank.TestDatabase;
import com.modernbank.security.TokenService;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardApiTest {

    private static Path dbFile;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void createDatabase() throws IOException, SQLException {
        dbFile = TestDatabase.create(tempDir);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("bank.db.path", () -> dbFile.toString());
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TokenService tokenService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DashboardService service;

    private String bearer() {
        return "Bearer " + tokenService.issue(1L, "admin", "Branch Administrator").token();
    }

    @Test
    void summaryMatchesDirectSqlOnSeedData() {
        DashboardSummary summary = service.summary();

        BigDecimal expected = jdbc.sql("SELECT SUM(BALANCE) FROM ACCOUNTS WHERE STATUS = 'ACTIVE'")
                .query(BigDecimal.class).single();
        assertThat(summary.customerCount()).isEqualTo(10);
        assertThat(summary.accountCount()).isEqualTo(15);
        assertThat(summary.totalActiveBalance()).isEqualByComparingTo(expected);
        assertThat(summary.totalActiveBalance().scale()).isEqualTo(2);
    }

    @Test
    void recentTransactionsAreNewestFirstAndLimited() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerCount").value(10))
                .andExpect(jsonPath("$.accountCount").value(15))
                .andExpect(jsonPath("$.recentTransactions.length()").value(
                        org.hamcrest.Matchers.both(org.hamcrest.Matchers.greaterThan(4))
                                .and(org.hamcrest.Matchers.lessThanOrEqualTo(DashboardService.RECENT_LIMIT))))
                .andExpect(jsonPath("$.recentTransactions[0].accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.recentTransactions[0].customerName").isNotEmpty())
                .andExpect(jsonPath("$.recentTransactions[0].referenceNumber").isNotEmpty());

        List<RecentTransaction> recent = service.summary().recentTransactions();
        assertThat(recent).hasSizeLessThanOrEqualTo(DashboardService.RECENT_LIMIT);
        assertThat(recent).extracting(RecentTransaction::transactionDate).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());
    }
}

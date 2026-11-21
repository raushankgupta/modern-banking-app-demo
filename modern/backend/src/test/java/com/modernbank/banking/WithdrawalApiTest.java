package com.modernbank.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.modernbank.TestDatabase;
import com.modernbank.security.TokenService;

@SpringBootTest
@AutoConfigureMockMvc
class WithdrawalApiTest {

    private static final String ACTIVE = "1001000004"; // CHECKING, ACTIVE, 7525.25
    private static final String LOW = "1001000010"; // CHECKING, ACTIVE, 560.35
    private static final String FROZEN = "1001000008";
    private static final String CLOSED = "1001000013";

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
    WithdrawalService withdrawalService;

    @SpyBean
    LedgerRepository ledger;

    @AfterEach
    void resetSpy() {
        reset(ledger);
    }

    private MockHttpServletRequestBuilder withdraw(String account, String json) {
        String token = tokenService.issue(1L, "admin", "Branch Administrator").token();
        return post("/api/v1/accounts/" + account + "/withdrawals")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private BigDecimal balance(String account) {
        return jdbc.sql("SELECT BALANCE FROM ACCOUNTS WHERE ACCOUNT_NUMBER = :a").param("a", account)
                .query(BigDecimal.class).single().setScale(2);
    }

    private int txCount(String account) {
        return jdbc.sql("SELECT COUNT(*) FROM TRANSACTIONS WHERE ACCOUNT_NUMBER = :a").param("a", account)
                .query(Integer.class).single();
    }

    private long refCount() {
        return jdbc.sql("SELECT COUNT(*) FROM REFERENCE_SEQUENCE").query(Long.class).single();
    }

    @Test
    void validWithdrawalDecreasesBalanceAndRecordsOneTransaction() throws Exception {
        BigDecimal before = balance(ACTIVE);
        int txBefore = txCount(ACTIVE);

        mockMvc.perform(withdraw(ACTIVE, "{\"amount\":\"125.25\",\"description\":\"Rent\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value(ACTIVE))
                .andExpect(jsonPath("$.transactionType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.amount").value(125.25))
                .andExpect(jsonPath("$.balance").value(before.subtract(new BigDecimal("125.25")).doubleValue()))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("WDR\\d{10}")));

        assertThat(balance(ACTIVE)).isEqualByComparingTo(before.subtract(new BigDecimal("125.25")));
        assertThat(txCount(ACTIVE)).isEqualTo(txBefore + 1);

        var row = jdbc.sql("SELECT TRANSACTION_TYPE, AMOUNT, DESCRIPTION, REFERENCE_NUMBER FROM TRANSACTIONS "
                + "WHERE ACCOUNT_NUMBER = :a ORDER BY TRANSACTION_ID DESC LIMIT 1").param("a", ACTIVE)
                .query().singleRow();
        assertThat(row.get("TRANSACTION_TYPE")).isEqualTo("WITHDRAWAL");
        assertThat(new BigDecimal(row.get("AMOUNT").toString())).isEqualByComparingTo("125.25");
        assertThat(row.get("DESCRIPTION")).isEqualTo("Rent (by admin)");
        assertThat((String) row.get("REFERENCE_NUMBER")).matches("WDR\\d{10}");
    }

    @Test
    void blankDescriptionUsesLegacyDefault() throws Exception {
        mockMvc.perform(withdraw(ACTIVE, "{\"amount\":\"1.00\"}")).andExpect(status().isCreated());

        String description = jdbc.sql("SELECT DESCRIPTION FROM TRANSACTIONS WHERE ACCOUNT_NUMBER = :a "
                + "ORDER BY TRANSACTION_ID DESC LIMIT 1").param("a", ACTIVE).query(String.class).single();
        assertThat(description).isEqualTo("Cash withdrawal (by admin)");
    }

    @Test
    void amountGreaterThanBalanceReturns409AndChangesNothing() throws Exception {
        BigDecimal before = balance(LOW);
        int txBefore = txCount(LOW);
        long refsBefore = refCount();

        mockMvc.perform(withdraw(LOW, "{\"amount\":\"560.36\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message").value("Insufficient funds. Available balance is 560.35."));

        assertThat(balance(LOW)).isEqualByComparingTo(before);
        assertThat(txCount(LOW)).isEqualTo(txBefore);
        assertThat(refCount()).isEqualTo(refsBefore);
    }

    @Test
    void withdrawingExactlyTheBalanceSucceedsAndLeavesZero() throws Exception {
        String account = "1001000012"; // CHECKING, ACTIVE, 4310.00 (used only by this test)

        mockMvc.perform(withdraw(account, "{\"amount\":\"4310.00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(0.0));

        assertThat(balance(account)).isEqualByComparingTo("0.00");
    }

    @Test
    void anyAmountOnAnEmptyAccountIsRejectedAsInsufficientFunds() throws Exception {
        String account = "1001000007"; // CHECKING, ACTIVE, 1850.90 (used only by this test); drain it first
        BigDecimal current = balance(account);
        if (current.signum() > 0) {
            mockMvc.perform(withdraw(account, "{\"amount\":\"" + current.toPlainString() + "\"}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(withdraw(account, "{\"amount\":\"0.01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message").value("Insufficient funds. Available balance is 0.00."));
    }

    @Test
    void withdrawalFromFrozenOrClosedAccountReturns409() throws Exception {
        for (String account : new String[] { FROZEN, CLOSED }) {
            BigDecimal before = balance(account);
            int txBefore = txCount(account);

            mockMvc.perform(withdraw(account, "{\"amount\":\"1\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.startsWith("Account " + account + " is not active (status: ")));

            assertThat(balance(account)).isEqualByComparingTo(before);
            assertThat(txCount(account)).isEqualTo(txBefore);
        }
    }

    @Test
    void inactiveAccountIsReportedBeforeInsufficientFunds() throws Exception {
        // CLOSED account has 0.00; the legacy order is status first, then funds.
        mockMvc.perform(withdraw(CLOSED, "{\"amount\":\"50\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    void invalidAmountsReturn400AndChangeNothing() throws Exception {
        BigDecimal before = balance(ACTIVE);
        for (String body : new String[] { "{\"amount\":\"0\"}", "{\"amount\":\"-5\"}", "{\"amount\":\"abc\"}",
                "{\"amount\":\"1.234\"}", "{\"amount\":\"10000000.01\"}", "{\"amount\":\"\"}", "{}" }) {
            mockMvc.perform(withdraw(ACTIVE, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.field").value("amount"));
        }
        assertThat(balance(ACTIVE)).isEqualByComparingTo(before);
    }

    @Test
    void invalidAccountNumberReturns400() throws Exception {
        mockMvc.perform(withdraw("10-01", "{\"amount\":\"10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("accountNumber"));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        mockMvc.perform(withdraw("9999999999", "{\"amount\":\"10\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Account 9999999999 does not exist."));
    }

    @Test
    void withdrawalRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/" + ACTIVE + "/withdrawals")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"10\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void failureWhileRecordingTheTransactionRollsBackTheBalanceAndReference() {
        BigDecimal before = balance(ACTIVE);
        int txBefore = txCount(ACTIVE);
        long refsBefore = refCount();
        doThrow(new IllegalStateException("simulated insert failure")).when(ledger)
                .insertTransaction(anyString(), any(), any(), anyString(), anyString());

        assertThatThrownBy(() -> withdrawalService.withdraw(ACTIVE, "10.00", null, "admin"))
                .isInstanceOf(IllegalStateException.class).hasMessage("simulated insert failure");

        assertThat(balance(ACTIVE)).isEqualByComparingTo(before);
        assertThat(txCount(ACTIVE)).isEqualTo(txBefore);
        assertThat(refCount()).isEqualTo(refsBefore);
    }
}

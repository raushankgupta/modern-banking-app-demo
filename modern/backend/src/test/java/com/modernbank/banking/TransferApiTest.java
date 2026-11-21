package com.modernbank.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

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
class TransferApiTest {

    private static final String A = "1001000001"; // SAVINGS, ACTIVE, 15250.75
    private static final String B = "1001000003"; // SAVINGS, ACTIVE, 48900.50
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
    TransferService transferService;

    @SpyBean
    LedgerRepository ledger;

    @AfterEach
    void resetSpy() {
        reset(ledger);
    }

    private MockHttpServletRequestBuilder transfer(String json) {
        String token = tokenService.issue(1L, "admin", "Branch Administrator").token();
        return post("/api/v1/transfers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private static String body(String from, String to, String amount) {
        return "{\"fromAccount\":\"" + from + "\",\"toAccount\":\"" + to + "\",\"amount\":\"" + amount + "\"}";
    }

    private BigDecimal balance(String account) {
        return jdbc.sql("SELECT BALANCE FROM ACCOUNTS WHERE ACCOUNT_NUMBER = :a").param("a", account)
                .query(BigDecimal.class).single().setScale(2);
    }

    private int txCount() {
        return jdbc.sql("SELECT COUNT(*) FROM TRANSACTIONS").query(Integer.class).single();
    }

    private long refCount() {
        return jdbc.sql("SELECT COUNT(*) FROM REFERENCE_SEQUENCE").query(Long.class).single();
    }

    @Test
    void validTransferMovesFundsAndKeepsTheTotalUnchanged() throws Exception {
        BigDecimal fromBefore = balance(A);
        BigDecimal toBefore = balance(B);
        BigDecimal totalBefore = fromBefore.add(toBefore);
        int txBefore = txCount();

        mockMvc.perform(transfer("{\"fromAccount\":\"" + A + "\",\"toAccount\":\"" + B
                + "\",\"amount\":\"1000.50\",\"description\":\"Rent share\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromAccount").value(A))
                .andExpect(jsonPath("$.toAccount").value(B))
                .andExpect(jsonPath("$.amount").value(1000.50))
                .andExpect(jsonPath("$.fromBalance").value(fromBefore.subtract(new BigDecimal("1000.50")).doubleValue()))
                .andExpect(jsonPath("$.toBalance").value(toBefore.add(new BigDecimal("1000.50")).doubleValue()))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("TRF\\d{10}")));

        assertThat(balance(A)).isEqualByComparingTo(fromBefore.subtract(new BigDecimal("1000.50")));
        assertThat(balance(B)).isEqualByComparingTo(toBefore.add(new BigDecimal("1000.50")));
        assertThat(balance(A).add(balance(B))).isEqualByComparingTo(totalBefore);
        assertThat(txCount()).isEqualTo(txBefore + 2);
    }

    @Test
    void bothLegsShareOneReferenceAndHaveLegacyDescriptions() throws Exception {
        String response = mockMvc.perform(transfer("{\"fromAccount\":\"" + A + "\",\"toAccount\":\"" + B
                + "\",\"amount\":\"1.00\",\"description\":\"Gift\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reference = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response)
                .get("reference").asText();

        List<Map<String, Object>> rows = jdbc.sql("SELECT ACCOUNT_NUMBER, TRANSACTION_TYPE, AMOUNT, DESCRIPTION, "
                + "REFERENCE_NUMBER FROM TRANSACTIONS WHERE REFERENCE_NUMBER = :r ORDER BY TRANSACTION_ID")
                .param("r", reference).query().listOfRows();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("ACCOUNT_NUMBER")).isEqualTo(A);
        assertThat(rows.get(0).get("TRANSACTION_TYPE")).isEqualTo("TRANSFER_DEBIT");
        assertThat(rows.get(0).get("DESCRIPTION")).isEqualTo("Transfer to " + B + " - Gift (by admin)");
        assertThat(rows.get(1).get("ACCOUNT_NUMBER")).isEqualTo(B);
        assertThat(rows.get(1).get("TRANSACTION_TYPE")).isEqualTo("TRANSFER_CREDIT");
        assertThat(rows.get(1).get("DESCRIPTION")).isEqualTo("Transfer from " + A + " - Gift (by admin)");
        assertThat(new BigDecimal(rows.get(0).get("AMOUNT").toString()))
                .isEqualByComparingTo(new BigDecimal(rows.get(1).get("AMOUNT").toString()));
    }

    @Test
    void descriptionWithoutNoteHasNoDash() throws Exception {
        mockMvc.perform(transfer(body(A, B, "1"))).andExpect(status().isCreated());

        String description = jdbc.sql("SELECT DESCRIPTION FROM TRANSACTIONS WHERE TRANSACTION_TYPE = 'TRANSFER_DEBIT' "
                + "ORDER BY TRANSACTION_ID DESC LIMIT 1").query(String.class).single();
        assertThat(description).isEqualTo("Transfer to " + B + " (by admin)");
    }

    @Test
    void sameSourceAndDestinationReturns400() throws Exception {
        int txBefore = txCount();

        mockMvc.perform(transfer(body(A, A, "10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Source and destination accounts must be different."));

        assertThat(txCount()).isEqualTo(txBefore);
    }

    @Test
    void insufficientFundsReturns409AndChangesNothing() throws Exception {
        String source = "1001000010"; // 560.35, used only by this test
        BigDecimal sourceBefore = balance(source);
        BigDecimal destBefore = balance("1001000011");
        int txBefore = txCount();
        long refsBefore = refCount();

        mockMvc.perform(transfer(body(source, "1001000011", "560.36")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message")
                        .value("Insufficient funds in source account. Available balance is 560.35."));

        assertThat(balance(source)).isEqualByComparingTo(sourceBefore);
        assertThat(balance("1001000011")).isEqualByComparingTo(destBefore);
        assertThat(txCount()).isEqualTo(txBefore);
        assertThat(refCount()).isEqualTo(refsBefore);
    }

    @Test
    void transferringExactlyTheSourceBalanceSucceeds() throws Exception {
        String source = "1001000014"; // 12080.65, used only by this test

        mockMvc.perform(transfer(body(source, "1001000015", "12080.65")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromBalance").value(0.0));

        assertThat(balance(source)).isEqualByComparingTo("0.00");
    }

    @Test
    void frozenOrClosedSourceReturns409() throws Exception {
        for (String source : new String[] { FROZEN, CLOSED }) {
            mockMvc.perform(transfer(body(source, A, "1")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.startsWith("Source account " + source + " is not active (status: ")));
        }
    }

    @Test
    void frozenOrClosedDestinationReturns409AndChangesNothing() throws Exception {
        for (String destination : new String[] { FROZEN, CLOSED }) {
            BigDecimal before = balance(A);
            int txBefore = txCount();

            mockMvc.perform(transfer(body(A, destination, "1")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers
                            .startsWith("Destination account " + destination + " is not active (status: ")));

            assertThat(balance(A)).isEqualByComparingTo(before);
            assertThat(txCount()).isEqualTo(txBefore);
        }
    }

    @Test
    void sourceStatusIsCheckedBeforeDestinationStatus() throws Exception {
        mockMvc.perform(transfer(body(FROZEN, CLOSED, "1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Source account")));
    }

    @Test
    void unknownSourceOrDestinationReturns404() throws Exception {
        mockMvc.perform(transfer(body("9999999999", A, "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Source account 9999999999 does not exist."));
        mockMvc.perform(transfer(body(A, "9999999999", "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Destination account 9999999999 does not exist."));
    }

    @Test
    void invalidInputReturns400() throws Exception {
        mockMvc.perform(transfer(body("10-01", B, "1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("fromAccount"));
        mockMvc.perform(transfer(body(A, "10-01", "1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("toAccount"));
        mockMvc.perform(transfer("{\"toAccount\":\"" + B + "\",\"amount\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter the source account number."));
        for (String amount : new String[] { "0", "-5", "abc", "1.234", "10000000.01", "" }) {
            mockMvc.perform(transfer(body(A, B, amount)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("amount"));
        }
        mockMvc.perform(transfer("not json")).andExpect(status().isBadRequest());
    }

    @Test
    void transferRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content(body(A, B, "1")))
                .andExpect(status().isUnauthorized());
    }

    // ---- atomicity

    @Test
    void failureOnTheCreditLegRollsBackTheDebitAndBothTransactionRows() {
        BigDecimal fromBefore = balance(A);
        BigDecimal toBefore = balance(B);
        int txBefore = txCount();
        long refsBefore = refCount();
        // First transaction row (the debit) is written for real, the second (the credit) fails.
        doCallRealMethod().doThrow(new IllegalStateException("simulated credit-leg failure")).when(ledger)
                .insertTransaction(anyString(), any(), any(), anyString(), anyString());

        assertThatThrownBy(() -> transferService.transfer(A, B, "250.00", null, "admin"))
                .isInstanceOf(IllegalStateException.class).hasMessage("simulated credit-leg failure");

        assertThat(balance(A)).isEqualByComparingTo(fromBefore);
        assertThat(balance(B)).isEqualByComparingTo(toBefore);
        assertThat(txCount()).isEqualTo(txBefore);
        assertThat(refCount()).isEqualTo(refsBefore);
    }

    @Test
    void failureWhileUpdatingTheDestinationBalanceRollsBackTheSourceDebit() {
        BigDecimal fromBefore = balance(A);
        BigDecimal toBefore = balance(B);
        int txBefore = txCount();
        doThrow(new IllegalStateException("simulated destination update failure")).when(ledger)
                .updateBalance(eq(B), any());

        assertThatThrownBy(() -> transferService.transfer(A, B, "250.00", null, "admin"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(balance(A)).isEqualByComparingTo(fromBefore);
        assertThat(balance(B)).isEqualByComparingTo(toBefore);
        assertThat(txCount()).isEqualTo(txBefore);
    }

    @Test
    void foreignKeyViolationOnTheCreditLegRollsBackEverything() {
        // Like the legacy transferRollsBackEverythingOnFailure: the credit row points at a missing account.
        BigDecimal fromBefore = balance(A);
        int txBefore = txCount();
        doCallRealMethod().when(ledger).insertTransaction(eq(A), any(), any(), anyString(), anyString());
        doCallRealMethod().when(ledger).insertTransaction(eq("NOSUCHACCOUNT"), any(), any(), anyString(), anyString());

        assertThatThrownBy(() -> tx(() -> {
            ledger.updateBalance(A, fromBefore.subtract(BigDecimal.TEN));
            ledger.insertTransaction(A, TransactionType.TRANSFER_DEBIT, BigDecimal.TEN, "debit", "TRF9999999999");
            ledger.insertTransaction("NOSUCHACCOUNT", TransactionType.TRANSFER_CREDIT, BigDecimal.TEN, "credit",
                    "TRF9999999999");
        })).hasMessageContaining("FOREIGN KEY constraint failed");

        assertThat(balance(A)).isEqualByComparingTo(fromBefore);
        assertThat(txCount()).isEqualTo(txBefore);
    }

    @Autowired
    org.springframework.transaction.support.TransactionTemplate txTemplate;

    private void tx(Runnable work) {
        txTemplate.executeWithoutResult(s -> work.run());
    }
}

package com.modernbank.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.modernbank.TestDatabase;
import com.modernbank.security.TokenService;

/** Seed data for 1001000001: DEPOSIT 5000 (-60d), WITHDRAWAL 750 (-45d). */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionHistoryApiTest {

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

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header("Authorization",
                "Bearer " + tokenService.issue(1L, "admin", "Branch Administrator").token());
    }

    @Test
    void historyIsNewestFirstWithAllFields() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/1001000001/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // newest first: the withdrawal (-45 days) before the deposit (-60 days)
                .andExpect(jsonPath("$[0].transactionId").value(90002))
                .andExpect(jsonPath("$[0].transactionType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$[0].amount").value(750.00))
                .andExpect(jsonPath("$[0].referenceNumber").value("WDR0000000002"))
                .andExpect(jsonPath("$[0].description").value("ATM-style cash withdrawal"))
                .andExpect(jsonPath("$[0].transactionDate").isNotEmpty())
                .andExpect(jsonPath("$[1].transactionId").value(90001))
                .andExpect(jsonPath("$[1].transactionType").value("DEPOSIT"))
                .andExpect(jsonPath("$[1].amount").value(5000.00))
                .andExpect(jsonPath("$[1].referenceNumber").value("DEP0000000001"));
    }

    @Test
    void transactionDateIsIsoLocalDateTime() throws Exception {
        String json = mockMvc.perform(authed(get("/api/v1/accounts/1001000001/transactions")))
                .andReturn().getResponse().getContentAsString();

        assertThat(json).containsPattern("\"transactionDate\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\"");
    }

    @Test
    void typeFilterReturnsOnlyThatType() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/1001000001/transactions").param("type", "DEPOSIT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].transactionType").value("DEPOSIT"));

        mockMvc.perform(authed(get("/api/v1/accounts/1001000006/transactions").param("type", "TRANSFER_DEBIT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].referenceNumber").value("TRF0000000006"));

        mockMvc.perform(authed(get("/api/v1/accounts/1001000011/transactions").param("type", "TRANSFER_CREDIT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Transfer from 1001000006"));
    }

    @Test
    void filterWithNoMatchesReturnsEmptyList() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/1001000001/transactions").param("type", "TRANSFER_CREDIT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void typeAllBlankAndLowerCaseAreAccepted() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/1001000001/transactions").param("type", "ALL")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(authed(get("/api/v1/accounts/1001000001/transactions").param("type", "")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(authed(get("/api/v1/accounts/1001000001/transactions").param("type", "deposit")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void invalidTypeReturns400() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/1001000001/transactions").param("type", "REFUND")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("type"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("TRANSFER_CREDIT")));
    }

    @Test
    void accountWithoutHistoryReturnsEmptyList() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/1001000002/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/9999999999/transactions")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Account 9999999999 was not found."));
    }

    @Test
    void invalidAccountNumberReturns400() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/10-01/transactions")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("accountNumber"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1001000001/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void newTransactionsAppearFirstAndSameSecondEntriesOrderByIdDescending() throws Exception {
        String account = "1001000015";
        for (String amount : new String[] { "1.00", "2.00", "3.00" }) {
            mockMvc.perform(authed(post("/api/v1/accounts/" + account + "/deposits")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}")))
                    .andExpect(status().isCreated());
        }

        // Three deposits can share the same second; the tie-break is TRANSACTION_ID DESC (legacy ordering).
        mockMvc.perform(authed(get("/api/v1/accounts/" + account + "/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].amount").value(3.00))
                .andExpect(jsonPath("$[1].amount").value(2.00))
                .andExpect(jsonPath("$[2].amount").value(1.00));
    }
}

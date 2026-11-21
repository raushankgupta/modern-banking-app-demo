package com.modernbank.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.modernbank.TestDatabase;
import com.modernbank.security.TokenService;

/** Lookup endpoints against the seeded modern database. */
@SpringBootTest
@AutoConfigureMockMvc
class LookupApiTest {

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

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        String token = tokenService.issue(1L, "admin", "Branch Administrator").token();
        return request.header("Authorization", "Bearer " + token);
    }

    // ---- GET /api/v1/customers/{id}

    @Test
    void customerByIdReturnsCustomerAndAllAccounts() throws Exception {
        mockMvc.perform(authed(get("/api/v1/customers/1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1001))
                .andExpect(jsonPath("$.customerName").value("John Anderson"))
                .andExpect(jsonPath("$.email").value("john.anderson@example.com"))
                .andExpect(jsonPath("$.phone").value("555-0101"))
                .andExpect(jsonPath("$.address").value("12 Maple Street, Springfield, IL 62701"))
                .andExpect(jsonPath("$.accounts.length()").value(2))
                .andExpect(jsonPath("$.accounts[0].accountNumber").value("1001000001"))
                .andExpect(jsonPath("$.accounts[0].accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.accounts[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.accounts[0].balance").value(15250.75))
                .andExpect(jsonPath("$.accounts[1].accountNumber").value("1001000002"))
                .andExpect(jsonPath("$.accounts[1].accountType").value("CHECKING"))
                .andExpect(jsonPath("$.accounts[1].balance").value(3200.00));
    }

    @Test
    void customerWithFrozenAccountShowsItsStatus() throws Exception {
        mockMvc.perform(authed(get("/api/v1/customers/1005")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(2))
                .andExpect(jsonPath("$.accounts[?(@.accountNumber=='1001000008')].status").value("FROZEN"))
                .andExpect(jsonPath("$.accounts[?(@.accountNumber=='1001000008')].balance").value(9400.00));
    }

    @Test
    void customerWithClosedAccountShowsItsStatus() throws Exception {
        mockMvc.perform(authed(get("/api/v1/customers/1009")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[?(@.accountNumber=='1001000013')].status").value("CLOSED"))
                .andExpect(jsonPath("$.accounts[?(@.accountNumber=='1001000013')].balance").value(0.0));
    }

    @Test
    void unknownCustomerIdReturns404() throws Exception {
        mockMvc.perform(authed(get("/api/v1/customers/999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No customer found."));
    }

    @Test
    void nonNumericCustomerIdReturns400() throws Exception {
        mockMvc.perform(authed(get("/api/v1/customers/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Customer ID must be a number."))
                .andExpect(jsonPath("$.field").value("customerId"));
    }

    // ---- GET /api/v1/customers/by-account/{accountNumber}

    @Test
    void customerByAccountNumberReturnsOwnerAndAllTheirAccounts() throws Exception {
        mockMvc.perform(authed(get("/api/v1/customers/by-account/1001000002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1001))
                .andExpect(jsonPath("$.accounts.length()").value(2));
    }

    @Test
    void customerByUnknownAccountNumberReturns404() throws Exception {
        mockMvc.perform(authed(get("/api/v1/customers/by-account/ZZ99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void customerByInvalidAccountNumberReturns400() throws Exception {
        mockMvc.perform(authed(get("/api/v1/customers/by-account/10-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("accountNumber"));
    }

    // ---- GET /api/v1/accounts/{accountNumber}

    @Test
    void accountDetailsReturnsAccountAndOwner() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/1001000001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("1001000001"))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(15250.75))
                .andExpect(jsonPath("$.opened").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value(1001))
                .andExpect(jsonPath("$.customerName").value("John Anderson"))
                .andExpect(jsonPath("$.email").value("john.anderson@example.com"))
                .andExpect(jsonPath("$.phone").value("555-0101"));
    }

    @Test
    void frozenAccountIsReturnedWithItsStatus() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/1001000008")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.balance").value(9400.00));
    }

    @Test
    void closedAccountIsReturnedWithItsStatus() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/1001000013")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void accountNumberIsTrimmed() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/ 1001000001 ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("1001000001"));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/9999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Account 9999999999 was not found."));
    }

    @Test
    void invalidAccountNumberFormatReturns400() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/10-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("The account number may only contain letters and digits (max 20)."))
                .andExpect(jsonPath("$.field").value("accountNumber"));
    }

    @Test
    void tooLongAccountNumberReturns400() throws Exception {
        mockMvc.perform(authed(get("/api/v1/accounts/123456789012345678901")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("accountNumber"));
    }

    // ---- security

    @Test
    void lookupsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1001000001")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/customers/1001")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/customers/by-account/1001000001")).andExpect(status().isUnauthorized());
    }
}

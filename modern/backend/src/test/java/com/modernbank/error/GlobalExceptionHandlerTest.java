package com.modernbank.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.modernbank.banking.BankValidator;

/** Verifies that validation errors surface as HTTP 400 with the uniform error body. */
class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {
        @GetMapping("/probe/amount")
        String amount(@RequestParam(required = false) String value) {
            return BankValidator.parseAmount(value).toPlainString();
        }

        @GetMapping("/probe/account")
        String account(@RequestParam(required = false) String value) {
            return BankValidator.checkAccountNumber(value, "accountNumber", "account number");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidAmountReturns400WithUniformBody() throws Exception {
        mockMvc.perform(get("/probe/amount").param("value", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Amount must be greater than zero."))
                .andExpect(jsonPath("$.field").value("amount"));
    }

    @Test
    void missingAmountReturns400() throws Exception {
        mockMvc.perform(get("/probe/amount"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Please enter an amount."));
    }

    @Test
    void invalidAccountNumberReturns400WithFieldName() throws Exception {
        mockMvc.perform(get("/probe/account").param("value", "10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("accountNumber"));
    }

    @Test
    void validInputPassesThrough() throws Exception {
        mockMvc.perform(get("/probe/amount").param("value", "12.5"))
                .andExpect(status().isOk());
    }
}

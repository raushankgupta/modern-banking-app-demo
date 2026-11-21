package com.modernbank.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.modernbank.error.ValidationException;

/** Mirrors the legacy BankUtilTest cases, plus the maximum-amount boundary. */
class BankValidatorTest {

    @Test
    void parseAmountAcceptsValidValues() {
        assertThat(BankValidator.parseAmount("100")).isEqualTo(new BigDecimal("100.00"));
        assertThat(BankValidator.parseAmount(" 12.5 ")).isEqualTo(new BigDecimal("12.50"));
        assertThat(BankValidator.parseAmount("0.01")).isEqualTo(new BigDecimal("0.01"));
        assertThat(BankValidator.parseAmount("1.500")).isEqualTo(new BigDecimal("1.50"));
    }

    @Test
    void parseAmountAcceptsExactMaximum() {
        assertThat(BankValidator.parseAmount("10000000")).isEqualTo(new BigDecimal("10000000.00"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  ", "0", "0.00", "-5", "abc", "1.234", "99999999999", "10000000.01" })
    void parseAmountRejectsInvalidValues(String input) {
        assertThatThrownBy(() -> BankValidator.parseAmount(input))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getMessage()).isNotBlank();
                    assertThat(ve.getField()).isEqualTo("amount");
                });
    }

    @Test
    void parseAmountMessagesMatchLegacy() {
        assertThatThrownBy(() -> BankValidator.parseAmount(null)).hasMessage("Please enter an amount.");
        assertThatThrownBy(() -> BankValidator.parseAmount("abc")).hasMessage("Amount must be a valid number.");
        assertThatThrownBy(() -> BankValidator.parseAmount("-5")).hasMessage("Amount must be greater than zero.");
        assertThatThrownBy(() -> BankValidator.parseAmount("1.234"))
                .hasMessage("Amount cannot have more than two decimal places.");
        assertThatThrownBy(() -> BankValidator.parseAmount("10000000.01"))
                .hasMessage("Amount cannot exceed 10,000,000.00 per transaction.");
    }

    @Test
    void checkAccountNumberTrimsAndAcceptsAlphanumeric() {
        assertThat(BankValidator.checkAccountNumber(" 1001000001 ", "accountNumber", "account number"))
                .isEqualTo("1001000001");
        assertThat(BankValidator.checkAccountNumber("ABC123", "accountNumber", "account number"))
                .isEqualTo("ABC123");
        assertThat(BankValidator.checkAccountNumber("A".repeat(20), "accountNumber", "account number"))
                .hasSize(20);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  ", "10-01", "has space", "AAAAAAAAAAAAAAAAAAAAA", "acct#1" }) // 21 chars
    void checkAccountNumberRejectsInvalidValues(String input) {
        assertThatThrownBy(() -> BankValidator.checkAccountNumber(input, "accountNumber", "account number"))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getField()).isEqualTo("accountNumber"));
    }

    @Test
    void checkAccountNumberMessagesMatchLegacy() {
        assertThatThrownBy(() -> BankValidator.checkAccountNumber("", "accountNumber", "account number"))
                .hasMessage("Please enter the account number.");
        assertThatThrownBy(() -> BankValidator.checkAccountNumber("10-01", "accountNumber", "account number"))
                .hasMessage("The account number may only contain letters and digits (max 20).");
    }
}

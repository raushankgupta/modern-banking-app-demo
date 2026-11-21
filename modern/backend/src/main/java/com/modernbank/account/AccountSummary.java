package com.modernbank.account;

import java.math.BigDecimal;

/** One row of a customer's account list. */
public record AccountSummary(String accountNumber, String accountType, String status, BigDecimal balance) {
}

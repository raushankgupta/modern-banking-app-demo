package com.modernbank.banking;

import java.math.BigDecimal;

/** Status and balance of an account, read inside the banking transaction. */
public record AccountState(String status, BigDecimal balance) {
}

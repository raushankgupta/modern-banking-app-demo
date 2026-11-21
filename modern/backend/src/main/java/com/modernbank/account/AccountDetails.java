package com.modernbank.account;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Account with its owner, as shown on the legacy Account Details screen. */
public record AccountDetails(
        String accountNumber,
        String accountType,
        String status,
        BigDecimal balance,
        LocalDate opened,
        long customerId,
        String customerName,
        String email,
        String phone) {
}

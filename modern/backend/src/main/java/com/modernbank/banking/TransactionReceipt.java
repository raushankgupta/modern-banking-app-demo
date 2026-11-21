package com.modernbank.banking;

import java.math.BigDecimal;

/** Result of a deposit or withdrawal: the new balance and the reference number of the transaction. */
public record TransactionReceipt(
        String accountNumber,
        TransactionType transactionType,
        BigDecimal amount,
        BigDecimal balance,
        String reference) {
}

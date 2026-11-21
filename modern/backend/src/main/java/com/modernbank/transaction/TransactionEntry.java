package com.modernbank.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.modernbank.banking.TransactionType;

/** One row of an account's transaction history. */
public record TransactionEntry(
        long transactionId,
        TransactionType transactionType,
        BigDecimal amount,
        LocalDateTime transactionDate,
        String referenceNumber,
        String description) {
}

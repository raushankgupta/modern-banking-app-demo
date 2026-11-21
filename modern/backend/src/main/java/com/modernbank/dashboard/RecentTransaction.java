package com.modernbank.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.modernbank.banking.TransactionType;

/** One row of the dashboard's "recent transactions" table (across all accounts). */
public record RecentTransaction(
        long transactionId,
        String accountNumber,
        String customerName,
        TransactionType transactionType,
        BigDecimal amount,
        LocalDateTime transactionDate,
        String referenceNumber,
        String description) {
}

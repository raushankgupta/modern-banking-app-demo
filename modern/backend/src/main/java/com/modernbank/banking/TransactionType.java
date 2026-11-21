package com.modernbank.banking;

/** Values of TRANSACTIONS.TRANSACTION_TYPE (see the CHECK constraint in schema.sql). */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_DEBIT,
    TRANSFER_CREDIT
}

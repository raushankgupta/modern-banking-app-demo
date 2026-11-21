package com.modernbank.banking;

import java.math.BigDecimal;

/** Result of a transfer: both new balances and the reference shared by the debit and credit transactions. */
public record TransferReceipt(
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        BigDecimal fromBalance,
        BigDecimal toBalance,
        String reference) {
}

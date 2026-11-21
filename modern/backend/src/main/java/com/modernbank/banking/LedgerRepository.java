package com.modernbank.banking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Statements used by deposit, withdrawal and transfer. Same SQL as the legacy {@code BankUtil}.
 * Call these inside one transaction: the connection runs {@code BEGIN IMMEDIATE}, so the write lock is
 * held from the first statement until commit or rollback and balance checks cannot interleave with
 * another teller.
 */
@Repository
public class LedgerRepository {

    private final JdbcClient jdbc;

    public LedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AccountState> findAccount(String accountNumber) {
        return jdbc.sql("SELECT STATUS, IFNULL(BALANCE, 0) AS BALANCE FROM ACCOUNTS "
                + "WHERE ACCOUNT_NUMBER = :accountNumber")
                .param("accountNumber", accountNumber)
                .query((rs, n) -> new AccountState(rs.getString("STATUS"), money(rs.getBigDecimal("BALANCE"))))
                .optional();
    }

    public void updateBalance(String accountNumber, BigDecimal newBalance) {
        jdbc.sql("UPDATE ACCOUNTS SET BALANCE = :balance WHERE ACCOUNT_NUMBER = :accountNumber")
                .param("balance", newBalance)
                .param("accountNumber", accountNumber)
                .update();
    }

    public void insertTransaction(String accountNumber, TransactionType type, BigDecimal amount,
            String description, String reference) {
        jdbc.sql("INSERT INTO TRANSACTIONS (ACCOUNT_NUMBER, TRANSACTION_TYPE, AMOUNT, DESCRIPTION, "
                + "TRANSACTION_DATE, REFERENCE_NUMBER) "
                + "VALUES (:accountNumber, :type, :amount, :description, datetime('now', 'localtime'), :reference)")
                .param("accountNumber", accountNumber)
                .param("type", type.name())
                .param("amount", amount)
                .param("description", description)
                .param("reference", reference)
                .update();
    }

    /** SQLite keeps decimals as 64-bit floats; round back to exactly two places (legacy {@code getMoney}). */
    private static BigDecimal money(BigDecimal value) {
        return value == null ? new BigDecimal("0.00") : value.setScale(2, RoundingMode.HALF_UP);
    }
}

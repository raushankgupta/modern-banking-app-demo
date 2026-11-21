package com.modernbank.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.modernbank.banking.TransactionType;

@Repository
public class TransactionRepository {

    private final JdbcClient jdbc;

    public TransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean accountExists(String accountNumber) {
        return jdbc.sql("SELECT COUNT(*) FROM ACCOUNTS WHERE ACCOUNT_NUMBER = :accountNumber")
                .param("accountNumber", accountNumber)
                .query(Integer.class).single() > 0;
    }

    /** Newest first (date, then id), optionally restricted to one transaction type. Same ordering as legacy. */
    public List<TransactionEntry> findByAccount(String accountNumber, TransactionType type) {
        StringBuilder sql = new StringBuilder("SELECT TRANSACTION_ID, TRANSACTION_TYPE, AMOUNT, TRANSACTION_DATE, "
                + "REFERENCE_NUMBER, IFNULL(DESCRIPTION, '') AS DESCRIPTION "
                + "FROM TRANSACTIONS WHERE ACCOUNT_NUMBER = :accountNumber ");
        if (type != null) {
            sql.append("AND TRANSACTION_TYPE = :type ");
        }
        sql.append("ORDER BY TRANSACTION_DATE DESC, TRANSACTION_ID DESC");

        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("accountNumber", accountNumber);
        if (type != null) {
            spec = spec.param("type", type.name());
        }
        return spec.query((rs, n) -> new TransactionEntry(
                rs.getLong("TRANSACTION_ID"),
                TransactionType.valueOf(rs.getString("TRANSACTION_TYPE")),
                rs.getBigDecimal("AMOUNT").setScale(2, RoundingMode.HALF_UP),
                LocalDateTime.parse(rs.getString("TRANSACTION_DATE").replace(' ', 'T')),
                rs.getString("REFERENCE_NUMBER"),
                rs.getString("DESCRIPTION"))).list();
    }
}

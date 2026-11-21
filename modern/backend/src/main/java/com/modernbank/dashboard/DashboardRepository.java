package com.modernbank.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.modernbank.banking.TransactionType;

/** Read-only aggregate queries for the dashboard. */
@Repository
public class DashboardRepository {

    private final JdbcClient jdbc;

    public DashboardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int countCustomers() {
        return jdbc.sql("SELECT COUNT(*) FROM CUSTOMERS").query(Integer.class).single();
    }

    public int countAccounts() {
        return jdbc.sql("SELECT COUNT(*) FROM ACCOUNTS").query(Integer.class).single();
    }

    public BigDecimal totalActiveBalance() {
        return jdbc.sql("SELECT IFNULL(SUM(BALANCE), 0) FROM ACCOUNTS WHERE STATUS = 'ACTIVE'")
                .query(BigDecimal.class).single().setScale(2, RoundingMode.HALF_UP);
    }

    /** Newest first (date, then id), same ordering as the per-account history. */
    public List<RecentTransaction> recentTransactions(int limit) {
        return jdbc.sql("SELECT T.TRANSACTION_ID, T.ACCOUNT_NUMBER, C.CUSTOMER_NAME, T.TRANSACTION_TYPE, T.AMOUNT, "
                + "T.TRANSACTION_DATE, T.REFERENCE_NUMBER, IFNULL(T.DESCRIPTION, '') AS DESCRIPTION "
                + "FROM TRANSACTIONS T "
                + "JOIN ACCOUNTS A ON A.ACCOUNT_NUMBER = T.ACCOUNT_NUMBER "
                + "JOIN CUSTOMERS C ON C.CUSTOMER_ID = A.CUSTOMER_ID "
                + "ORDER BY T.TRANSACTION_DATE DESC, T.TRANSACTION_ID DESC LIMIT :limit")
                .param("limit", limit)
                .query((rs, n) -> new RecentTransaction(
                        rs.getLong("TRANSACTION_ID"),
                        rs.getString("ACCOUNT_NUMBER"),
                        rs.getString("CUSTOMER_NAME"),
                        TransactionType.valueOf(rs.getString("TRANSACTION_TYPE")),
                        rs.getBigDecimal("AMOUNT").setScale(2, RoundingMode.HALF_UP),
                        LocalDateTime.parse(rs.getString("TRANSACTION_DATE").replace(' ', 'T')),
                        rs.getString("REFERENCE_NUMBER"),
                        rs.getString("DESCRIPTION")))
                .list();
    }
}

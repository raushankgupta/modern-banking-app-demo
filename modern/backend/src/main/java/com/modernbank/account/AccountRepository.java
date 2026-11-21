package com.modernbank.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read access to ACCOUNTS. Same SQL as the legacy screens, with money read as scale-2 BigDecimal. */
@Repository
public class AccountRepository {

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AccountDetails> findDetails(String accountNumber) {
        return jdbc.sql("SELECT A.ACCOUNT_NUMBER, A.ACCOUNT_TYPE, A.STATUS, IFNULL(A.BALANCE, 0) AS BALANCE, "
                + "substr(A.CREATED_DATE, 1, 10) AS OPENED, "
                + "C.CUSTOMER_ID, C.CUSTOMER_NAME, C.EMAIL, C.PHONE "
                + "FROM ACCOUNTS A JOIN CUSTOMERS C ON A.CUSTOMER_ID = C.CUSTOMER_ID "
                + "WHERE A.ACCOUNT_NUMBER = :accountNumber")
                .param("accountNumber", accountNumber)
                .query((rs, n) -> new AccountDetails(
                        rs.getString("ACCOUNT_NUMBER"),
                        rs.getString("ACCOUNT_TYPE"),
                        rs.getString("STATUS"),
                        money(rs.getBigDecimal("BALANCE")),
                        LocalDate.parse(rs.getString("OPENED")),
                        rs.getLong("CUSTOMER_ID"),
                        rs.getString("CUSTOMER_NAME"),
                        rs.getString("EMAIL"),
                        rs.getString("PHONE")))
                .optional();
    }

    public List<AccountSummary> findByCustomerId(long customerId) {
        return jdbc.sql("SELECT ACCOUNT_NUMBER, ACCOUNT_TYPE, STATUS, IFNULL(BALANCE, 0) AS BALANCE "
                + "FROM ACCOUNTS WHERE CUSTOMER_ID = :customerId ORDER BY ACCOUNT_NUMBER")
                .param("customerId", customerId)
                .query((rs, n) -> new AccountSummary(
                        rs.getString("ACCOUNT_NUMBER"),
                        rs.getString("ACCOUNT_TYPE"),
                        rs.getString("STATUS"),
                        money(rs.getBigDecimal("BALANCE"))))
                .list();
    }

    /**
     * SQLite stores NUMERIC values with decimals as 64-bit floating point, so the value is rounded back to
     * exactly two decimal places (same as the legacy {@code BankUtil.getMoney}).
     */
    static BigDecimal money(BigDecimal value) {
        return value == null ? new BigDecimal("0.00") : value.setScale(2, RoundingMode.HALF_UP);
    }
}

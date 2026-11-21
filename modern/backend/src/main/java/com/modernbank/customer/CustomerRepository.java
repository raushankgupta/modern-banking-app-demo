package com.modernbank.customer;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

    /** Customer row without accounts. */
    public record CustomerRow(long customerId, String customerName, String email, String phone, String address) {
    }

    private final JdbcClient jdbc;

    public CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CustomerRow> findById(long customerId) {
        return jdbc.sql("SELECT CUSTOMER_ID, CUSTOMER_NAME, EMAIL, PHONE, ADDRESS "
                + "FROM CUSTOMERS WHERE CUSTOMER_ID = :customerId")
                .param("customerId", customerId)
                .query(CustomerRepository::map)
                .optional();
    }

    public Optional<CustomerRow> findByAccountNumber(String accountNumber) {
        return jdbc.sql("SELECT C.CUSTOMER_ID, C.CUSTOMER_NAME, C.EMAIL, C.PHONE, C.ADDRESS "
                + "FROM CUSTOMERS C JOIN ACCOUNTS A ON C.CUSTOMER_ID = A.CUSTOMER_ID "
                + "WHERE A.ACCOUNT_NUMBER = :accountNumber")
                .param("accountNumber", accountNumber)
                .query(CustomerRepository::map)
                .optional();
    }

    private static CustomerRow map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CustomerRow(rs.getLong("CUSTOMER_ID"), rs.getString("CUSTOMER_NAME"), rs.getString("EMAIL"),
                rs.getString("PHONE"), rs.getString("ADDRESS"));
    }
}

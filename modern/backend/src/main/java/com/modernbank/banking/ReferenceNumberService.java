package com.modernbank.banking;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates reference numbers such as {@code WDR0000000007}.
 * SQLite has no sequences, so a row is inserted into REFERENCE_SEQUENCE and its generated key is used,
 * exactly like the legacy app. {@code last_insert_rowid()} is per connection, so this must run inside
 * the caller's transaction (which keeps one connection and makes the number atomic with the banking
 * operation).
 */
@Service
public class ReferenceNumberService {

    private final JdbcClient jdbc;

    public ReferenceNumberService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(ReferenceType type) {
        jdbc.sql("INSERT INTO REFERENCE_SEQUENCE DEFAULT VALUES").update();
        long id = jdbc.sql("SELECT last_insert_rowid()").query(Long.class).single();
        return String.format("%s%010d", type.prefix(), id);
    }
}

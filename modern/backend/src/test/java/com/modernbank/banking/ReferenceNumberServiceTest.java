package com.modernbank.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.modernbank.TestDatabase;

@SpringBootTest
class ReferenceNumberServiceTest {

    private static Path dbFile;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void createDatabase() throws IOException, SQLException {
        dbFile = TestDatabase.create(tempDir);
    }

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("bank.db.path", () -> dbFile.toString());
    }

    @Autowired
    ReferenceNumberService service;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Test
    void referencesAreUniqueIncreasingAndInLegacyFormat() {
        List<String> refs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            refs.add(tx.execute(status -> service.next(ReferenceType.DEPOSIT)));
        }

        assertThat(refs).doesNotHaveDuplicates().isSorted();
        assertThat(refs).allMatch(r -> r.matches("DEP\\d{10}"));
    }

    @Test
    void referencesContinueAfterSeededSequence() {
        // The seed leaves REFERENCE_SEQUENCE at 6, so the next generated number is greater than 6.
        String ref = tx.execute(status -> service.next(ReferenceType.WITHDRAWAL));

        assertThat(ref).startsWith("WDR");
        assertThat(Long.parseLong(ref.substring(3))).isGreaterThan(6L);
    }

    @Test
    void prefixesMatchLegacy() {
        String dep = tx.execute(s -> service.next(ReferenceType.DEPOSIT));
        String wdr = tx.execute(s -> service.next(ReferenceType.WITHDRAWAL));
        String trf = tx.execute(s -> service.next(ReferenceType.TRANSFER));

        assertThat(dep).startsWith("DEP");
        assertThat(wdr).startsWith("WDR");
        assertThat(trf).startsWith("TRF");
    }

    @Test
    void transferLegsCanShareOneReference() {
        List<String> used = tx.execute(status -> {
            String ref = service.next(ReferenceType.TRANSFER);
            return List.of(ref, ref);
        });

        assertThat(used.get(0)).isEqualTo(used.get(1));
    }

    @Test
    void rolledBackTransactionDoesNotConsumeTheReference() {
        Long before = jdbc.sql("SELECT MAX(REF_ID) FROM REFERENCE_SEQUENCE").query(Long.class).single();

        tx.execute(status -> {
            service.next(ReferenceType.DEPOSIT);
            status.setRollbackOnly();
            return null;
        });

        Long after = jdbc.sql("SELECT MAX(REF_ID) FROM REFERENCE_SEQUENCE").query(Long.class).single();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void requiresAnExistingTransaction() {
        assertThatThrownBy(() -> service.next(ReferenceType.DEPOSIT))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}

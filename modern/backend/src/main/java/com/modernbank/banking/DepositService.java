package com.modernbank.banking;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.modernbank.error.NotFoundException;

@Service
public class DepositService {

    private static final String DEFAULT_DESCRIPTION = "Cash deposit";

    private final LedgerRepository ledger;
    private final ReferenceNumberService references;
    private final TransactionTemplate tx;

    public DepositService(LedgerRepository ledger, ReferenceNumberService references, TransactionTemplate tx) {
        this.ledger = ledger;
        this.references = references;
        this.tx = tx;
    }

    /**
     * Validates the input first (no database access), then does balance update, reference number and
     * transaction row in one database transaction.
     */
    public TransactionReceipt deposit(String accountNumberInput, String amountInput, String descriptionInput,
            String username) {
        String accountNumber = BankValidator.checkAccountNumber(accountNumberInput, "accountNumber", "account number");
        BigDecimal amount = BankValidator.parseAmount(amountInput);
        String description = AccountRules.describe(descriptionInput, DEFAULT_DESCRIPTION, username);

        return tx.execute(status -> {
            AccountState account = ledger.findAccount(accountNumber)
                    .orElseThrow(() -> new NotFoundException("Account " + accountNumber + " does not exist."));
            AccountRules.requireActive(accountNumber, account);

            BigDecimal newBalance = account.balance().add(amount);
            ledger.updateBalance(accountNumber, newBalance);
            String reference = references.next(ReferenceType.DEPOSIT);
            ledger.insertTransaction(accountNumber, TransactionType.DEPOSIT, amount, description, reference);
            return new TransactionReceipt(accountNumber, TransactionType.DEPOSIT, amount, newBalance, reference);
        });
    }
}

package com.modernbank.banking;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.modernbank.error.NotFoundException;

@Service
public class WithdrawalService {

    private static final String DEFAULT_DESCRIPTION = "Cash withdrawal";

    private final LedgerRepository ledger;
    private final ReferenceNumberService references;
    private final TransactionTemplate tx;

    public WithdrawalService(LedgerRepository ledger, ReferenceNumberService references, TransactionTemplate tx) {
        this.ledger = ledger;
        this.references = references;
        this.tx = tx;
    }

    /**
     * Validates the input first (no database access), then checks status and funds, updates the balance,
     * and records the transaction in one database transaction. Overdraft is never allowed.
     */
    public TransactionReceipt withdraw(String accountNumberInput, String amountInput, String descriptionInput,
            String username) {
        String accountNumber = BankValidator.checkAccountNumber(accountNumberInput, "accountNumber", "account number");
        BigDecimal amount = BankValidator.parseAmount(amountInput);
        String description = AccountRules.describe(descriptionInput, DEFAULT_DESCRIPTION, username);

        return tx.execute(status -> {
            AccountState account = ledger.findAccount(accountNumber)
                    .orElseThrow(() -> new NotFoundException("Account " + accountNumber + " does not exist."));
            AccountRules.requireActive(accountNumber, account);
            AccountRules.requireSufficientFunds(account, amount);

            BigDecimal newBalance = account.balance().subtract(amount);
            ledger.updateBalance(accountNumber, newBalance);
            String reference = references.next(ReferenceType.WITHDRAWAL);
            ledger.insertTransaction(accountNumber, TransactionType.WITHDRAWAL, amount, description, reference);
            return new TransactionReceipt(accountNumber, TransactionType.WITHDRAWAL, amount, newBalance, reference);
        });
    }
}

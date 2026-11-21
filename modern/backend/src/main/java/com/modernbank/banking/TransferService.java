package com.modernbank.banking;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.modernbank.error.BusinessRuleException;
import com.modernbank.error.NotFoundException;
import com.modernbank.error.ValidationException;

@Service
public class TransferService {

    private final LedgerRepository ledger;
    private final ReferenceNumberService references;
    private final TransactionTemplate tx;

    public TransferService(LedgerRepository ledger, ReferenceNumberService references, TransactionTemplate tx) {
        this.ledger = ledger;
        this.references = references;
        this.tx = tx;
    }

    /**
     * Moves money between two accounts. Debit, credit and both transaction rows share one reference number and
     * are written in a single database transaction, so a failure at any step leaves nothing behind.
     */
    public TransferReceipt transfer(String fromInput, String toInput, String amountInput, String descriptionInput,
            String username) {
        // Same order as the legacy TransferFrame: source, destination, same-account check, amount.
        String from = BankValidator.checkAccountNumber(fromInput, "fromAccount", "source account number");
        String to = BankValidator.checkAccountNumber(toInput, "toAccount", "destination account number");
        if (from.equals(to)) {
            throw new ValidationException("Source and destination accounts must be different.", "toAccount");
        }
        BigDecimal amount = BankValidator.parseAmount(amountInput);

        String note = descriptionInput == null ? "" : descriptionInput.trim();
        String suffix = (note.isEmpty() ? "" : " - " + note) + " (by " + username + ")";

        return tx.execute(status -> {
            AccountState source = ledger.findAccount(from)
                    .orElseThrow(() -> new NotFoundException("Source account " + from + " does not exist."));
            AccountState destination = ledger.findAccount(to)
                    .orElseThrow(() -> new NotFoundException("Destination account " + to + " does not exist."));

            AccountRules.requireActive("Source account", from, source);
            AccountRules.requireActive("Destination account", to, destination);
            if (source.balance().compareTo(amount) < 0) {
                throw new BusinessRuleException(BusinessRuleException.INSUFFICIENT_FUNDS,
                        "Insufficient funds in source account. Available balance is "
                                + AccountRules.formatMoney(source.balance()) + ".");
            }

            BigDecimal newFrom = source.balance().subtract(amount);
            BigDecimal newTo = destination.balance().add(amount);
            String reference = references.next(ReferenceType.TRANSFER);

            ledger.updateBalance(from, newFrom);
            ledger.updateBalance(to, newTo);
            ledger.insertTransaction(from, TransactionType.TRANSFER_DEBIT, amount, "Transfer to " + to + suffix,
                    reference);
            ledger.insertTransaction(to, TransactionType.TRANSFER_CREDIT, amount, "Transfer from " + from + suffix,
                    reference);
            return new TransferReceipt(from, to, amount, newFrom, newTo, reference);
        });
    }
}

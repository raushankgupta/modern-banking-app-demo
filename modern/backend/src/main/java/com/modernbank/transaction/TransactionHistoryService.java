package com.modernbank.transaction;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.modernbank.banking.BankValidator;
import com.modernbank.banking.TransactionType;
import com.modernbank.error.NotFoundException;
import com.modernbank.error.ValidationException;

@Service
public class TransactionHistoryService {

    private final TransactionRepository transactions;

    public TransactionHistoryService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    /**
     * @param typeInput optional filter; absent, blank or {@code ALL} means every type
     */
    public List<TransactionEntry> history(String accountNumberInput, String typeInput) {
        String accountNumber = BankValidator.checkAccountNumber(accountNumberInput, "accountNumber", "account number");
        TransactionType type = parseType(typeInput);
        if (!transactions.accountExists(accountNumber)) {
            throw new NotFoundException("Account " + accountNumber + " was not found.");
        }
        return transactions.findByAccount(accountNumber, type);
    }

    private static TransactionType parseType(String input) {
        if (input == null || input.isBlank() || "ALL".equalsIgnoreCase(input.trim())) {
            return null;
        }
        try {
            return TransactionType.valueOf(input.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Transaction type must be one of: "
                    + String.join(", ", Arrays.stream(TransactionType.values()).map(Enum::name).toList())
                    + ", or ALL.", "type");
        }
    }
}

package com.modernbank.account;

import org.springframework.stereotype.Service;

import com.modernbank.banking.BankValidator;
import com.modernbank.error.NotFoundException;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public AccountDetails getDetails(String accountNumberInput) {
        String accountNumber = BankValidator.checkAccountNumber(accountNumberInput, "accountNumber", "account number");
        return accounts.findDetails(accountNumber)
                .orElseThrow(() -> new NotFoundException("Account " + accountNumber + " was not found."));
    }
}

package com.modernbank.customer;

import org.springframework.stereotype.Service;

import com.modernbank.account.AccountRepository;
import com.modernbank.banking.BankValidator;
import com.modernbank.error.NotFoundException;
import com.modernbank.error.ValidationException;

@Service
public class CustomerService {

    private static final String NOT_FOUND = "No customer found.";

    private final CustomerRepository customers;
    private final AccountRepository accounts;

    public CustomerService(CustomerRepository customers, AccountRepository accounts) {
        this.customers = customers;
        this.accounts = accounts;
    }

    public CustomerWithAccounts findById(String customerIdInput) {
        long customerId = parseCustomerId(customerIdInput);
        return withAccounts(customers.findById(customerId));
    }

    /** Legacy "search by account number": the customer owning that account, plus all their accounts. */
    public CustomerWithAccounts findByAccountNumber(String accountNumberInput) {
        String accountNumber = BankValidator.checkAccountNumber(accountNumberInput, "accountNumber", "account number");
        return withAccounts(customers.findByAccountNumber(accountNumber));
    }

    private CustomerWithAccounts withAccounts(java.util.Optional<CustomerRepository.CustomerRow> row) {
        CustomerRepository.CustomerRow c = row.orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return new CustomerWithAccounts(c.customerId(), c.customerName(), c.email(), c.phone(), c.address(),
                accounts.findByCustomerId(c.customerId()));
    }

    private static long parseCustomerId(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Please enter a search value.", "customerId");
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Customer ID must be a number.", "customerId");
        }
    }
}

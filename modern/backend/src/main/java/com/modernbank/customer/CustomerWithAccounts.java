package com.modernbank.customer;

import java.util.List;

import com.modernbank.account.AccountSummary;

/** Customer with all their accounts, as shown on the legacy Customer Search screen. */
public record CustomerWithAccounts(
        long customerId,
        String customerName,
        String email,
        String phone,
        String address,
        List<AccountSummary> accounts) {
}

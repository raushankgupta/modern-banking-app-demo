package com.modernbank.banking;

import com.modernbank.error.BusinessRuleException;

/** Rules shared by deposit, withdrawal and transfer; messages are the legacy ones. */
final class AccountRules {

    private AccountRules() {
    }

    static void requireActive(String accountNumber, AccountState account) {
        if (!"ACTIVE".equals(account.status())) {
            throw new BusinessRuleException(BusinessRuleException.ACCOUNT_NOT_ACTIVE,
                    "Account " + accountNumber + " is not active (status: " + account.status() + ").");
        }
    }

    /** Uses the default text when blank and always appends who performed the operation, like legacy. */
    static String describe(String input, String defaultText, String username) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            text = defaultText;
        }
        return text + " (by " + username + ")";
    }
}

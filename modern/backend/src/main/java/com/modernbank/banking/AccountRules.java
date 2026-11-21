package com.modernbank.banking;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import com.modernbank.error.BusinessRuleException;

/** Rules shared by deposit, withdrawal and transfer; messages are the legacy ones. */
final class AccountRules {

    private AccountRules() {
    }

    static void requireActive(String accountNumber, AccountState account) {
        requireActive("Account", accountNumber, account);
    }

    /** @param label how the account is named in the message, for example "Source account" (transfer). */
    static void requireActive(String label, String accountNumber, AccountState account) {
        if (!"ACTIVE".equals(account.status())) {
            throw new BusinessRuleException(BusinessRuleException.ACCOUNT_NOT_ACTIVE,
                    label + " " + accountNumber + " is not active (status: " + account.status() + ").");
        }
    }

    /** No overdraft: the balance must be at least the amount. Withdrawing exactly the balance is allowed. */
    static void requireSufficientFunds(AccountState account, BigDecimal amount) {
        if (account.balance().compareTo(amount) < 0) {
            throw new BusinessRuleException(BusinessRuleException.INSUFFICIENT_FUNDS,
                    "Insufficient funds. Available balance is " + formatMoney(account.balance()) + ".");
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

    static String formatMoney(BigDecimal value) {
        return new DecimalFormat("#,##0.00").format(value);
    }
}

package com.modernbank.banking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.regex.Pattern;

import com.modernbank.error.ValidationException;

/** Input validation ported from the legacy {@code BankUtil}; rules and messages are unchanged. */
public final class BankValidator {

    /** Largest single deposit / withdrawal / transfer accepted. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000.00");

    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("[A-Za-z0-9]{1,20}");

    private BankValidator() {
    }

    /** Parses and validates a monetary amount; the result always has scale 2. */
    public static BigDecimal parseAmount(String text) {
        final String field = "amount";
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Please enter an amount.", field);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Amount must be a valid number.", field);
        }
        if (amount.signum() <= 0) {
            throw new ValidationException("Amount must be greater than zero.", field);
        }
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            throw new ValidationException("Amount cannot have more than two decimal places.", field);
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException(
                    "Amount cannot exceed " + formatMoney(MAX_AMOUNT) + " per transaction.", field);
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * Validates and trims an account number (1 to 20 letters or digits).
     *
     * @param field name of the request field, used in the error body (for example {@code accountNumber})
     * @param label human readable name used in messages (for example {@code account number})
     */
    public static String checkAccountNumber(String text, String field, String label) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Please enter the " + label + ".", field);
        }
        String value = text.trim();
        if (!ACCOUNT_NUMBER.matcher(value).matches()) {
            throw new ValidationException(
                    "The " + label + " may only contain letters and digits (max 20).", field);
        }
        return value;
    }

    private static String formatMoney(BigDecimal value) {
        return new DecimalFormat("#,##0.00").format(value);
    }
}

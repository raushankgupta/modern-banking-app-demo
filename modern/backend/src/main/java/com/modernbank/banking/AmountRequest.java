package com.modernbank.banking;

/**
 * Body of a deposit or withdrawal. {@code amount} is read as text (a JSON string or number) so that it can be
 * validated exactly like the legacy text field; {@code description} is optional.
 */
public record AmountRequest(String amount, String description) {
}

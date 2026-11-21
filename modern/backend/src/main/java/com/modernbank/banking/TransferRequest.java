package com.modernbank.banking;

/**
 * Body of a transfer. {@code amount} is read as text (a JSON string or number) so it is validated exactly like
 * the legacy text field; {@code description} is optional.
 */
public record TransferRequest(String fromAccount, String toAccount, String amount, String description) {
}

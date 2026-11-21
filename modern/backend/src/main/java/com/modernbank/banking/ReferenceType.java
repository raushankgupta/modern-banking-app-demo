package com.modernbank.banking;

/** Transaction families that get their own reference number prefix (legacy format). */
public enum ReferenceType {

    DEPOSIT("DEP"),
    WITHDRAWAL("WDR"),
    TRANSFER("TRF");

    private final String prefix;

    ReferenceType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}

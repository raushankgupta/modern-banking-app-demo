package com.modernbank.error;

import org.springframework.http.HttpStatus;

/** Requested customer or account does not exist (HTTP 404). */
public class NotFoundException extends BankingException {

    public static final String CODE = "NOT_FOUND";

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, CODE, message, null);
    }
}

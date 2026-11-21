package com.modernbank.error;

import org.springframework.http.HttpStatus;

/** Invalid user input (HTTP 400). */
public class ValidationException extends BankingException {

    public static final String CODE = "VALIDATION_ERROR";

    public ValidationException(String message, String field) {
        super(HttpStatus.BAD_REQUEST, CODE, message, field);
    }
}

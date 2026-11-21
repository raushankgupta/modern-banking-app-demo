package com.modernbank.error;

import org.springframework.http.HttpStatus;

/** Failed login (HTTP 401). The message is deliberately generic so it does not reveal which part was wrong. */
public class AuthenticationFailedException extends BankingException {

    public static final String CODE = "AUTHENTICATION_FAILED";

    public AuthenticationFailedException() {
        super(HttpStatus.UNAUTHORIZED, CODE, "Invalid username or password.", null);
    }
}

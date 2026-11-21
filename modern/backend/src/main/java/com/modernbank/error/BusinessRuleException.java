package com.modernbank.error;

import org.springframework.http.HttpStatus;

/** A banking rule rejects the operation, for example an inactive account or insufficient funds (HTTP 409). */
public class BusinessRuleException extends BankingException {

    public static final String ACCOUNT_NOT_ACTIVE = "ACCOUNT_NOT_ACTIVE";
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message, null);
    }
}

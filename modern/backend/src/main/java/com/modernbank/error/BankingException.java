package com.modernbank.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for expected, business-level failures. Each subclass carries the HTTP status
 * and error code that {@link GlobalExceptionHandler} turns into the uniform {@link ApiError} body.
 */
public abstract class BankingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;

    protected BankingException(HttpStatus status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** Name of the request field the error relates to, or {@code null}. */
    public String getField() {
        return field;
    }
}

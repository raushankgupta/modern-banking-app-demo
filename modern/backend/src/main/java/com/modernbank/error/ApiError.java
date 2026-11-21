package com.modernbank.error;

/** Uniform JSON error body returned by every failing API call. */
public record ApiError(String code, String message, String field) {
}

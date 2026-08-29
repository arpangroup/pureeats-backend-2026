package com.pureeats.domain.common.exception;

public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) {
        super(429, "RATE_LIMIT_EXCEEDED", message);
    }

    public TooManyRequestsException(String errorCode, String message) {
        super(429, errorCode, message);
    }
}

package com.pureeats.domain.common.exception;

public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(401, "UNAUTHORIZED", message);
    }

    public UnauthorizedException(String errorCode, String message) {
        super(401, errorCode, message);
    }
}

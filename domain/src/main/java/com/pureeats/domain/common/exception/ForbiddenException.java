package com.pureeats.domain.common.exception;

public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(403, "FORBIDDEN", message);
    }
}

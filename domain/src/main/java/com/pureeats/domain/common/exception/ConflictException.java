package com.pureeats.domain.common.exception;

public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(409, "CONFLICT", message);
    }
}

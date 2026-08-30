package com.pureeats.domain.common.exception;

import lombok.Getter;

/**
 * A wrong-but-not-yet-locked-out OTP guess. Carries {@code attemptsRemaining} so
 * {@code GlobalExceptionHandler} can surface it under the response's {@code data} field
 * without any other module needing to know about this one edge case.
 */
@Getter
public class InvalidOtpException extends ApiException {

    private final Integer attemptsRemaining;

    public InvalidOtpException(String message, Integer attemptsRemaining) {
        super(400, "INVALID_OTP", message);
        this.attemptsRemaining = attemptsRemaining;
    }
}

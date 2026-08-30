package com.pureeats.domain.common.exception;

import lombok.Getter;

/**
 * Base for all business/API exceptions across every module. Deliberately framework-free
 * (no Spring HttpStatus type here) so the domain module keeps zero web/framework dependency;
 * pureeats-app's GlobalExceptionHandler maps {@link #getHttpStatus()} to a real HTTP response.
 */
@Getter
public class ApiException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;

    public ApiException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}

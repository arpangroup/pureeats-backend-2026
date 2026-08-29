package com.pureeats.domain.common.response;

import java.time.Instant;

/**
 * Uniform response envelope returned by every controller in every module.
 */
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final Instant timestamp;
    private final String errorCode;
    private final String requestId;

    private ApiResponse(boolean success, String message, T data, String errorCode, String requestId) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now();
        this.errorCode = errorCode;
        this.requestId = requestId;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", data, null, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, null);
    }

    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>(false, message, data, null, null);
    }

    /** Used by {@code GlobalExceptionHandler} so clients can branch on a stable code, not the human message. */
    public static <T> ApiResponse<T> error(String message, String errorCode, String requestId) {
        return new ApiResponse<>(false, message, null, errorCode, requestId);
    }

    /** Same as above, plus a small structured payload (e.g. {@code {attemptsRemaining: 2}}). */
    public static <T> ApiResponse<T> error(String message, T data, String errorCode, String requestId) {
        return new ApiResponse<>(false, message, data, errorCode, requestId);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getRequestId() {
        return requestId;
    }
}

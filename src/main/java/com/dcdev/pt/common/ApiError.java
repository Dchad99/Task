package com.dcdev.pt.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Single error shape for every failure response. The timestamp is passed in by
 * {@link ErrorHandler} from the injected {@code Clock}, so it can be pinned in tests.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(Instant timestamp, int status, String error, String message, String path) {
        return new ApiError(timestamp, status, error, message, path, null);
    }

    public static ApiError withFieldErrors(
            Instant timestamp, int status, String error, String message, String path,
            Map<String, String> fieldErrors) {
        return new ApiError(timestamp, status, error, message, path, fieldErrors);
    }
}

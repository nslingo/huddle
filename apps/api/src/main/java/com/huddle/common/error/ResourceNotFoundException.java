package com.huddle.common.error;

/**
 * A requested resource doesn't exist. Mapped to a 404 {@link ApiError} by
 * {@link GlobalExceptionHandler}; the message is returned to the client, so keep it free of
 * internal detail (a client-supplied public id is fine).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
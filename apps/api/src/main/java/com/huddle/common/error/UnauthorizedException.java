package com.huddle.common.error;

/**
 * The caller isn't authenticated: a missing, malformed, expired, or otherwise unverifiable token.
 * Mapped to a 401 {@link ApiError} by {@link GlobalExceptionHandler}.
 *
 * <p>The message reaches the client, so keep it coarse — say that the token is invalid, never
 * <em>why</em> it failed verification. Distinguishing "bad signature" from "wrong audience" from
 * "expired" is a probing oracle for an attacker and buys a legitimate client nothing.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
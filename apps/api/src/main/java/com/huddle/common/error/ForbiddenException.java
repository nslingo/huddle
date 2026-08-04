package com.huddle.common.error;

/**
 * The caller proved who they are but isn't allowed in — currently only a verified Google account
 * outside the permitted email domain. Mapped to a 403 {@link ApiError} by
 * {@link GlobalExceptionHandler}.
 *
 * <p>Unlike {@link UnauthorizedException}, the message here is meant to be actionable: a student
 * who signs in with a personal Gmail needs to be told to use their Cornell account.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
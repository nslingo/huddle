package com.huddle.auth.dto;

/**
 * Response of both {@code POST /api/auth/google} and {@code POST /api/auth/refresh}: a fresh token
 * pair plus the current user.
 *
 * <p>Refresh returns a <em>new</em> refresh token as well as a new access token — tokens rotate on
 * every use, so the client must replace both and discard the one it presented.
 *
 * @param expiresIn access-token lifetime in seconds, so the client can refresh proactively instead
 *                  of waiting to be told 401.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        AuthUserResponse user) {

    public static AuthResponse of(
            String accessToken, String refreshToken, long expiresIn, AuthUserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
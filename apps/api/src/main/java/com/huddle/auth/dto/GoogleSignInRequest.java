package com.huddle.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/auth/google}.
 *
 * @param idToken the Google <em>ID token</em> from {@code signIn().data.idToken} on the client —
 *                not the access token, and not the {@code serverAuthCode}. It's a JWT we can verify
 *                offline against Google's published keys; an access token would only be checkable
 *                by calling Google on every sign-in.
 */
public record GoogleSignInRequest(@NotBlank String idToken) {
}
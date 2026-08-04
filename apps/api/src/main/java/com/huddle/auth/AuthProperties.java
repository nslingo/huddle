package com.huddle.auth;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Auth configuration, bound from {@code huddle.auth.*}. Secrets come from the {@code apps/api/.env}
 * file imported by {@code application.properties}; nothing here has a usable default, by design —
 * a missing client id or signing secret should fail startup loudly rather than silently accept
 * tokens.
 *
 * @param googleClientId    the <em>Web</em> OAuth client id. The mobile app passes this same value
 *                          as {@code webClientId} to {@code GoogleSignin.configure()}, and it's the
 *                          {@code aud} this service requires on every incoming Google ID token.
 * @param jwtSecret         HMAC-SHA256 signing secret for our own access tokens. Must be at least
 *                          32 bytes; RFC 7518 §3.2 requires a key at least as long as the digest.
 * @param issuer            the {@code iss} claim we stamp on, and require from, our access tokens.
 * @param accessTokenTtl    lifetime of an access token — short, since it can't be revoked.
 * @param refreshTokenTtl   lifetime of a refresh token; also the effective "stay signed in" window.
 * @param allowedEmailDomain the Google Workspace domain a user must belong to. Configurable rather
 *                          than hardcoded so tests can exercise the rejection path without pointing
 *                          at a real domain.
 */
@ConfigurationProperties(prefix = "huddle.auth")
@Validated
public record AuthProperties(
        @NotBlank String googleClientId,
        @NotBlank String jwtSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String allowedEmailDomain) {

    private static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        issuer = issuer != null ? issuer : "https://huddle.app";
        accessTokenTtl = accessTokenTtl != null ? accessTokenTtl : Duration.ofMinutes(15);
        refreshTokenTtl = refreshTokenTtl != null ? refreshTokenTtl : Duration.ofDays(30);
        allowedEmailDomain = allowedEmailDomain != null ? allowedEmailDomain : "cornell.edu";

        if (jwtSecret != null && jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "huddle.auth.jwt-secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
    }
}
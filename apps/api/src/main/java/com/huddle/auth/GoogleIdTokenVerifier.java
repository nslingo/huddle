package com.huddle.auth;

import com.huddle.common.error.ForbiddenException;
import com.huddle.common.error.UnauthorizedException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies a Google ID token minted for our mobile app and reduces it to a {@link GoogleIdentity}.
 *
 * <p>Nimbus does the cryptographic work: {@link NimbusJwtDecoder} fetches and caches Google's JWK
 * set, checks the RS256 signature against the key named by the token's {@code kid}, and runs the
 * validator chain below. The JWK set is fetched lazily on first decode, so constructing this at
 * startup makes no network call.
 *
 * <p>Checks, in order — all of them must pass:
 * <ol>
 *   <li>signature, against Google's published keys;
 *   <li>{@code exp} / {@code nbf} (with Spring's default 60s clock skew);
 *   <li>{@code iss} is one of Google's two accepted spellings;
 *   <li>{@code aud} contains our <em>Web</em> OAuth client id — this is what stops a token minted
 *       for some other app from being replayed against Huddle, and it's the single most important
 *       check here;
 *   <li>{@code email_verified};
 *   <li>the account belongs to the allowed Workspace domain.
 * </ol>
 *
 * <p>The first five failures are 401s with a deliberately vague message. The last is a 403 with a
 * specific one — "you're who you say you are, but this app is Cornell-only" is useful feedback,
 * and it's not information the caller didn't already have.
 */
@Component
public class GoogleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    /** Google issues both spellings and treats them as equivalent; accept either. */
    private static final Set<String> ACCEPTED_ISSUERS =
            Set.of("https://accounts.google.com", "accounts.google.com");

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_EMAIL_VERIFIED = "email_verified";
    private static final String CLAIM_HOSTED_DOMAIN = "hd";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_PICTURE = "picture";

    private final JwtDecoder decoder;
    private final String allowedEmailDomain;

    /** {@code @Autowired} is required: the test constructor below makes the choice ambiguous. */
    @Autowired
    public GoogleIdTokenVerifier(AuthProperties properties) {
        this(properties, buildDecoder(properties));
    }

    /** Visible for testing, so a suite can supply a decoder over a local key pair. */
    GoogleIdTokenVerifier(AuthProperties properties, JwtDecoder decoder) {
        this.decoder = decoder;
        this.allowedEmailDomain = properties.allowedEmailDomain().toLowerCase(Locale.ROOT);
    }

    private static JwtDecoder buildDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
        decoder.setJwtValidator(tokenValidator(properties));
        return decoder;
    }

    /**
     * The non-cryptographic half of verification: expiry, issuer, audience.
     *
     * <p>Package-private so tests can build a decoder over a local key pair and still run the real
     * validator chain — otherwise substituting a decoder would silently skip the audience check,
     * which is the one that matters most.
     */
    static OAuth2TokenValidator<Jwt> tokenValidator(AuthProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                issuerValidator(),
                audienceValidator(properties.googleClientId()));
    }

    private static OAuth2TokenValidator<Jwt> issuerValidator() {
        return jwt -> ACCEPTED_ISSUERS.contains(jwt.getClaimAsString(JwtClaimNames.ISS))
                ? OAuth2TokenValidatorResult.success()
                : failure("Unexpected issuer");
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
        return jwt -> {
            List<String> audience = jwt.getAudience();
            return audience != null && audience.contains(clientId)
                    ? OAuth2TokenValidatorResult.success()
                    : failure("Unexpected audience");
        };
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", description, null));
    }

    /**
     * @throws UnauthorizedException if the token doesn't verify or the email isn't confirmed
     * @throws ForbiddenException    if it verifies but the account is outside the allowed domain
     */
    public GoogleIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException ex) {
            // Log the real reason; return a vague one.
            log.debug("Google ID token rejected: {}", ex.getMessage());
            throw new UnauthorizedException("Invalid Google ID token");
        }

        String email = jwt.getClaimAsString(CLAIM_EMAIL);
        if (email == null || email.isBlank()) {
            throw new UnauthorizedException("Invalid Google ID token");
        }
        email = email.trim().toLowerCase(Locale.ROOT);

        if (!isEmailVerified(jwt)) {
            throw new UnauthorizedException("Google account email is not verified");
        }

        if (!isInAllowedDomain(jwt, email)) {
            throw new ForbiddenException(
                    "Huddle is only available to " + allowedEmailDomain
                            + " accounts. Please sign in with your Cornell Google account.");
        }

        return new GoogleIdentity(
                jwt.getSubject(),
                email,
                jwt.getClaimAsString(CLAIM_NAME),
                jwt.getClaimAsString(CLAIM_PICTURE));
    }

    /** Google sends a JSON boolean, but has historically also sent the string {@code "true"}. */
    private static boolean isEmailVerified(Jwt jwt) {
        Object claim = jwt.getClaim(CLAIM_EMAIL_VERIFIED);
        return switch (claim) {
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            case null, default -> false;
        };
    }

    /**
     * The email address must itself be in the domain, and {@code hd} — if Google sent one — must
     * agree.
     *
     * <p>The email suffix is the mandatory half rather than an alternative to {@code hd}, because a
     * Workspace may own several domains: a {@code cornell.edu} tenant can legitimately issue an
     * address in a secondary domain, which would satisfy {@code hd} while violating
     * {@code ck_users_email_cornell} on insert. Accepting on {@code hd} alone would turn that into
     * a 500 at the database instead of a clean 403 here.
     *
     * <p>{@code hd} is still checked when present: it's Google's own assertion that the account is
     * Workspace-managed, so a mismatch is a signal worth refusing on.
     */
    private boolean isInAllowedDomain(Jwt jwt, String normalizedEmail) {
        if (!normalizedEmail.endsWith("@" + allowedEmailDomain)) {
            return false;
        }
        String hostedDomain = jwt.getClaimAsString(CLAIM_HOSTED_DOMAIN);
        return hostedDomain == null
                || hostedDomain.trim().toLowerCase(Locale.ROOT).equals(allowedEmailDomain);
    }
}
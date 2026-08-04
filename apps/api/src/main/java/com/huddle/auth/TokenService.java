package com.huddle.auth;

import com.huddle.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

/**
 * Mints the two credentials this API issues.
 *
 * <p><strong>Access token</strong> — a short-lived HS256 JWT carrying the user's {@code public_id}
 * as {@code sub}. Stateless and therefore <em>not revocable</em>, which is precisely why its TTL is
 * measured in minutes.
 *
 * <p><strong>Refresh token</strong> — 256 bits of {@link SecureRandom} entropy, base64url-encoded.
 * Deliberately opaque rather than a second JWT: refresh tokens must be revocable (rotation, reuse
 * detection, sign-out), and a self-contained JWT can only be revoked by maintaining a denylist —
 * which is the same database round-trip as a lookup table, with worse ergonomics. Only the SHA-256
 * hash is persisted; the plaintext exists solely in the response to the client.
 *
 * <p>A plain digest is correct here, unlike for passwords: these are full-entropy random values, so
 * there is nothing to brute-force and no need for a slow KDF.
 */
@Service
public class TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String CLAIM_EMAIL = "email";

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

    public TokenService(JwtEncoder jwtEncoder, AuthProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /** Signs an access token for {@code user}, valid for {@code huddle.auth.access-token-ttl}. */
    public String issueAccessToken(User user, Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getPublicId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.accessTokenTtl()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** A fresh opaque refresh token. Returned to the client once and never stored in plaintext. */
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return base64Url.encodeToString(bytes);
    }

    /** The lookup key stored in {@code refresh_tokens.token_hash}. */
    public String hashRefreshToken(String tokenValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return base64Url.encodeToString(digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is required of every JRE by the MessageDigest spec; unreachable.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public Instant refreshTokenExpiry(Instant issuedAt) {
        return issuedAt.plus(properties.refreshTokenTtl());
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
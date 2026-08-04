package com.huddle.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huddle.common.error.ForbiddenException;
import com.huddle.common.error.UnauthorizedException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verification is the security boundary of the whole slice, so these run against real signed
 * tokens: a locally generated RSA key stands in for Google's, and the decoder is wired with the
 * production validator chain via {@link GoogleIdTokenVerifier#tokenValidator} so audience, issuer
 * and expiry are genuinely exercised rather than stubbed past.
 */
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "test-google-client-id.apps.googleusercontent.com";
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private static RSAKey googleKey;
    private static RSAKey otherKey;

    private GoogleIdTokenVerifier verifier;

    @BeforeAll
    static void generateKeys() throws JOSEException {
        googleKey = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("attacker-key").generate();
    }

    @BeforeEach
    void setUp() throws JOSEException {
        AuthProperties properties = properties();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(googleKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(GoogleIdTokenVerifier.tokenValidator(properties));
        verifier = new GoogleIdTokenVerifier(properties, decoder);
    }

    @Test
    void verify_validCornellToken_returnsIdentity() throws Exception {
        String token = sign(baseClaims().build());

        GoogleIdentity identity = verifier.verify(token);

        assertThat(identity.sub()).isEqualTo("google-sub-123");
        assertThat(identity.email()).isEqualTo("nsl42@cornell.edu");
        assertThat(identity.name()).isEqualTo("Noah Lingo");
        assertThat(identity.picture()).isEqualTo("https://lh3.googleusercontent.com/a/photo");
    }

    @Test
    void verify_uppercaseEmail_isNormalizedToLowercase() throws Exception {
        // ck_users_email_cornell requires a lowercase address, so normalization has to happen
        // before the value reaches the insert.
        String token = sign(baseClaims().claim("email", "NSL42@Cornell.EDU").build());

        assertThat(verifier.verify(token).email()).isEqualTo("nsl42@cornell.edu");
    }

    @Test
    void verify_withoutHostedDomainClaim_stillAcceptsCornellEmail() throws Exception {
        String token = sign(baseClaims().claim("hd", null).build());

        assertThat(verifier.verify(token).email()).isEqualTo("nsl42@cornell.edu");
    }

    @Test
    void verify_bareIssuerSpelling_isAccepted() throws Exception {
        // Google issues both "accounts.google.com" and "https://accounts.google.com".
        String token = sign(baseClaims().issuer("accounts.google.com").build());

        assertThat(verifier.verify(token).sub()).isEqualTo("google-sub-123");
    }

    @Test
    void verify_emailVerifiedAsString_isAccepted() throws Exception {
        String token = sign(baseClaims().claim("email_verified", "true").build());

        assertThat(verifier.verify(token).sub()).isEqualTo("google-sub-123");
    }

    @Test
    void verify_tokenForAnotherApp_isRejected() throws Exception {
        // The critical check: a valid Google token minted for a different client must not be
        // replayable against Huddle.
        String token = sign(baseClaims().audience("some-other-app.apps.googleusercontent.com").build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid Google ID token");
    }

    @Test
    void verify_tokenSignedByAnotherKey_isRejected() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(otherKey.getKeyID()).build(),
                baseClaims().build());
        jwt.sign(new RSASSASigner(otherKey));

        assertThatThrownBy(() -> verifier.verify(jwt.serialize()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verify_wrongIssuer_isRejected() throws Exception {
        String token = sign(baseClaims().issuer("https://evil.example.com").build());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verify_expiredToken_isRejected() throws Exception {
        Instant expiredAt = Instant.now().minus(Duration.ofHours(1));
        String token = sign(baseClaims().expirationTime(Date.from(expiredAt)).build());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verify_unverifiedEmail_isRejected() throws Exception {
        String token = sign(baseClaims().claim("email_verified", false).build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Google account email is not verified");
    }

    @Test
    void verify_missingEmail_isRejected() throws Exception {
        String token = sign(baseClaims().claim("email", null).build());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verify_malformedToken_isRejected() {
        assertThatThrownBy(() -> verifier.verify("not-a-jwt"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid Google ID token");
    }

    @Test
    void verify_personalGoogleAccount_isForbiddenNotUnauthorized() throws Exception {
        // Authentic Google identity, wrong school: the caller proved who they are, so 403 with an
        // actionable message rather than a vague 401.
        String token = sign(baseClaims()
                .claim("email", "someone@gmail.com")
                .claim("hd", null)
                .build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cornell.edu");
    }

    @Test
    void verify_hostedDomainMatchesButEmailIsSecondaryDomain_isForbidden() throws Exception {
        // A Workspace can own several domains. Accepting on hd alone would let this through and
        // then blow up on ck_users_email_cornell as a 500; refuse it here instead.
        String token = sign(baseClaims()
                .claim("email", "someone@med.cornell.example")
                .claim("hd", "cornell.edu")
                .build());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void verify_cornellEmailButForeignHostedDomain_isForbidden() throws Exception {
        String token = sign(baseClaims().claim("hd", "evil.example.com").build());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(ForbiddenException.class);
    }

    private static AuthProperties properties() {
        return new AuthProperties(
                CLIENT_ID,
                "test-signing-secret-not-used-anywhere-real-0123456789",
                "https://huddle.test",
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                "cornell.edu");
    }

    private static JWTClaimsSet.Builder baseClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(GOOGLE_ISSUER)
                .audience(CLIENT_ID)
                .subject("google-sub-123")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                .claim("email", "nsl42@cornell.edu")
                .claim("email_verified", true)
                .claim("hd", "cornell.edu")
                .claim("name", "Noah Lingo")
                .claim("picture", "https://lh3.googleusercontent.com/a/photo");
    }

    private static String sign(JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(googleKey.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(googleKey));
        return jwt.serialize();
    }
}
package com.huddle.auth;

import com.huddle.auth.dto.AuthResponse;
import com.huddle.auth.dto.AuthUserResponse;
import com.huddle.common.error.UnauthorizedException;
import com.huddle.user.User;
import com.huddle.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in, token rotation and sign-out.
 *
 * <p>Sign-in is an upsert keyed on {@code google_sub}, so a returning student lands on their
 * existing row (and their saved clubs) rather than a duplicate account, and profile changes on the
 * Google side propagate on each sign-in.
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final EntityManager entityManager;

    public AuthService(
            GoogleIdTokenVerifier googleIdTokenVerifier,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            TokenService tokenService,
            EntityManager entityManager) {
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.entityManager = entityManager;
    }

    /**
     * Verifies a Google ID token, upserts the user, and starts a fresh rotation family. Each
     * sign-in gets its own family so signing in on a second device doesn't disturb the first.
     */
    public AuthResponse signInWithGoogle(String idToken) {
        GoogleIdentity identity = googleIdTokenVerifier.verify(idToken);
        User user = upsert(identity);
        return issueTokenPair(user, UUID.randomUUID());
    }

    /**
     * Exchanges a refresh token for a new pair, revoking the presented one.
     *
     * <p>{@code noRollbackFor} is load-bearing: the reuse-detection branch revokes the family and
     * <em>then</em> throws, and the default rollback-on-RuntimeException would quietly undo exactly
     * the write we care about. Every other failure path in here is read-only, so suppressing
     * rollback for this exception costs nothing.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refresh(String refreshTokenValue) {
        Instant now = Instant.now();
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(tokenService.hashRefreshToken(refreshTokenValue))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (token.isRevoked()) {
            // Already rotated: the only way to hold this value and still be presenting it is that
            // it leaked. Assume the whole chain is compromised and force a full re-authentication.
            UUID familyId = token.getFamilyId();
            Long userId = token.getUser().getId();
            refreshTokenRepository.revokeFamily(familyId, now);
            log.warn("Refresh token reuse detected for user {}; revoked family {}", userId, familyId);
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (token.isExpired(now)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        token.setRevokedAt(now);
        return issueTokenPair(token.getUser(), token.getFamilyId());
    }

    /**
     * Signs out the device holding this refresh token by revoking its whole family.
     *
     * <p>Intentionally silent on an unknown or already-revoked token: sign-out is idempotent, and
     * reporting "no such token" would turn this into an oracle for probing token validity. Any
     * still-valid access token survives until it expires — that's the accepted cost of stateless
     * access tokens, and why their TTL is short.
     */
    public void logout(String refreshTokenValue) {
        refreshTokenRepository
                .findByTokenHash(tokenService.hashRefreshToken(refreshTokenValue))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    /** Resolves the {@code sub} of a verified access token to the current user projection. */
    @Transactional(readOnly = true)
    public AuthUserResponse currentUser(String publicId) {
        UUID parsed;
        try {
            parsed = UUID.fromString(publicId);
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid access token");
        }
        return userRepository.findByPublicId(parsed)
                .map(AuthService::toUserResponse)
                // Verified signature but no row: the account was deleted after the token was
                // issued. The token is valid and simply has no subject anymore.
                .orElseThrow(() -> new UnauthorizedException("Invalid access token"));
    }

    private User upsert(GoogleIdentity identity) {
        User user = userRepository.findByGoogleSub(identity.sub()).orElseGet(User::new);
        user.setGoogleSub(identity.sub());
        user.setEmail(identity.email());
        user.setFullName(blankToNull(identity.name()));
        user.setAvatarUrl(blankToNull(identity.picture()));

        User saved = userRepository.saveAndFlush(user);
        if (saved.getPublicId() == null) {
            // public_id / created_at are DB-generated and mapped read-only, so they aren't
            // populated by the insert; re-read them before building the response.
            entityManager.refresh(saved);
        }
        return saved;
    }

    private AuthResponse issueTokenPair(User user, UUID familyId) {
        Instant now = Instant.now();

        RefreshToken refreshToken = new RefreshToken();
        String refreshValue = tokenService.generateRefreshTokenValue();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenService.hashRefreshToken(refreshValue));
        refreshToken.setFamilyId(familyId);
        refreshToken.setExpiresAt(tokenService.refreshTokenExpiry(now));
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(
                tokenService.issueAccessToken(user, now),
                refreshValue,
                tokenService.accessTokenTtlSeconds(),
                toUserResponse(user));
    }

    private static AuthUserResponse toUserResponse(User user) {
        return new AuthUserResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getOnboardingCompletedAt());
    }

    /**
     * The {@code ck_users_full_name_nonempty} constraint rejects an empty string but allows null,
     * so a missing Google display name has to be stored as null.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
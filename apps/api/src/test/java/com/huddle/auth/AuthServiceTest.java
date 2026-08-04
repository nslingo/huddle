package com.huddle.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.huddle.auth.dto.AuthResponse;
import com.huddle.common.error.UnauthorizedException;
import com.huddle.user.User;
import com.huddle.user.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Unit tests for the sign-in / rotation logic. {@link TokenService} is real rather than mocked —
 * it's cheap, and it means the assertions run against genuinely signed tokens and real hashes.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "test-signing-secret-not-used-anywhere-real-0123456789";
    private static final UUID USER_PUBLIC_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private EntityManager entityManager;
    @Captor
    private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

    private TokenService tokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                "client-id", SECRET, "https://huddle.test",
                Duration.ofMinutes(15), Duration.ofDays(30), "cornell.edu");
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        tokenService = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties);
        authService = new AuthService(
                googleIdTokenVerifier, userRepository, refreshTokenRepository, tokenService, entityManager);
    }

    @Test
    void signInWithGoogle_newUser_createsRowAndReturnsTokenPair() {
        when(googleIdTokenVerifier.verify("google-token")).thenReturn(
                new GoogleIdentity("sub-1", "nsl42@cornell.edu", "Noah Lingo", "https://photo"));
        when(userRepository.findByGoogleSub("sub-1")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        // public_id is DB-generated, so it only materializes on the post-insert refresh.
        doAnswer(inv -> {
            ((User) inv.getArgument(0)).setPublicId(USER_PUBLIC_ID);
            return null;
        }).when(entityManager).refresh(any(User.class));

        AuthResponse response = authService.signInWithGoogle("google-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getGoogleSub()).isEqualTo("sub-1");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("nsl42@cornell.edu");
        assertThat(userCaptor.getValue().getFullName()).isEqualTo("Noah Lingo");

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().publicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(response.user().email()).isEqualTo("nsl42@cornell.edu");
    }

    @Test
    void signInWithGoogle_existingUser_updatesProfileInPlace() {
        User existing = user();
        existing.setFullName("Old Name");
        when(googleIdTokenVerifier.verify("google-token")).thenReturn(
                new GoogleIdentity("sub-1", "nsl42@cornell.edu", "New Name", "https://new-photo"));
        when(userRepository.findByGoogleSub("sub-1")).thenReturn(Optional.of(existing));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.signInWithGoogle("google-token");

        assertThat(existing.getFullName()).isEqualTo("New Name");
        assertThat(existing.getAvatarUrl()).isEqualTo("https://new-photo");
        assertThat(existing.getId()).isEqualTo(1L);
        // Already has a public_id, so no post-insert re-read is needed.
        verify(entityManager, never()).refresh(any());
    }

    @Test
    void signInWithGoogle_blankDisplayName_isStoredAsNull() {
        // ck_users_full_name_nonempty rejects an empty string but permits null.
        when(googleIdTokenVerifier.verify("google-token")).thenReturn(
                new GoogleIdentity("sub-1", "nsl42@cornell.edu", "   ", null));
        when(userRepository.findByGoogleSub("sub-1")).thenReturn(Optional.of(user()));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.signInWithGoogle("google-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getFullName()).isNull();
        assertThat(userCaptor.getValue().getAvatarUrl()).isNull();
    }

    @Test
    void signInWithGoogle_eachSignInStartsANewRotationFamily() {
        when(googleIdTokenVerifier.verify(any())).thenReturn(
                new GoogleIdentity("sub-1", "nsl42@cornell.edu", "Noah", null));
        when(userRepository.findByGoogleSub("sub-1")).thenReturn(Optional.of(user()));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.signInWithGoogle("token-a");
        authService.signInWithGoogle("token-b");

        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(refreshTokenCaptor.capture());
        // Signing in on a second device must not disturb the first device's chain.
        assertThat(refreshTokenCaptor.getAllValues().get(0).getFamilyId())
                .isNotEqualTo(refreshTokenCaptor.getAllValues().get(1).getFamilyId());
    }

    @Test
    void refresh_rotatesTokenAndRevokesThePresentedOne() {
        String presented = "presented-refresh-value";
        RefreshToken stored = storedToken(presented, Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHash(tokenService.hashRefreshToken(presented)))
                .thenReturn(Optional.of(stored));

        AuthResponse response = authService.refresh(presented);

        assertThat(stored.getRevokedAt()).isNotNull();
        assertThat(response.refreshToken()).isNotEqualTo(presented);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        // The successor stays in the same family so reuse detection can trace the chain.
        assertThat(refreshTokenCaptor.getValue().getFamilyId()).isEqualTo(stored.getFamilyId());
        assertThat(refreshTokenCaptor.getValue().getTokenHash())
                .isEqualTo(tokenService.hashRefreshToken(response.refreshToken()));
    }

    @Test
    void refresh_reusedToken_revokesWholeFamilyAndRejects() {
        String presented = "already-rotated";
        RefreshToken stored = storedToken(presented, Instant.now().plus(Duration.ofDays(1)));
        stored.setRevokedAt(Instant.now().minus(Duration.ofMinutes(5)));
        when(refreshTokenRepository.findByTokenHash(tokenService.hashRefreshToken(presented)))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(presented))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository).revokeFamily(eq(stored.getFamilyId()), any(Instant.class));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_expiredToken_isRejectedWithoutRevokingTheFamily() {
        String presented = "expired";
        RefreshToken stored = storedToken(presented, Instant.now().minus(Duration.ofMinutes(1)));
        when(refreshTokenRepository.findByTokenHash(tokenService.hashRefreshToken(presented)))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(presented))
                .isInstanceOf(UnauthorizedException.class);

        // Expiry is normal end-of-life, not evidence of theft.
        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_unknownToken_isRejected() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("never-issued"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    void logout_revokesThePresentedTokensFamily() {
        String presented = "logging-out";
        RefreshToken stored = storedToken(presented, Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHash(tokenService.hashRefreshToken(presented)))
                .thenReturn(Optional.of(stored));

        authService.logout(presented);

        verify(refreshTokenRepository).revokeFamily(eq(stored.getFamilyId()), any(Instant.class));
    }

    @Test
    void logout_unknownToken_isSilentlyIgnored() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        authService.logout("never-issued");

        // Idempotent, and deliberately not an oracle for whether a token exists.
        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    void currentUser_returnsProjectionForTheTokenSubject() {
        when(userRepository.findByPublicId(USER_PUBLIC_ID)).thenReturn(Optional.of(user()));

        assertThat(authService.currentUser(USER_PUBLIC_ID.toString()).email())
                .isEqualTo("nsl42@cornell.edu");
    }

    @Test
    void currentUser_deletedAccount_isRejected() {
        when(userRepository.findByPublicId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.currentUser(UUID.randomUUID().toString()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void currentUser_nonUuidSubject_isRejectedWithoutQueryingTheDatabase() {
        assertThatThrownBy(() -> authService.currentUser("not-a-uuid"))
                .isInstanceOf(UnauthorizedException.class);

        verifyNoInteractions(userRepository);
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setPublicId(USER_PUBLIC_ID);
        user.setGoogleSub("sub-1");
        user.setEmail("nsl42@cornell.edu");
        return user;
    }

    private RefreshToken storedToken(String value, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setId(10L);
        token.setUser(user());
        token.setTokenHash(tokenService.hashRefreshToken(value));
        token.setFamilyId(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"));
        token.setExpiresAt(expiresAt);
        return token;
    }
}
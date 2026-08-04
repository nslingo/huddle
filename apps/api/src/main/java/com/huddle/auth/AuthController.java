package com.huddle.auth;

import com.huddle.auth.dto.AuthResponse;
import com.huddle.auth.dto.AuthUserResponse;
import com.huddle.auth.dto.GoogleSignInRequest;
import com.huddle.auth.dto.RefreshRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Exchanges a Google ID token for a Huddle token pair, creating the account on first sign-in.
     * Public by necessity — this is how a caller becomes authenticated.
     *
     * <p>401 if the Google token doesn't verify, 403 if it verifies but the account isn't Cornell.
     */
    @PostMapping("/google")
    public AuthResponse signInWithGoogle(@Valid @RequestBody GoogleSignInRequest request) {
        return authService.signInWithGoogle(request.idToken());
    }

    /**
     * Rotates a refresh token into a new pair. Also public — a caller with an expired access token
     * can't authenticate, which is the whole point of the endpoint. The refresh token in the body
     * is the credential.
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Revokes the presented refresh token's family. Idempotent, always 204. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * The signed-in user. Lets the client validate a stored token on launch and re-hydrate the
     * profile without keeping a stale copy on device.
     */
    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(jwt.getSubject());
    }
}
package com.huddle.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huddle.auth.dto.AuthResponse;
import com.huddle.auth.dto.AuthUserResponse;
import com.huddle.common.error.ForbiddenException;
import com.huddle.common.error.UnauthorizedException;
import com.huddle.config.SecurityConfig;
import com.huddle.config.SecurityErrorResponder;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for the auth endpoints: HTTP contract, status mapping, and which routes the filter
 * chain lets through unauthenticated.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityErrorResponder.class})
@ActiveProfiles("test")
class AuthControllerWebMvcTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void signInWithGoogle_returnsTokenPairAndUser() throws Exception {
        when(authService.signInWithGoogle("google-id-token")).thenReturn(
                AuthResponse.of("access-jwt", "refresh-value", 900, userResponse()));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.publicId").value(USER_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.user.email").value("nsl42@cornell.edu"))
                // Null until interest selection lands; the client routes on it.
                .andExpect(jsonPath("$.user.onboardingCompletedAt").doesNotExist());

        verify(authService).signInWithGoogle("google-id-token");
    }

    @Test
    void signInWithGoogle_isReachableWithoutAuthentication() throws Exception {
        // The endpoint that hands out credentials obviously can't require them.
        when(authService.signInWithGoogle(any())).thenReturn(
                AuthResponse.of("access-jwt", "refresh-value", 900, userResponse()));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-id-token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void signInWithGoogle_blankIdToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("idToken"));

        verifyNoInteractions(authService);
    }

    @Test
    void signInWithGoogle_unverifiableToken_returns401WithBearerChallenge() throws Exception {
        when(authService.signInWithGoogle(any()))
                .thenThrow(new UnauthorizedException("Invalid Google ID token"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"bogus\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid Google ID token"));
    }

    @Test
    void signInWithGoogle_nonCornellAccount_returns403WithActionableMessage() throws Exception {
        when(authService.signInWithGoogle(any()))
                .thenThrow(new ForbiddenException("Huddle is only available to cornell.edu accounts."));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"personal-account-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Huddle is only available to cornell.edu accounts."));
    }

    @Test
    void refresh_returnsRotatedPair() throws Exception {
        when(authService.refresh("old-refresh")).thenReturn(
                AuthResponse.of("new-access", "new-refresh", 900, userResponse()));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                // Rotation: the client must replace the refresh token too, not just the access one.
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(authService.refresh(any())).thenThrow(new UnauthorizedException("Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"stale\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void refresh_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("refreshToken"));

        verifyNoInteractions(authService);
    }

    @Test
    void logout_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"some-token\"}"))
                .andExpect(status().isNoContent());

        verify(authService).logout("some-token");
    }

    @Test
    void logout_unknownToken_stillReturns204() throws Exception {
        doThrow(new AssertionError("logout must not surface errors"))
                .when(authService).logout("throws-if-called-wrong");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"unknown\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void me_withValidToken_returnsCurrentUser() throws Exception {
        when(authService.currentUser(USER_PUBLIC_ID.toString())).thenReturn(userResponse());

        mockMvc.perform(get("/api/auth/me")
                        .with(jwt().jwt(builder -> builder.subject(USER_PUBLIC_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(USER_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.email").value("nsl42@cornell.edu"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));

        verifyNoInteractions(authService);
    }

    private static AuthUserResponse userResponse() {
        return new AuthUserResponse(
                USER_PUBLIC_ID, "nsl42@cornell.edu", "Noah Lingo", "https://photo", null);
    }
}
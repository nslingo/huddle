package com.huddle.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huddle.user.User;
import com.huddle.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack auth test against a real Postgres with Flyway applied.
 *
 * <p>{@link GoogleIdTokenVerifier} is the one thing stubbed: minting a token Google would actually
 * sign isn't possible from a test, and the verifier's own logic is covered exhaustively against
 * real signatures in {@link GoogleIdTokenVerifierTest}. Everything downstream of it is real — the
 * upsert, the DB-generated {@code public_id}, token signing, rotation, and the security filter
 * chain deciding whether the issued token opens a protected endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthControllerIT {

    private static final GoogleIdentity CORNELL_IDENTITY =
            new GoogleIdentity("google-sub-abc", "nsl42@cornell.edu", "Noah Lingo", "https://photo");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @BeforeEach
    void resetState() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        org.mockito.Mockito.when(googleIdTokenVerifier.verify("valid-google-token"))
                .thenReturn(CORNELL_IDENTITY);
    }

    @Test
    void signIn_createsUserAndIssuesATokenThatOpensProtectedEndpoints() throws Exception {
        String body = signIn();

        User created = userRepository.findByGoogleSub("google-sub-abc").orElseThrow();
        assertThat(created.getEmail()).isEqualTo("nsl42@cornell.edu");
        assertThat(created.getFullName()).isEqualTo("Noah Lingo");
        // public_id is a DB default and mapped read-only; this proves the post-insert re-read works.
        assertThat(created.getPublicId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getOnboardingCompletedAt()).isNull();

        assertThat(JsonPath.<String>read(body, "$.user.publicId"))
                .isEqualTo(created.getPublicId().toString());

        // The point of the whole slice: a token minted here is accepted by the resource server on a
        // different, protected endpoint.
        mockMvc.perform(get("/api/clubs").header(HttpHeaders.AUTHORIZATION, bearer(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void signIn_forAReturningUser_reusesTheSameRow() throws Exception {
        String first = signIn();
        String second = signIn();

        assertThat(userRepository.count()).isEqualTo(1);
        // Same account, so the same external identifier across sign-ins.
        assertThat(JsonPath.<String>read(second, "$.user.publicId"))
                .isEqualTo(JsonPath.read(first, "$.user.publicId"));
    }

    @Test
    void clubs_withoutAToken_areNotReadable() throws Exception {
        mockMvc.perform(get("/api/clubs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void clubs_withAForgedToken_areNotReadable() throws Exception {
        mockMvc.perform(get("/api/clubs").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsTheSignedInUser() throws Exception {
        String body = signIn();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nsl42@cornell.edu"))
                .andExpect(jsonPath("$.fullName").value("Noah Lingo"));
    }

    @Test
    void refresh_issuesAWorkingPairAndRetiresThePresentedToken() throws Exception {
        String signIn = signIn();
        String originalRefresh = JsonPath.read(signIn, "$.refreshToken");

        String refreshed = refresh(originalRefresh);
        String newRefresh = JsonPath.read(refreshed, "$.refreshToken");
        assertThat(newRefresh).isNotEqualTo(originalRefresh);

        // The new access token works...
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(refreshed)))
                .andExpect(status().isOk());

        // ...and the rotated-away token is dead.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(originalRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_replayingARotatedToken_killsTheWholeFamily() throws Exception {
        String signIn = signIn();
        String originalRefresh = JsonPath.read(signIn, "$.refreshToken");
        String currentRefresh = JsonPath.read(refresh(originalRefresh), "$.refreshToken");

        // Replay the retired token, as a thief holding a stolen copy would.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(originalRefresh)))
                .andExpect(status().isUnauthorized());

        // The legitimate holder's current token is revoked too — the family is assumed compromised.
        // This also pins that the revocation survives the exception, i.e. that noRollbackFor on
        // AuthService.refresh is doing its job.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(currentRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesTheRefreshTokenFamily() throws Exception {
        String signIn = signIn();
        String refreshToken = JsonPath.read(signIn, "$.refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withAnUnknownToken_isStillANoContent() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("never-issued")))
                .andExpect(status().isNoContent());
    }

    @Test
    void signIn_persistsOnlyAHashOfTheRefreshToken() throws Exception {
        String body = signIn();
        String refreshToken = JsonPath.read(body, "$.refreshToken");

        // A database dump must not yield replayable credentials.
        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .satisfies(stored -> assertThat(stored.getTokenHash()).isNotEqualTo(refreshToken));
        assertThat(refreshTokenRepository.findByTokenHash(refreshToken)).isEmpty();
    }

    private String signIn() throws Exception {
        return mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"valid-google-token\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    private static String bearer(String authResponseBody) {
        return "Bearer " + JsonPath.<String>read(authResponseBody, "$.accessToken");
    }
}
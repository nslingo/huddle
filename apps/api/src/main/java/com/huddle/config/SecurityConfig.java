package com.huddle.config;

import com.huddle.auth.AuthProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless bearer-token security.
 *
 * <p>The {@link JwtEncoder} / {@link JwtDecoder} beans here handle <em>our own</em> access tokens,
 * signed HS256 with a shared secret. Symmetric signing is the right call while one service both
 * mints and verifies these; no third party needs to verify them independently, so an asymmetric key
 * pair would add distribution and rotation work for no gain.
 *
 * <p>Google ID tokens are verified separately, inside {@code GoogleIdTokenVerifier}, which builds
 * its own decoder over Google's JWK set rather than exposing a second {@link JwtDecoder} bean —
 * two beans of that type would make the resource-server wiring ambiguous.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    private final AuthProperties authProperties;

    public SecurityConfig(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    /**
     * Everything requires a bearer token except the endpoints that hand one out, plus the health
     * probes. The club feed and detail endpoints are deliberately inside "everything": Huddle is a
     * Cornell-only app, and leaving the club data anonymously readable would undercut that across
     * most of the API surface.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, SecurityErrorResponder errorResponder) throws Exception {
        return http
                // No cookies and no session, so CSRF has no vector here; the browser-oriented
                // defaults would only get in a native client's way.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/google", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder()))
                        .authenticationEntryPoint(errorResponder)
                        .accessDeniedHandler(errorResponder))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorResponder)
                        .accessDeniedHandler(errorResponder))
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Built explicitly rather than via JwtValidators.createDefaultWithIssuer, whose contents
        // have shifted between Spring Security minors; these two are what we actually require.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(authProperties.issuer())));
        return decoder;
    }

    private SecretKey signingKey() {
        return new SecretKeySpec(
                authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
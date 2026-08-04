package com.huddle.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The minimal user projection returned alongside a token pair — enough for the client to render a
 * profile chip and decide where to route next, without a follow-up request.
 *
 * @param onboardingCompletedAt null until interest selection is done. The client routes to
 *                              onboarding vs. the feed on this; onboarding itself is a later slice.
 */
public record AuthUserResponse(
        UUID publicId,
        String email,
        String fullName,
        String avatarUrl,
        Instant onboardingCompletedAt) {
}
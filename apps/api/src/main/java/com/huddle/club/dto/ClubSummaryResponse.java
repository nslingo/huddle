package com.huddle.club.dto;

import com.huddle.interest.dto.InterestRef;
import java.util.List;
import java.util.UUID;

/**
 * Feed card for a club. {@code blurb} is a server-truncated summary
 * ({@code coalesce(description, mission)}); {@code interests} is capped (≤3) and batch-fetched for
 * the page.
 */
public record ClubSummaryResponse(
        UUID publicId,
        String name,
        String logoUrl,
        String blurb,
        List<InterestRef> interests) {
}
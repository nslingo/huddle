package com.huddle.club.dto;

import com.huddle.club.ClubStatus;
import com.huddle.interest.dto.InterestRef;
import java.util.List;
import java.util.UUID;

/**
 * Full club page. Carries the feed card's identity/interest fields — with {@code interests}
 * <em>uncapped</em> — plus everything the detail screen needs.
 *
 * <p>No {@code blurb} here (unlike the feed card): the full {@code description} and {@code mission}
 * are both present, so a truncated derivative would be dead weight the screen wouldn't render.
 *
 * <p>{@code links} is the club's stored links with the Instagram URL normalized from
 * {@code instagramHandle}; {@code contacts} is the free-form {@code {type, value}} list from
 * ingestion. Both are always present — empty rather than null.
 */
public record ClubDetailResponse(
        UUID publicId,
        String slug,
        String name,
        ClubStatus status,
        String logoUrl,
        String description,
        String mission,
        String goals,
        String clubType,
        String membershipType,
        String instagramHandle,
        Integer followerCount,
        Float activityScore,
        List<InterestRef> interests,
        List<ContactRef> contacts,
        List<ClubLinkRef> links) {
}
package com.huddle.club.dto;

import com.huddle.club.ClubLinkType;

/**
 * An external link for a club. {@code type} serializes as its lowercase label ({@code instagram},
 * {@code website}, …), matching the Postgres {@code club_link_type} enum.
 */
public record ClubLinkRef(ClubLinkType type, String url) {
}
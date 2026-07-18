package com.huddle.club;

/**
 * Maps the Postgres {@code club_link_type} native enum. Constant names match the DB labels exactly
 * (lowercase) for the same reason as {@link ClubStatus}.
 */
public enum ClubLinkType {
    instagram,
    facebook,
    x,
    linkedin,
    youtube,
    tiktok,
    discord,
    linktree,
    website
}
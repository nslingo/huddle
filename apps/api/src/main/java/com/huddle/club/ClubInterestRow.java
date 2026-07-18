package com.huddle.club;

/**
 * Flat projection used to batch-fetch interests for a page of clubs in a single query. One row per
 * (club, interest); callers group by {@code clubId} and cap how many they keep per club.
 */
public interface ClubInterestRow {

    Long getClubId();

    String getSlug();

    String getName();
}
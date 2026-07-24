package com.huddle.club;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry of the {@code clubs.contacts} jsonb array, e.g. {@code {"type":"email","value":"…"}}.
 *
 * <p>{@code type} is free-form rather than an enum: the ingestion job derives it from the
 * CampusGroups contact label, which is usually {@code email} or {@code phone} but falls back to the
 * raw label (or {@code other}) for anything else. Unknown properties are ignored so a scraper that
 * starts emitting extra keys doesn't break the read path.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubContact(String type, String value) {
}
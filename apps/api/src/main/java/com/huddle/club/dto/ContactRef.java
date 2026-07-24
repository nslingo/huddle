package com.huddle.club.dto;

/**
 * A club contact on the detail response. {@code type} is free-form (typically {@code email} or
 * {@code phone}) — see {@code ClubContact} for why.
 */
public record ContactRef(String type, String value) {
}
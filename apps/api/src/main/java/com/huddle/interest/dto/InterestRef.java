package com.huddle.interest.dto;

/**
 * Minimal interest reference exposed on club responses: the public {@code slug} plus display
 * {@code name}.
 */
public record InterestRef(String slug, String name) {
}
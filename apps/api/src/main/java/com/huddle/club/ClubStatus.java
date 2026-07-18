package com.huddle.club;

/**
 * Maps the Postgres {@code club_status} native enum.
 *
 * <p>Constant names must exactly match the enum labels defined in the V1 migration, which are
 * lowercase. Hibernate's {@link org.hibernate.type.SqlTypes#NAMED_ENUM} binds and reads by
 * {@link Enum#name()} and is case-sensitive, so these are intentionally lowercase rather than the
 * usual {@code UPPER_CASE}.
 */
public enum ClubStatus {
    active,
    inactive
}
package com.huddle.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A Huddle account, always backed by a Cornell-managed Google account. Rows are created by the
 * sign-in flow (upsert by {@code google_sub}), never by the ingestion job.
 *
 * <p>Mapping notes, same conventions as {@code Club}: {@code public_id}, {@code created_at} and
 * {@code updated_at} are DB-managed (default value / trigger) so they're mapped read-only
 * ({@code insertable = false, updatable = false}); {@code public_id} must be re-read after insert.
 * No {@code @UpdateTimestamp} on {@code updatedAt} — the {@code trg_users_set_updated_at} trigger
 * owns it.
 *
 * <p>{@code email} is check-constrained by the database to a lowercase {@code @cornell.edu} address;
 * the sign-in flow rejects non-Cornell accounts well before that, but the constraint remains the
 * last line of defense.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", insertable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "google_sub", nullable = false, unique = true)
    private String googleSub;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** Null until the user finishes interest selection; the client routes on this. */
    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
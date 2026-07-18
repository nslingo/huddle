package com.huddle.club;

import com.huddle.interest.Interest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A Cornell club. Populated offline by the ingestion job; the API is read-only over this table.
 *
 * <p>Schema-driven mappings worth noting:
 * <ul>
 *   <li>{@code public_id}, {@code created_at}, {@code updated_at} are DB-managed (default value /
 *       trigger) and mapped read-only ({@code insertable = false, updatable = false}); no
 *       {@code @UpdateTimestamp} on {@code updatedAt} since a trigger maintains it.
 *   <li>{@code status} is a Postgres native enum, mapped via {@link SqlTypes#NAMED_ENUM}.
 *   <li>The {@code search_vector} (tsvector) and {@code contacts} (jsonb) columns are intentionally
 *       left unmapped here — full-text search uses a native query, and {@code contacts} arrives with
 *       the club detail endpoint in a later slice.
 * </ul>
 */
@Entity
@Table(name = "clubs")
@Getter
@Setter
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", insertable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, unique = true)
    private String slug;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "club_status", nullable = false)
    private ClubStatus status;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column
    private String mission;

    @Column
    private String goals;

    @Column(name = "club_type")
    private String clubType;

    @Column(name = "membership_type")
    private String membershipType;

    @Column(name = "instagram_handle")
    private String instagramHandle;

    @Column(name = "follower_count")
    private Integer followerCount;

    @Column(name = "activity_score")
    private Float activityScore;

    @Column(name = "logo_url")
    private String logoUrl;

    /**
     * Interests linked through the {@code club_interests} join table. Lazy and read-only here: the
     * feed batch-fetches interests for a whole page via a dedicated projection query rather than
     * touching this association per row (avoids N+1). The join table's {@code source} column is not
     * needed by the API and is left unmapped.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "club_interests",
            joinColumns = @JoinColumn(name = "club_id"),
            inverseJoinColumns = @JoinColumn(name = "interest_id"))
    private Set<Interest> interests = new LinkedHashSet<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
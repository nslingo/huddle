package com.huddle.club;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClubRepository extends JpaRepository<Club, Long> {

    /**
     * One page of the feed filtered by status, ordered most-active-first with a stable tiebreak.
     *
     * <p>The {@code ORDER BY} lives in the query (not the {@link Pageable}'s {@code Sort}) because
     * {@code NULLS LAST} precedence is unsupported on Spring Data's Criteria-backed derived queries
     * but is supported in HQL. Callers pass an <em>unsorted</em> {@link Pageable}.
     */
    @Query(value = """
            select c from Club c
            where c.status = :status
            order by c.activityScore desc nulls last, c.id asc
            """,
            countQuery = "select count(c) from Club c where c.status = :status")
    Page<Club> findFeedByStatus(@Param("status") ClubStatus status, Pageable pageable);

    /** Looks up a single club by its public id, regardless of status (see {@code ClubService}). */
    Optional<Club> findByPublicId(UUID publicId);

    /**
     * Batch-fetches the interests for the given clubs in a single query, ordered by club and then
     * interest name. Callers cap how many are kept per club. Returns an empty list for empty input.
     */
    @Query("""
            select c.id as clubId, i.slug as slug, i.name as name
            from Club c
            join c.interests i
            where c.id in :clubIds
            order by c.id, i.name
            """)
    List<ClubInterestRow> findInterestRowsForClubs(@Param("clubIds") Collection<Long> clubIds);
}
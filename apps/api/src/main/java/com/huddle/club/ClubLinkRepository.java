package com.huddle.club;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubLinkRepository extends JpaRepository<ClubLink, Long> {

    /**
     * Every stored link for one club. Unordered — {@link ClubService} keys them into an
     * {@code EnumMap} to merge in the derived Instagram link, which fixes the response order.
     */
    List<ClubLink> findByClubId(Long clubId);
}
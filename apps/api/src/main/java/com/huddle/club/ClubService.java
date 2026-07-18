package com.huddle.club;

import com.huddle.club.dto.ClubSummaryResponse;
import com.huddle.common.PageResponse;
import com.huddle.interest.dto.InterestRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClubService {

    /** Max interests shown on a feed card. */
    private static final int MAX_INTERESTS = 3;

    /** Approximate max length of a feed-card blurb, before any ellipsis. */
    private static final int BLURB_MAX_CHARS = 160;

    private final ClubRepository clubRepository;

    public ClubService(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    /**
     * Returns one page of the club discovery feed (active clubs, most active first). Each card
     * carries up to {@value #MAX_INTERESTS} interests, batch-fetched for the whole page.
     */
    @Transactional(readOnly = true)
    public PageResponse<ClubSummaryResponse> getFeed(int page, int size) {
        // Ordering is defined in the query; pass an unsorted Pageable (see findFeedByStatus).
        Pageable pageable = PageRequest.of(page, size);
        Page<Club> clubs = clubRepository.findFeedByStatus(ClubStatus.active, pageable);

        Map<Long, List<InterestRef>> interestsByClub = fetchInterests(clubs.getContent());

        List<ClubSummaryResponse> content = clubs.getContent().stream()
                .map(club -> toSummary(club, interestsByClub.getOrDefault(club.getId(), List.of())))
                .toList();

        return PageResponse.of(content, clubs);
    }

    private Map<Long, List<InterestRef>> fetchInterests(List<Club> clubs) {
        if (clubs.isEmpty()) {
            return Map.of();
        }
        List<Long> clubIds = clubs.stream().map(Club::getId).toList();
        Map<Long, List<InterestRef>> byClub = new LinkedHashMap<>();
        for (ClubInterestRow row : clubRepository.findInterestRowsForClubs(clubIds)) {
            List<InterestRef> refs = byClub.computeIfAbsent(row.getClubId(), key -> new ArrayList<>());
            if (refs.size() < MAX_INTERESTS) {
                refs.add(new InterestRef(row.getSlug(), row.getName()));
            }
        }
        return byClub;
    }

    private ClubSummaryResponse toSummary(Club club, List<InterestRef> interests) {
        return new ClubSummaryResponse(
                club.getPublicId(),
                club.getName(),
                club.getLogoUrl(),
                buildBlurb(club),
                interests);
    }

    /**
     * {@code coalesce(description, mission)}, whitespace-trimmed and, if longer than
     * {@value #BLURB_MAX_CHARS} code points, truncated on a word boundary with a trailing ellipsis.
     * Returns {@code null} when neither field has text.
     *
     * <p>Truncation counts by code points and cuts on a code-point boundary so a surrogate pair
     * (emoji, supplementary CJK) is never split into a lone surrogate. Scraped descriptions can
     * carry non-breaking spaces, so the word-boundary backup treats any Unicode space as a break.
     */
    private static String buildBlurb(Club club) {
        String source = StringUtils.hasText(club.getDescription())
                ? club.getDescription()
                : club.getMission();
        if (!StringUtils.hasText(source)) {
            return null;
        }
        String trimmed = source.strip();
        if (trimmed.codePointCount(0, trimmed.length()) <= BLURB_MAX_CHARS) {
            return trimmed;
        }
        String cut = trimmed.substring(0, trimmed.offsetByCodePoints(0, BLURB_MAX_CHARS));
        int lastSpace = lastWhitespaceIndex(cut);
        if (lastSpace > 0) {
            cut = cut.substring(0, lastSpace);
        }
        return cut.strip() + "…";
    }

    /**
     * Index of the last whitespace character in {@code s}, or {@code -1} if none. Recognizes any
     * Unicode space — {@link Character#isWhitespace} plus {@link Character#isSpaceChar} (the latter
     * covering NBSP and friends common in scraped HTML) — and walks by code point.
     */
    private static int lastWhitespaceIndex(String s) {
        for (int i = s.length(); i > 0; ) {
            int cp = s.codePointBefore(i);
            int start = i - Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                return start;
            }
            i = start;
        }
        return -1;
    }
}
package com.huddle.club;

import com.huddle.club.dto.ClubDetailResponse;
import com.huddle.club.dto.ClubLinkRef;
import com.huddle.club.dto.ClubSummaryResponse;
import com.huddle.club.dto.ContactRef;
import com.huddle.common.PageResponse;
import com.huddle.common.error.ResourceNotFoundException;
import com.huddle.interest.dto.InterestRef;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    /** Base of a public Instagram profile URL; a club's handle is appended to it. */
    private static final String INSTAGRAM_PROFILE_URL = "https://www.instagram.com/";

    private final ClubRepository clubRepository;
    private final ClubLinkRepository clubLinkRepository;

    public ClubService(ClubRepository clubRepository, ClubLinkRepository clubLinkRepository) {
        this.clubRepository = clubRepository;
        this.clubLinkRepository = clubLinkRepository;
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

        Map<Long, List<InterestRef>> interestsByClub =
                fetchInterests(clubs.getContent(), MAX_INTERESTS);

        List<ClubSummaryResponse> content = clubs.getContent().stream()
                .map(club -> toSummary(club, interestsByClub.getOrDefault(club.getId(), List.of())))
                .toList();

        return PageResponse.of(content, clubs);
    }

    /**
     * Full detail for one club, by public id.
     *
     * <p>Unlike the feed this does <em>not</em> filter on status: a club that goes inactive stays
     * reachable from a link a user already has (a saved club shouldn't start 404ing), and the
     * response carries {@code status} so the client can label it.
     *
     * @throws ResourceNotFoundException if no club has that public id
     */
    @Transactional(readOnly = true)
    public ClubDetailResponse getClub(UUID publicId) {
        Club club = clubRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("No club with id " + publicId));

        List<InterestRef> interests = fetchInterests(List.of(club), Integer.MAX_VALUE)
                .getOrDefault(club.getId(), List.of());

        return new ClubDetailResponse(
                club.getPublicId(),
                club.getSlug(),
                club.getName(),
                club.getStatus(),
                club.getLogoUrl(),
                club.getDescription(),
                club.getMission(),
                club.getGoals(),
                club.getClubType(),
                club.getMembershipType(),
                club.getInstagramHandle(),
                club.getFollowerCount(),
                club.getActivityScore(),
                interests,
                buildContacts(club),
                buildLinks(club));
    }

    /**
     * Interests for the given clubs, batched into one query and keyed by club id. Each club keeps at
     * most {@code maxPerClub} (the feed caps them; the detail page passes no effective limit).
     */
    private Map<Long, List<InterestRef>> fetchInterests(List<Club> clubs, int maxPerClub) {
        if (clubs.isEmpty()) {
            return Map.of();
        }
        List<Long> clubIds = clubs.stream().map(Club::getId).toList();
        Map<Long, List<InterestRef>> byClub = new LinkedHashMap<>();
        for (ClubInterestRow row : clubRepository.findInterestRowsForClubs(clubIds)) {
            List<InterestRef> refs = byClub.computeIfAbsent(row.getClubId(), key -> new ArrayList<>());
            if (refs.size() < maxPerClub) {
                refs.add(new InterestRef(row.getSlug(), row.getName()));
            }
        }
        return byClub;
    }

    /** Contacts as stored, minus any entry with no value. Empty rather than null when unset. */
    private static List<ContactRef> buildContacts(Club club) {
        List<ClubContact> contacts = club.getContacts();
        if (contacts == null) {
            return List.of();
        }
        return contacts.stream()
                .filter(contact -> contact != null && StringUtils.hasText(contact.value()))
                .map(contact -> new ContactRef(contact.type(), contact.value()))
                .toList();
    }

    /**
     * The club's stored links, with the Instagram entry rebuilt from {@code instagram_handle} when
     * there is one.
     *
     * <p>The two sources are redundant today — ingestion parses the handle out of the same scraped
     * profile URL — so this isn't merging information: the handle is the normalized form (lowercase,
     * check-constrained, and what the Instagram post pipeline keys on), so deriving from it emits a
     * clean URL instead of whatever tracking params the scrape carried. A club with a stored link
     * but no handle keeps its stored URL.
     *
     * <p>Ordering comes from the {@link EnumMap}, which iterates in {@link ClubLinkType} declaration
     * order, so the response is stable without an {@code ORDER BY}.
     */
    private List<ClubLinkRef> buildLinks(Club club) {
        Map<ClubLinkType, String> byType = new EnumMap<>(ClubLinkType.class);
        for (ClubLink link : clubLinkRepository.findByClubId(club.getId())) {
            byType.put(link.getType(), link.getUrl());
        }
        if (StringUtils.hasText(club.getInstagramHandle())) {
            byType.put(ClubLinkType.instagram, INSTAGRAM_PROFILE_URL + club.getInstagramHandle().strip());
        }
        return byType.entrySet().stream()
                .map(entry -> new ClubLinkRef(entry.getKey(), entry.getValue()))
                .toList();
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
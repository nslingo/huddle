package com.huddle.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huddle.club.dto.ClubSummaryResponse;
import com.huddle.common.PageResponse;
import com.huddle.interest.dto.InterestRef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ClubServiceTest {

    @Mock
    private ClubRepository clubRepository;

    @InjectMocks
    private ClubService clubService;

    @Test
    void getFeed_mapsClubs_capsInterests_andBuildsBlurb() {
        UUID chessId = UUID.randomUUID();
        Club chess = club(1L, chessId, "Chess Club", "logo.png",
                "a".repeat(200), null); // long description -> truncated blurb
        Club art = club(2L, UUID.randomUUID(), "Art Society", null,
                null, "Make art together"); // description blank -> blurb from mission

        Page<Club> page = new PageImpl<>(List.of(chess, art), PageRequest.of(0, 20), 2);
        when(clubRepository.findFeedByStatus(eq(ClubStatus.active), any(Pageable.class))).thenReturn(page);
        // Chess has 5 interests (query-ordered) -> capped to 3; Art has 1.
        when(clubRepository.findInterestRowsForClubs(anyCollection())).thenReturn(List.of(
                row(1L, "arts", "Arts"),
                row(1L, "games", "Games"),
                row(1L, "music", "Music"),
                row(1L, "service", "Service"),
                row(1L, "tech", "Technology"),
                row(2L, "arts", "Arts")));

        PageResponse<ClubSummaryResponse> feed = clubService.getFeed(0, 20);

        assertThat(feed.content()).hasSize(2);
        assertThat(feed.totalElements()).isEqualTo(2);
        assertThat(feed.page()).isZero();
        assertThat(feed.size()).isEqualTo(20);
        assertThat(feed.hasNext()).isFalse();

        ClubSummaryResponse chessCard = feed.content().get(0);
        assertThat(chessCard.publicId()).isEqualTo(chessId);
        assertThat(chessCard.name()).isEqualTo("Chess Club");
        assertThat(chessCard.logoUrl()).isEqualTo("logo.png");
        assertThat(chessCard.blurb()).endsWith("…").hasSizeLessThanOrEqualTo(161);
        assertThat(chessCard.interests())
                .extracting(InterestRef::slug)
                .containsExactly("arts", "games", "music"); // preserves query order, capped at 3

        ClubSummaryResponse artCard = feed.content().get(1);
        assertThat(artCard.blurb()).isEqualTo("Make art together");
        assertThat(artCard.interests()).extracting(InterestRef::slug).containsExactly("arts");
    }

    @Test
    void getFeed_blurb_truncatesOnWholeWord() {
        // A spaced description well over the cap (~233 chars), so the word-boundary backup runs.
        String description = "lorem ipsum dolor sit amet consectetur ".repeat(6).strip();
        stubSingleClub(club(1L, UUID.randomUUID(), "Wordy", null, description, null));

        String blurb = clubService.getFeed(0, 20).content().get(0).blurb();

        assertThat(blurb).endsWith("…").hasSizeLessThanOrEqualTo(161);
        String body = blurb.substring(0, blurb.length() - 1); // drop the ellipsis
        // Cut on a whole word: body is a non-empty source prefix, ends on a word char (the pre-ellipsis
        // strip worked), and the next source char is whitespace (we cut at a boundary, not mid-word).
        assertThat(body).isNotEmpty();
        assertThat(description).startsWith(body);
        assertThat(Character.isWhitespace(body.charAt(body.length() - 1))).isFalse();
        assertThat(Character.isWhitespace(description.charAt(body.length()))).isTrue();
    }

    @Test
    void getFeed_blurb_doesNotSplitSurrogatePairs() {
        // Odd leading char makes the naive 160-char cut land inside a surrogate pair; no spaces, so
        // the backup can't rescue it. A correct cut keeps whole code points (no lone surrogate/U+FFFD).
        String emoji = new String(Character.toChars(0x1F600)); // 😀
        stubSingleClub(club(1L, UUID.randomUUID(), "Emoji", null, "x" + emoji.repeat(200), null));

        String blurb = clubService.getFeed(0, 20).content().get(0).blurb();

        assertThat(blurb).endsWith("…");
        String body = blurb.substring(0, blurb.length() - 1);
        // A well-formed string yields no code point in the surrogate range; an unpaired surrogate
        // (what a naive char-boundary cut leaves behind) would, and Jackson would emit it as U+FFFD.
        boolean hasLoneSurrogate = body.codePoints()
                .anyMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
        assertThat(hasLoneSurrogate).as("blurb must not contain an unpaired surrogate").isFalse();
    }

    @Test
    void getFeed_requestsGivenPageAndSize_ordersInQuery() {
        Page<Club> page = new PageImpl<>(List.of(), PageRequest.of(3, 25), 0);
        when(clubRepository.findFeedByStatus(eq(ClubStatus.active), any(Pageable.class))).thenReturn(page);

        clubService.getFeed(3, 25);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clubRepository).findFeedByStatus(eq(ClubStatus.active), captor.capture());
        // Ordering lives in the query (NULLS LAST), so the Pageable is unsorted.
        assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(25);
        assertThat(captor.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    void getFeed_emptyPage_skipsInterestQuery() {
        Page<Club> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(clubRepository.findFeedByStatus(eq(ClubStatus.active), any(Pageable.class))).thenReturn(page);

        PageResponse<ClubSummaryResponse> feed = clubService.getFeed(0, 20);

        assertThat(feed.content()).isEmpty();
        verify(clubRepository, never()).findInterestRowsForClubs(anyCollection());
    }

    /** Stubs a one-club feed page with no interests, for tests that only care about the club itself. */
    private void stubSingleClub(Club club) {
        Page<Club> page = new PageImpl<>(List.of(club), PageRequest.of(0, 20), 1);
        when(clubRepository.findFeedByStatus(eq(ClubStatus.active), any(Pageable.class))).thenReturn(page);
        when(clubRepository.findInterestRowsForClubs(anyCollection())).thenReturn(List.of());
    }

    private static Club club(Long id, UUID publicId, String name, String logoUrl,
            String description, String mission) {
        Club club = new Club();
        club.setId(id);
        club.setPublicId(publicId);
        club.setName(name);
        club.setLogoUrl(logoUrl);
        club.setDescription(description);
        club.setMission(mission);
        return club;
    }

    private static ClubInterestRow row(long clubId, String slug, String name) {
        return new ClubInterestRow() {
            @Override
            public Long getClubId() {
                return clubId;
            }

            @Override
            public String getSlug() {
                return slug;
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }
}

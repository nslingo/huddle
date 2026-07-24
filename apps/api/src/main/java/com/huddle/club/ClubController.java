package com.huddle.club;

import com.huddle.club.dto.ClubDetailResponse;
import com.huddle.club.dto.ClubSummaryResponse;
import com.huddle.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clubs")
@Validated
public class ClubController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    /** Paginated club discovery feed, sorted by activity (most active first). No auth yet. */
    @GetMapping
    public PageResponse<ClubSummaryResponse> listClubs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return clubService.getFeed(page, size);
    }

    /**
     * One club's full detail page. 404s if the id is unknown; a malformed uuid is a 400 (handled as
     * a type mismatch by {@code GlobalExceptionHandler}).
     */
    @GetMapping("/{publicId}")
    public ClubDetailResponse getClub(@PathVariable UUID publicId) {
        return clubService.getClub(publicId);
    }
}
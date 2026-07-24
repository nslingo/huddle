package com.huddle.club;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack read-path test for the club feed against a real Postgres (Testcontainers) with Flyway
 * migrations applied. Requires Docker; skipped by Surefire (named {@code *IT}) and run via Failsafe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Sql("/it-seed-clubs.sql")
class ClubControllerIT {

    // public_ids pinned by it-seed-clubs.sql so the detail tests can address clubs directly.
    private static final String CHESS_ID = "11111111-1111-4111-8111-111111111111";
    private static final String ART_ID = "22222222-2222-4222-8222-222222222222";
    private static final String INACTIVE_ID = "44444444-4444-4444-8444-444444444444";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listClubs_returnsActiveClubsSortedByActivityWithCappedInterests() throws Exception {
        mockMvc.perform(get("/api/clubs"))
                .andExpect(status().isOk())
                // Only the 3 active clubs, ordered by activity_score DESC with NULLs last.
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Chess Club"))
                .andExpect(jsonPath("$.content[1].name").value("Art Society"))
                .andExpect(jsonPath("$.content[2].name").value("No Score Club"))
                // Blurb from description, truncated on a word boundary with a trailing ellipsis.
                .andExpect(jsonPath("$.content[0].blurb").value(endsWith("…")))
                // Interests batch-fetched, ordered by name, capped at 3.
                .andExpect(jsonPath("$.content[0].interests.length()").value(3))
                .andExpect(jsonPath("$.content[0].interests[*].slug")
                        .value(contains("arts", "games", "music")))
                // Blurb falls back to mission when description is null.
                .andExpect(jsonPath("$.content[1].blurb").value("Make art together"))
                .andExpect(jsonPath("$.content[1].interests.length()").value(0));
    }

    @Test
    void getClub_returnsFullDetail() throws Exception {
        mockMvc.perform(get("/api/clubs/{publicId}", CHESS_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(CHESS_ID))
                .andExpect(jsonPath("$.slug").value("chess-club"))
                .andExpect(jsonPath("$.name").value("Chess Club"))
                // Native club_status enum round-trips as its lowercase label.
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.logoUrl").value("https://cdn.example.com/chess.png"))
                // Full text, not the feed's truncated blurb (which the detail response omits).
                .andExpect(jsonPath("$.blurb").doesNotExist())
                .andExpect(jsonPath("$.description").value(startsWith("The Cornell Chess Club meets weekly")))
                .andExpect(jsonPath("$.description").value(endsWith("from across campus.")))
                .andExpect(jsonPath("$.mission").value("Grow chess at Cornell"))
                .andExpect(jsonPath("$.goals").value("Win the Ivy tournament"))
                .andExpect(jsonPath("$.clubType").value("Games"))
                .andExpect(jsonPath("$.membershipType").value("Open"))
                .andExpect(jsonPath("$.instagramHandle").value("cornellchess"))
                .andExpect(jsonPath("$.followerCount").value(1200))
                .andExpect(jsonPath("$.activityScore").value(90.0))
                // Uncapped here: the feed shows 3 of these 5.
                .andExpect(jsonPath("$.interests.length()").value(5))
                .andExpect(jsonPath("$.interests[*].slug")
                        .value(contains("arts", "games", "music", "service", "tech")))
                // contacts is jsonb on clubs, read through the SqlTypes.JSON mapping.
                .andExpect(jsonPath("$.contacts.length()").value(2))
                .andExpect(jsonPath("$.contacts[0].type").value("email"))
                .andExpect(jsonPath("$.contacts[0].value").value("chess@cornell.edu"))
                .andExpect(jsonPath("$.contacts[1].type").value("phone"))
                // Links come back in club_link_type declaration order (instagram before website).
                .andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[0].type").value("instagram"))
                // The stored row's tracking param is replaced by the handle-derived URL.
                .andExpect(jsonPath("$.links[0].url").value("https://www.instagram.com/cornellchess"))
                .andExpect(jsonPath("$.links[1].type").value("website"))
                .andExpect(jsonPath("$.links[1].url").value("https://chess.cornell.edu"));
    }

    @Test
    void getClub_withoutHandle_keepsStoredInstagramLink() throws Exception {
        mockMvc.perform(get("/api/clubs/{publicId}", ART_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramHandle").doesNotExist())
                .andExpect(jsonPath("$.links[0].url").value("https://www.instagram.com/artsociety/"))
                // Nothing to report, but never null.
                .andExpect(jsonPath("$.contacts").isEmpty())
                .andExpect(jsonPath("$.interests").isEmpty());
    }

    /** Detail deliberately ignores status, so a saved club that goes inactive doesn't start 404ing. */
    @Test
    void getClub_servesInactiveClub_withItsStatus() throws Exception {
        mockMvc.perform(get("/api/clubs/{publicId}", INACTIVE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Inactive Club"))
                .andExpect(jsonPath("$.status").value("inactive"));
    }

    @Test
    void getClub_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/clubs/{publicId}", "99999999-9999-4999-8999-999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/clubs/99999999-9999-4999-8999-999999999999"));
    }
}
package com.huddle.club;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
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
                // Interests batch-fetched, ordered by
                // name, capped at 3.
                .andExpect(jsonPath("$.content[0].interests.length()").value(3))
                .andExpect(jsonPath("$.content[0].interests[*].slug")
                        .value(contains("arts", "games", "music")))
                // Blurb falls back to mission when description is null.
                .andExpect(jsonPath("$.content[1].blurb").value("Make art together"))
                .andExpect(jsonPath("$.content[1].interests.length()").value(0));
    }
}
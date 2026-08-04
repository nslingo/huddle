package com.huddle.club;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huddle.club.dto.ClubDetailResponse;
import com.huddle.club.dto.ClubLinkRef;
import com.huddle.club.dto.ClubSummaryResponse;
import com.huddle.club.dto.ContactRef;
import com.huddle.common.PageResponse;
import com.huddle.common.error.ResourceNotFoundException;
import com.huddle.config.SecurityConfig;
import com.huddle.interest.dto.InterestRef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for the club endpoints. Since slice 5 these require authentication, so the class runs
 * as a mock authenticated user; {@link #listClubs_withoutAuthentication_returns401} is the one case
 * that opts back out to pin the unauthenticated behaviour.
 */
@WebMvcTest(ClubController.class)
@Import({SecurityConfig.class, com.huddle.config.SecurityErrorResponder.class})
@ActiveProfiles("test")
@WithMockUser
class ClubControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClubService clubService;

    @Test
    void listClubs_returnsFeedEnvelope() throws Exception {
        UUID publicId = UUID.randomUUID();
        ClubSummaryResponse summary = new ClubSummaryResponse(
                publicId, "Chess Club", "logo.png", "We play chess.",
                List.of(new InterestRef("games", "Games")));
        when(clubService.getFeed(0, 20)).thenReturn(
                new PageResponse<>(List.of(summary), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].publicId").value(publicId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Chess Club"))
                .andExpect(jsonPath("$.content[0].interests[0].slug").value("games"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(clubService).getFeed(0, 20);
    }

    @Test
    void listClubs_passesThroughPagingParams() throws Exception {
        when(clubService.getFeed(2, 50)).thenReturn(
                new PageResponse<>(List.of(), 2, 50, 0, 0, false));

        mockMvc.perform(get("/api/clubs").param("page", "2").param("size", "50"))
                .andExpect(status().isOk());

        verify(clubService).getFeed(2, 50);
    }

    @Test
    void listClubs_rejectsSizeBelowMinimum() throws Exception {
        mockMvc.perform(get("/api/clubs").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(clubService);
    }

    @Test
    void listClubs_rejectsSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/clubs").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(clubService);
    }

    @Test
    void listClubs_rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/clubs").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clubService);
    }

    @Test
    void listClubs_rejectsNonNumericParam() throws Exception {
        mockMvc.perform(get("/api/clubs").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(clubService);
    }

    @Test
    void getClub_returnsDetail() throws Exception {
        UUID publicId = UUID.randomUUID();
        when(clubService.getClub(publicId)).thenReturn(detail(publicId));

        mockMvc.perform(get("/api/clubs/{publicId}", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(publicId.toString()))
                .andExpect(jsonPath("$.slug").value("chess-club"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.interests[0].slug").value("games"))
                .andExpect(jsonPath("$.contacts[0].type").value("email"))
                .andExpect(jsonPath("$.links[0].type").value("instagram"));

        verify(clubService).getClub(publicId);
    }

    @Test
    void getClub_unknownId_returns404() throws Exception {
        UUID publicId = UUID.randomUUID();
        when(clubService.getClub(publicId))
                .thenThrow(new ResourceNotFoundException("No club with id " + publicId));

        mockMvc.perform(get("/api/clubs/{publicId}", publicId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString(publicId.toString())));
    }

    @Test
    void getClub_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/clubs/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(clubService);
    }

    @Test
    void wrongHttpMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(post("/api/clubs"))
                .andExpect(status().isMethodNotAllowed())
                // RFC 9110 §15.5.6: a 405 must advertise the permitted methods.
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));

        verifyNoInteractions(clubService);
    }

    @Test
    @WithAnonymousUser
    void listClubs_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/clubs"))
                .andExpect(status().isUnauthorized())
                // The filter chain rejects before the controller advice runs, so this asserts the
                // SecurityErrorResponder still emits the standard ApiError shape.
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/clubs"));

        verifyNoInteractions(clubService);
    }

    private static ClubDetailResponse detail(UUID publicId) {
        return new ClubDetailResponse(
                publicId, "chess-club", "Chess Club", ClubStatus.active, "logo.png",
                "We play chess.", "Grow chess", "Win the Ivy tournament",
                "Games", "Open", "cornellchess", 1200, 90f,
                List.of(new InterestRef("games", "Games")),
                List.of(new ContactRef("email", "chess@cornell.edu")),
                List.of(new ClubLinkRef(ClubLinkType.instagram, "https://www.instagram.com/cornellchess")));
    }
}
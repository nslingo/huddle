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

import com.huddle.club.dto.ClubSummaryResponse;
import com.huddle.common.PageResponse;
import com.huddle.config.SecurityConfig;
import com.huddle.interest.dto.InterestRef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClubController.class)
@Import(SecurityConfig.class)
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
    void wrongHttpMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(post("/api/clubs"))
                .andExpect(status().isMethodNotAllowed())
                // RFC 9110 §15.5.6: a 405 must advertise the permitted methods.
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));

        verifyNoInteractions(clubService);
    }
}
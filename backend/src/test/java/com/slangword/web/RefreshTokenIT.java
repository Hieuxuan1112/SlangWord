package com.slangword.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.slangword.AbstractIntegrationTest;
import com.slangword.repository.RefreshTokenRepository;
import com.slangword.repository.SearchHistoryRepository;
import com.slangword.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class RefreshTokenIT extends AbstractIntegrationTest {



    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    SearchHistoryRepository searchHistoryRepository;

    private JsonNode session;

    @BeforeEach
    void setUp() throws Exception {
        searchHistoryRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        session = createAccount("session-user");
    }

    private String refreshBody(String token) throws Exception {
        return objectMapper.writeValueAsString(Map.of("refreshToken", token));
    }

    @Test
    void registrationReturnsBothTokens() {
        assertThat(session.get("accessToken").asText()).isNotBlank();
        assertThat(session.get("refreshToken").asText()).isNotBlank();
    }

    @Test
    void theRawRefreshTokenIsNeverStored() {
        String issued = session.get("refreshToken").asText();

        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .satisfies(stored -> {
                    assertThat(stored.getTokenHash()).isNotEqualTo(issued);
                    assertThat(stored.getTokenHash()).hasSize(64); // hex SHA-256
                });
    }

    @Test
    void refreshReturnsANewPairAndRotatesTheRefreshToken() throws Exception {
        String original = session.get("refreshToken").asText();

        String json = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(original)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(json).get("refreshToken").asText()).isNotEqualTo(original);
    }

    @Test
    void theRotatedTokenGrantsAccess() throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(session.get("refreshToken").asText())))
                .andReturn().getResponse().getContentAsString();
        String newAccess = objectMapper.readTree(json).get("accessToken").asText();

        // 404, not 401: the token was accepted and the request reached the service.
        mockMvc.perform(delete("/api/v1/slang-words/NOPE").header("Authorization", bearer(newAccess)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reusingAnAlreadyRotatedTokenRevokesTheWholeFamily() throws Exception {
        String original = session.get("refreshToken").asText();

        String json = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(original)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String rotated = objectMapper.readTree(json).get("refreshToken").asText();

        // Replaying the spent token is the signal that one copy was stolen.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(original)))
                .andExpect(status().isUnauthorized());

        // The thief's replay must also cost the legitimate holder their session,
        // because there is no way to tell which of the two is the attacker.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(rotated)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutStopsTheRefreshTokenWorking() throws Exception {
        String token = session.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutLeavesOtherSessionsAlone() throws Exception {
        // A second login is a second family: signing out of one device must not
        // sign the user out everywhere.
        String secondJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials("session-user")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondDevice = objectMapper.readTree(secondJson).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(session.get("refreshToken").asText())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(secondDevice)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnUnknownRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody("not-a-real-token")))
                .andExpect(status().isUnauthorized());
    }
}

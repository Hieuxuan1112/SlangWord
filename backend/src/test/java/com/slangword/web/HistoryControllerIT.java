package com.slangword.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slangword.AbstractIntegrationTest;
import com.slangword.repository.QuizResultRepository;
import com.slangword.repository.SearchHistoryRepository;
import com.slangword.repository.UserRepository;
import com.slangword.service.SeedService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class HistoryControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SearchHistoryRepository historyRepository;

    @Autowired
    QuizResultRepository quizResultRepository;

    @Autowired
    SeedService seedService;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        historyRepository.deleteAll();
        quizResultRepository.deleteAll();
        userRepository.deleteAll();
        seedService.reset();
        String body = objectMapper.writeValueAsString(Map.of("username", "hieu", "password", "secret123"));
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(json).get("token").asText();
    }

    @Test
    void recordsSearchForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/slang-words").param("q", "BB").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/history").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].keyword").value("BB"))
                .andExpect(jsonPath("$.content[0].resultCount").value(2));
    }

    @Test
    void doesNotRecordSearchForAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/slang-words").param("q", "BB")).andExpect(status().isOk());

        assertThat(historyRepository.count()).isZero();
    }

    @Test
    void requiresAuthenticationToReadHistory() throws Exception {
        mockMvc.perform(get("/api/history")).andExpect(status().isUnauthorized());
    }

    @Test
    void ignoresBlankKeyword() throws Exception {
        mockMvc.perform(get("/api/slang-words").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(historyRepository.count()).isZero();
    }

    @Test
    void reportsZeroQuizStatsForAFreshUser() throws Exception {
        mockMvc.perform(get("/api/history/quiz-stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAnswered").value(0))
                .andExpect(jsonPath("$.accuracy").value(0.0));
    }
}

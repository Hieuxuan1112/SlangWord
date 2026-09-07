package com.slangword.web;

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
class QuizControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    QuizResultRepository quizResultRepository;

    @Autowired
    SearchHistoryRepository searchHistoryRepository;

    @Autowired
    SeedService seedService;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        quizResultRepository.deleteAll();
        searchHistoryRepository.deleteAll();
        userRepository.deleteAll();
        seedService.reset();
        String body = objectMapper.writeValueAsString(Map.of("username", "quizzer", "password", "secret123"));
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(json).get("token").asText();
    }

    @Test
    void servesAQuestionAnonymously() throws Exception {
        mockMvc.perform(get("/api/quiz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt").isNotEmpty())
                .andExpect(jsonPath("$.correctAnswer").isNotEmpty())
                .andExpect(jsonPath("$.options").isArray());
    }

    @Test
    void servesTheDefinitionModeQuestion() throws Exception {
        mockMvc.perform(get("/api/quiz").param("mode", "definition-from-word"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DEFINITION_FROM_WORD"));
    }

    @Test
    void gradesAndStoresAnAnswer() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "mode", "WORD_FROM_DEFINITION",
                "prompt", "Babe",
                "correctAnswer", "BBE",
                "chosenAnswer", "BBE"));

        mockMvc.perform(post("/api/quiz/answer").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.totalAnswered").value(1))
                .andExpect(jsonPath("$.totalCorrect").value(1));
    }

    @Test
    void gradesAWrongAnswer() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "mode", "WORD_FROM_DEFINITION",
                "prompt", "Babe",
                "correctAnswer", "BBE",
                "chosenAnswer", "BBC"));

        mockMvc.perform(post("/api/quiz/answer").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.totalCorrect").value(0));
    }

    @Test
    void requiresAuthenticationToAnswer() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "mode", "WORD_FROM_DEFINITION",
                "prompt", "Babe",
                "correctAnswer", "BBE",
                "chosenAnswer", "BBE"));

        mockMvc.perform(post("/api/quiz/answer")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}

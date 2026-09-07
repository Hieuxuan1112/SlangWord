package com.slangword.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slangword.AbstractIntegrationTest;
import com.slangword.repository.SearchHistoryRepository;
import com.slangword.repository.UserRepository;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SearchHistoryRepository searchHistoryRepository;

    @BeforeEach
    void clean() {
        searchHistoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String register(String username) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", "secret123"));
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    @Test
    void registersThenAuthorisesAProtectedCall() throws Exception {
        String token = register("hieu");

        // 404 rather than 401 proves the token was accepted and the request reached the service.
        mockMvc.perform(delete("/api/slang-words/NOPE").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsProtectedCallWithoutToken() throws Exception {
        mockMvc.perform(delete("/api/slang-words/NOPE")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsGarbageToken() throws Exception {
        mockMvc.perform(delete("/api/slang-words/NOPE").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        register("hieu");
        String body = objectMapper.writeValueAsString(Map.of("username", "hieu", "password", "secret123"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsShortPassword() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "someone", "password", "123"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("password")));
    }

    @Test
    void loginsWithCorrectCredentials() throws Exception {
        register("hieu");
        String body = objectMapper.writeValueAsString(Map.of("username", "hieu", "password", "secret123"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        register("hieu");
        String body = objectMapper.writeValueAsString(Map.of("username", "hieu", "password", "wrongpass"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}

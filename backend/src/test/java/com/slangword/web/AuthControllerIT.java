package com.slangword.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slangword.AbstractIntegrationTest;
import com.slangword.repository.SearchHistoryRepository;
import com.slangword.repository.UserRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    SearchHistoryRepository searchHistoryRepository;

    @BeforeEach
    void clean() {
        searchHistoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registersThenAuthorisesAProtectedCall() throws Exception {
        String token = createAccountToken("hieu");

        // 404 rather than 401 proves the token was accepted and the request reached the service.
        mockMvc.perform(delete("/api/v1/slang-words/NOPE").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsProtectedCallWithoutToken() throws Exception {
        mockMvc.perform(delete("/api/v1/slang-words/NOPE")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsGarbageToken() throws Exception {
        mockMvc.perform(delete("/api/v1/slang-words/NOPE").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        createAccountToken("hieu");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials("hieu")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("someone", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("password")));
    }

    @Test
    void loginsWithCorrectCredentials() throws Exception {
        createAccountToken("hieu");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials("hieu")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        createAccountToken("hieu");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("hieu", ACCOUNT_PASSWORD + "-wrong")))
                .andExpect(status().isUnauthorized());
    }
}

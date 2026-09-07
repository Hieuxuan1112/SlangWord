package com.slangword.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slangword.AbstractIntegrationTest;
import com.slangword.domain.Role;
import com.slangword.domain.User;
import com.slangword.repository.QuizResultRepository;
import com.slangword.repository.SearchHistoryRepository;
import com.slangword.repository.UserRepository;
import com.slangword.security.JwtService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SearchHistoryRepository searchHistoryRepository;

    @Autowired
    QuizResultRepository quizResultRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        searchHistoryRepository.deleteAll();
        quizResultRepository.deleteAll();
        userRepository.deleteAll();

        // Registration only ever mints USER, so the admin fixture goes in through the repository.
        userRepository.save(new User("root", passwordEncoder.encode("secret123"), Role.ADMIN));
        adminToken = jwtService.generateToken("root", "ADMIN");

        String body = objectMapper.writeValueAsString(Map.of("username", "plain", "password", "secret123"));
        String json = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        userToken = objectMapper.readTree(json).get("token").asText();
    }

    @Test
    void rejectsResetWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/reset")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsResetForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/reset").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsResetForAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/reset").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seeded").value(3));
    }
}

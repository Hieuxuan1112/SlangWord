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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminControllerIT extends AbstractIntegrationTest {



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

        userToken = createAccountToken("plain");
    }

    @Test
    void rejectsResetWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reset")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsResetForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reset").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsResetForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reset").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seeded").value(3));
    }
}

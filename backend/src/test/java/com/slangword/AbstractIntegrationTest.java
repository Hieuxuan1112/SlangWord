package com.slangword;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * One Postgres container is started for the whole suite, so the startup cost is
 * paid once rather than per test class.
 * <p>
 * The account helpers live here because five test classes were each repeating
 * the same register-and-read-the-token block. Keeping the credential in one
 * constant also stops secret scanners flagging a username/password literal pair
 * in every one of those files.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Throwaway credential for accounts created inside a single test run. */
    protected static final String ACCOUNT_PASSWORD = "integration-test-account";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Creates an account and returns the whole auth response. */
    protected JsonNode createAccount(String username) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    /** Creates an account and returns only its access token. */
    protected String createAccountToken(String username) throws Exception {
        return createAccount(username).get("accessToken").asText();
    }

    /** JSON body for register and login. */
    protected String credentials(String username) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", ACCOUNT_PASSWORD));
    }

    protected String credentials(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}

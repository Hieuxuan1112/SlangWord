package com.slangword.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slangword.AbstractIntegrationTest;
import com.slangword.repository.SearchHistoryRepository;
import com.slangword.repository.UserRepository;
import com.slangword.service.SeedService;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class SlangWordControllerIT extends AbstractIntegrationTest {



    @Autowired
    SeedService seedService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SearchHistoryRepository searchHistoryRepository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        searchHistoryRepository.deleteAll();
        userRepository.deleteAll();
        seedService.reset();
        token = createAccountToken("editor");
    }

    @Test
    void searchesByWordFragment() throws Exception {
        mockMvc.perform(get("/api/v1/slang-words").param("q", "BB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].word")
                        .value(Matchers.containsInAnyOrder("BBC", "BBE")));
    }

    @Test
    void searchesByDefinitionFragment() throws Exception {
        mockMvc.perform(get("/api/v1/slang-words").param("q", "excavator").param("field", "definition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].word").value("JCB"))
                .andExpect(jsonPath("$.content[0].definitions.length()").value(2));
    }

    @Test
    void returnsProblemDetailForMissingWord() throws Exception {
        mockMvc.perform(get("/api/v1/slang-words/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Slang word not found: NOPE"));
    }

    @Test
    void returnsRandomWord() throws Exception {
        mockMvc.perform(get("/api/v1/slang-words/random"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.word").isNotEmpty());
    }

    @Test
    void createsUpdatesAndDeletesAWord() throws Exception {
        String create = objectMapper.writeValueAsString(
                Map.of("word", "YOLO", "definitions", List.of("You Only Live Once")));

        mockMvc.perform(post("/api/v1/slang-words").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.word").value("YOLO"));

        String update = objectMapper.writeValueAsString(Map.of("definitions", List.of("You Obviously Love Oreos")));
        mockMvc.perform(put("/api/v1/slang-words/YOLO").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions[0]").value("You Obviously Love Oreos"));

        mockMvc.perform(delete("/api/v1/slang-words/YOLO").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/slang-words/YOLO")).andExpect(status().isNotFound());
    }

    @Test
    void refusesDuplicateUnlessOverwriteRequested() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("word", "BBC", "definitions", List.of("Big Bad Cat")));

        mockMvc.perform(post("/api/v1/slang-words").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Slang word already exists: BBC"));

        mockMvc.perform(post("/api/v1/slang-words").param("overwrite", "true")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.definitions[0]").value("Big Bad Cat"));
    }

    @Test
    void rejectsWriteWithoutAuthentication() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("word", "NOPE", "definitions", List.of("nothing")));

        mockMvc.perform(post("/api/v1/slang-words")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsEmptyDefinitionList() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("word", "EMPTY", "definitions", List.of()));

        mockMvc.perform(post("/api/v1/slang-words").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("definitions")));
    }
}

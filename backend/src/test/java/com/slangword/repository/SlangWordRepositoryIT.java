package com.slangword.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.slangword.AbstractIntegrationTest;
import com.slangword.domain.SlangWord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class SlangWordRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    SlangWordRepository repository;

    @BeforeEach
    void reset() {
        repository.deleteAll();
    }

    @Test
    void savesWordWithItsDefinitions() {
        SlangWord word = new SlangWord("JCB");
        word.replaceDefinitions(List.of("J C Bamford", "excavator manufacturer"));
        repository.save(word);

        SlangWord found = repository.findByWordIgnoreCase("jcb").orElseThrow();

        assertThat(found.getDefinitions()).extracting("text")
                .containsExactly("J C Bamford", "excavator manufacturer");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsByDefinitionFragment() {
        SlangWord word = new SlangWord("BBE");
        word.replaceDefinitions(List.of("Babe"));
        repository.save(word);

        assertThat(repository.searchByDefinition("bab", PageRequest.of(0, 10)))
                .extracting(SlangWord::getWord)
                .containsExactly("BBE");
    }

    @Test
    void replacingDefinitionsRemovesTheOldRows() {
        SlangWord word = new SlangWord("XYZ");
        word.replaceDefinitions(List.of("first", "second"));
        repository.saveAndFlush(word);

        word.replaceDefinitions(List.of("only"));
        repository.saveAndFlush(word);

        assertThat(repository.findByWordIgnoreCase("XYZ").orElseThrow().getDefinitions())
                .extracting("text")
                .containsExactly("only");
    }
}

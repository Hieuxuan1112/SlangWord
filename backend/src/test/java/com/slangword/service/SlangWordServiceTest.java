package com.slangword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.slangword.domain.SlangWord;
import com.slangword.dto.SlangWordDtos.UpdateSlangWordRequest;
import com.slangword.dto.SlangWordDtos.UpsertSlangWordRequest;
import com.slangword.exception.ConflictException;
import com.slangword.exception.NotFoundException;
import com.slangword.repository.SlangWordRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SlangWordServiceTest {

    @Mock
    SlangWordRepository repository;

    @InjectMocks
    SlangWordService service;

    private static SlangWord word(String text, String... definitions) {
        SlangWord entity = new SlangWord(text);
        entity.replaceDefinitions(List.of(definitions));
        return entity;
    }

    @Test
    void getByWordReturnsMatch() {
        when(repository.findByWordIgnoreCase("bbc"))
                .thenReturn(Optional.of(word("BBC", "British Broadcasting Corporation")));

        assertThat(service.getByWord("bbc").definitions())
                .containsExactly("British Broadcasting Corporation");
    }

    @Test
    void getByWordThrowsWhenAbsent() {
        when(repository.findByWordIgnoreCase("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByWord("nope"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void searchByDefinitionUsesDefinitionQuery() {
        when(repository.searchByDefinition(eq("babe"), any()))
                .thenReturn(new PageImpl<>(List.of(word("BBE", "Babe"))));

        var page = service.search("babe", SlangWordService.SearchField.DEFINITION, PageRequest.of(0, 10));

        assertThat(page.content()).singleElement().extracting("word").isEqualTo("BBE");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void blankQueryListsEverything() {
        when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(word("A", "a"))));

        var page = service.search("  ", SlangWordService.SearchField.WORD, PageRequest.of(0, 10));

        assertThat(page.content()).hasSize(1);
    }

    @Test
    void randomThrowsWhenDictionaryEmpty() {
        when(repository.findRandom()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.random()).isInstanceOf(NotFoundException.class);
    }

    @Test
    void createRejectsDuplicateWithoutOverwrite() {
        when(repository.findByWordIgnoreCase("BBC")).thenReturn(Optional.of(word("BBC", "old")));

        assertThatThrownBy(() -> service.create(new UpsertSlangWordRequest("BBC", List.of("new")), false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("BBC");
    }

    @Test
    void createOverwritesExistingWhenAsked() {
        when(repository.findByWordIgnoreCase("BBC")).thenReturn(Optional.of(word("BBC", "old")));
        when(repository.save(any(SlangWord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new UpsertSlangWordRequest("BBC", List.of("new")), true);

        assertThat(response.definitions()).containsExactly("new");
    }

    @Test
    void createSavesNewWord() {
        when(repository.findByWordIgnoreCase("ZZZ")).thenReturn(Optional.empty());
        when(repository.save(any(SlangWord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(new UpsertSlangWordRequest("ZZZ", List.of("sleep")), false).word())
                .isEqualTo("ZZZ");
    }

    @Test
    void updateThrowsWhenWordMissing() {
        when(repository.findByWordIgnoreCase("GONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("GONE", new UpdateSlangWordRequest(List.of("x"))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteThrowsWhenWordMissing() {
        when(repository.findByWordIgnoreCase("GONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("GONE")).isInstanceOf(NotFoundException.class);
    }
}

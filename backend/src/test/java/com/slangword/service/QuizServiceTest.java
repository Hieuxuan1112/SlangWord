package com.slangword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.slangword.domain.QuizMode;
import com.slangword.domain.QuizResult;
import com.slangword.domain.SlangWord;
import com.slangword.dto.QuizDtos.QuizAnswerRequest;
import com.slangword.exception.NotFoundException;
import com.slangword.repository.QuizResultRepository;
import com.slangword.repository.SlangWordRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    SlangWordRepository slangWordRepository;

    @Mock
    QuizResultRepository quizResultRepository;

    @InjectMocks
    QuizService service;

    private static SlangWord word(String text, String definition) {
        SlangWord entity = new SlangWord(text);
        entity.replaceDefinitions(List.of(definition));
        return entity;
    }

    @Test
    void buildsFourDistinctOptionsIncludingTheAnswer() {
        when(slangWordRepository.findRandom())
                .thenReturn(Optional.of(word("BBC", "British Broadcasting Corporation")));
        when(slangWordRepository.findRandomWordsExcluding(any(), anyInt()))
                .thenReturn(List.of("AAA", "BBB", "CCC"));

        var question = service.nextQuestion(QuizMode.WORD_FROM_DEFINITION);

        assertThat(question.prompt()).isEqualTo("British Broadcasting Corporation");
        assertThat(question.correctAnswer()).isEqualTo("BBC");
        assertThat(question.options()).hasSize(4).doesNotHaveDuplicates().contains("BBC");
    }

    @Test
    void definitionModePromptsWithTheWord() {
        when(slangWordRepository.findRandom())
                .thenReturn(Optional.of(word("BBC", "British Broadcasting Corporation")));
        when(slangWordRepository.findRandomWordsExcluding(any(), anyInt()))
                .thenReturn(List.of("AAA", "BBB", "CCC"));
        when(slangWordRepository.findByWordIgnoreCase("AAA")).thenReturn(Optional.of(word("AAA", "alpha")));
        when(slangWordRepository.findByWordIgnoreCase("BBB")).thenReturn(Optional.of(word("BBB", "bravo")));
        when(slangWordRepository.findByWordIgnoreCase("CCC")).thenReturn(Optional.of(word("CCC", "charlie")));

        var question = service.nextQuestion(QuizMode.DEFINITION_FROM_WORD);

        assertThat(question.prompt()).isEqualTo("BBC");
        assertThat(question.options()).hasSize(4).contains("British Broadcasting Corporation");
    }

    @Test
    void throwsWhenDictionaryEmpty() {
        when(slangWordRepository.findRandom()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.nextQuestion(QuizMode.WORD_FROM_DEFINITION))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void gradesCorrectAnswerAndPersistsIt() {
        when(quizResultRepository.save(any(QuizResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quizResultRepository.countByUserId(7L)).thenReturn(1L);
        when(quizResultRepository.countByUserIdAndCorrectTrue(7L)).thenReturn(1L);

        var result = service.grade(7L, new QuizAnswerRequest(
                QuizMode.WORD_FROM_DEFINITION, "British Broadcasting Corporation", "BBC", "BBC"));

        assertThat(result.correct()).isTrue();
        assertThat(result.totalCorrect()).isEqualTo(1);
    }

    @Test
    void gradesWrongAnswer() {
        when(quizResultRepository.save(any(QuizResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quizResultRepository.countByUserId(anyLong())).thenReturn(1L);
        when(quizResultRepository.countByUserIdAndCorrectTrue(anyLong())).thenReturn(0L);

        var result = service.grade(7L, new QuizAnswerRequest(
                QuizMode.WORD_FROM_DEFINITION, "British Broadcasting Corporation", "BBC", "AAA"));

        assertThat(result.correct()).isFalse();
        assertThat(result.correctAnswer()).isEqualTo("BBC");
    }
}

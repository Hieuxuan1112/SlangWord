package com.slangword.service;

import com.slangword.domain.QuizMode;
import com.slangword.domain.QuizResult;
import com.slangword.domain.SlangWord;
import com.slangword.dto.QuizDtos.QuizAnswerRequest;
import com.slangword.dto.QuizDtos.QuizAnswerResult;
import com.slangword.dto.QuizDtos.QuizQuestion;
import com.slangword.exception.NotFoundException;
import com.slangword.repository.QuizResultRepository;
import com.slangword.repository.SlangWordRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QuizService {

    private static final int OPTION_COUNT = 4;

    private final SlangWordRepository slangWordRepository;
    private final QuizResultRepository quizResultRepository;

    public QuizService(SlangWordRepository slangWordRepository, QuizResultRepository quizResultRepository) {
        this.slangWordRepository = slangWordRepository;
        this.quizResultRepository = quizResultRepository;
    }

    public QuizQuestion nextQuestion(QuizMode mode) {
        SlangWord answer = slangWordRepository.findRandom()
                .orElseThrow(() -> new NotFoundException("Dictionary is empty"));
        String correctDefinition = answer.getDefinitions().isEmpty()
                ? ""
                : answer.getDefinitions().get(0).getText();
        List<String> distractorWords =
                slangWordRepository.findRandomWordsExcluding(answer.getId(), OPTION_COUNT - 1);

        String prompt;
        String correctOption;
        List<String> distractors = new ArrayList<>();
        if (mode == QuizMode.WORD_FROM_DEFINITION) {
            prompt = correctDefinition;
            correctOption = answer.getWord();
            distractors.addAll(distractorWords);
        } else {
            prompt = answer.getWord();
            correctOption = correctDefinition;
            distractorWords.stream()
                    .map(slangWordRepository::findByWordIgnoreCase)
                    .flatMap(Optional::stream)
                    .filter(word -> !word.getDefinitions().isEmpty())
                    .map(word -> word.getDefinitions().get(0).getText())
                    .forEach(distractors::add);
        }

        // A set before shuffling: two slang words can share a definition, and a
        // repeated option would make the question unanswerable.
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        distinct.add(correctOption);
        distinct.addAll(distractors);
        List<String> options = new ArrayList<>(distinct);
        Collections.shuffle(options);
        return new QuizQuestion(mode, prompt, correctOption, options);
    }

    @Transactional
    public QuizAnswerResult grade(Long userId, QuizAnswerRequest request) {
        boolean correct = request.correctAnswer().equals(request.chosenAnswer());
        quizResultRepository.save(new QuizResult(
                userId,
                request.mode(),
                request.prompt(),
                request.correctAnswer(),
                request.chosenAnswer(),
                correct));
        return new QuizAnswerResult(
                correct,
                request.correctAnswer(),
                quizResultRepository.countByUserId(userId),
                quizResultRepository.countByUserIdAndCorrectTrue(userId));
    }
}

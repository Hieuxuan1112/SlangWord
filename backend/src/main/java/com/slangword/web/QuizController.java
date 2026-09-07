package com.slangword.web;

import com.slangword.domain.QuizMode;
import com.slangword.dto.QuizDtos.QuizAnswerRequest;
import com.slangword.dto.QuizDtos.QuizAnswerResult;
import com.slangword.dto.QuizDtos.QuizQuestion;
import com.slangword.security.CurrentUser;
import com.slangword.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/quiz")
@Tag(name = "Quiz", description = "Four-option quiz in both directions")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    @Operation(summary = "Fetch the next question")
    public QuizQuestion next(@RequestParam(defaultValue = "word-from-definition") String mode) {
        QuizMode quizMode = "definition-from-word".equalsIgnoreCase(mode)
                ? QuizMode.DEFINITION_FROM_WORD
                : QuizMode.WORD_FROM_DEFINITION;
        return quizService.nextQuestion(quizMode);
    }

    @PostMapping("/answer")
    @Operation(summary = "Grade an answer and record it against the caller")
    public QuizAnswerResult answer(@Valid @RequestBody QuizAnswerRequest request) {
        return quizService.grade(CurrentUser.require().getId(), request);
    }
}

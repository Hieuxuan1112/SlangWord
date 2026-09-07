package com.slangword.dto;

import com.slangword.domain.QuizMode;
import com.slangword.domain.QuizResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class QuizDtos {

    private QuizDtos() {
    }

    /**
     * The question carries its own answer and the client echoes it back when answering,
     * which keeps the API stateless — no server-side question store. A determined client
     * could cheat; for a dictionary quiz the honest score is the user's own.
     */
    public record QuizQuestion(
            QuizMode mode,
            String prompt,
            String correctAnswer,
            List<String> options) {
    }

    public record QuizAnswerRequest(
            @NotNull QuizMode mode,
            @NotBlank String prompt,
            @NotBlank String correctAnswer,
            @NotBlank String chosenAnswer) {
    }

    public record QuizAnswerResult(
            boolean correct,
            String correctAnswer,
            long totalAnswered,
            long totalCorrect) {
    }

    public record QuizResultResponse(
            Long id,
            QuizMode mode,
            String prompt,
            String correctAnswer,
            String chosenAnswer,
            boolean correct,
            Instant answeredAt) {

        public static QuizResultResponse from(QuizResult entity) {
            return new QuizResultResponse(
                    entity.getId(),
                    entity.getMode(),
                    entity.getPrompt(),
                    entity.getCorrectAnswer(),
                    entity.getChosenAnswer(),
                    entity.isCorrect(),
                    entity.getAnsweredAt());
        }
    }
}

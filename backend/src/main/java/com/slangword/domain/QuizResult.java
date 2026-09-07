package com.slangword.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "quiz_result")
@EntityListeners(AuditingEntityListener.class)
public class QuizResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuizMode mode;

    @Column(nullable = false, length = 512)
    private String prompt;

    @Column(name = "correct_answer", nullable = false, length = 512)
    private String correctAnswer;

    @Column(name = "chosen_answer", length = 512)
    private String chosenAnswer;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @CreatedDate
    @Column(name = "answered_at", nullable = false, updatable = false)
    private Instant answeredAt;

    protected QuizResult() {
    }

    public QuizResult(Long userId, QuizMode mode, String prompt, String correctAnswer, String chosenAnswer, boolean correct) {
        this.userId = userId;
        this.mode = mode;
        this.prompt = prompt;
        this.correctAnswer = correctAnswer;
        this.chosenAnswer = chosenAnswer;
        this.correct = correct;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public QuizMode getMode() {
        return mode;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public String getChosenAnswer() {
        return chosenAnswer;
    }

    public boolean isCorrect() {
        return correct;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}

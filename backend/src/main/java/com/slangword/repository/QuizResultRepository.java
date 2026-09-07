package com.slangword.repository;

import com.slangword.domain.QuizResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizResultRepository extends JpaRepository<QuizResult, Long> {

    Page<QuizResult> findByUserIdOrderByAnsweredAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndCorrectTrue(Long userId);
}

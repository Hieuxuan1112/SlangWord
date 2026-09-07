package com.slangword.service;

import com.slangword.domain.SearchHistory;
import com.slangword.dto.HistoryDtos.QuizStatsResponse;
import com.slangword.dto.HistoryDtos.SearchHistoryResponse;
import com.slangword.dto.PageResponse;
import com.slangword.repository.QuizResultRepository;
import com.slangword.repository.SearchHistoryRepository;
import com.slangword.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class HistoryService {

    private final SearchHistoryRepository historyRepository;
    private final QuizResultRepository quizResultRepository;

    public HistoryService(SearchHistoryRepository historyRepository, QuizResultRepository quizResultRepository) {
        this.historyRepository = historyRepository;
        this.quizResultRepository = quizResultRepository;
    }

    /** No-op for anonymous callers and blank keywords, so search stays public. */
    public void recordSearch(String keyword, int resultCount) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        CurrentUser.get().ifPresent(user ->
                historyRepository.save(new SearchHistory(user.getId(), keyword.trim(), resultCount)));
    }

    @Transactional(readOnly = true)
    public PageResponse<SearchHistoryResponse> history(Long userId, Pageable pageable) {
        return PageResponse.of(
                historyRepository.findByUserIdOrderBySearchedAtDesc(userId, pageable),
                SearchHistoryResponse::from);
    }

    @Transactional(readOnly = true)
    public QuizStatsResponse quizStats(Long userId) {
        long total = quizResultRepository.countByUserId(userId);
        long correct = quizResultRepository.countByUserIdAndCorrectTrue(userId);
        return new QuizStatsResponse(total, correct, total == 0 ? 0d : (double) correct / total);
    }

    public void clearHistory(Long userId) {
        historyRepository.deleteByUserId(userId);
    }
}

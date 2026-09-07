package com.slangword.dto;

import com.slangword.domain.SearchHistory;
import java.time.Instant;

public final class HistoryDtos {

    private HistoryDtos() {
    }

    public record SearchHistoryResponse(
            Long id,
            String keyword,
            int resultCount,
            Instant searchedAt) {

        public static SearchHistoryResponse from(SearchHistory entity) {
            return new SearchHistoryResponse(
                    entity.getId(),
                    entity.getKeyword(),
                    entity.getResultCount(),
                    entity.getSearchedAt());
        }
    }

    public record QuizStatsResponse(long totalAnswered, long totalCorrect, double accuracy) {
    }
}

package com.slangword.web;

import com.slangword.dto.HistoryDtos.QuizStatsResponse;
import com.slangword.dto.HistoryDtos.SearchHistoryResponse;
import com.slangword.dto.PageResponse;
import com.slangword.security.CurrentUser;
import com.slangword.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/history")
@Tag(name = "History", description = "The caller's own search history and quiz statistics")
public class HistoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    @Operation(summary = "List the caller's searches, newest first")
    public PageResponse<SearchHistoryResponse> history(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return historyService.history(
                CurrentUser.require().getId(),
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
    }

    @GetMapping("/quiz-stats")
    @Operation(summary = "Answered, correct and accuracy for the caller")
    public QuizStatsResponse quizStats() {
        return historyService.quizStats(CurrentUser.require().getId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear the caller's search history")
    public void clear() {
        historyService.clearHistory(CurrentUser.require().getId());
    }
}

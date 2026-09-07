package com.slangword.web;

import com.slangword.dto.PageResponse;
import com.slangword.dto.SlangWordDtos.SlangWordResponse;
import com.slangword.dto.SlangWordDtos.UpdateSlangWordRequest;
import com.slangword.dto.SlangWordDtos.UpsertSlangWordRequest;
import com.slangword.service.HistoryService;
import com.slangword.service.SlangWordService;
import com.slangword.service.SlangWordService.SearchField;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/slang-words")
@Tag(name = "Slang words", description = "Search and manage dictionary entries")
public class SlangWordController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SlangWordService service;
    private final HistoryService historyService;

    public SlangWordController(SlangWordService service, HistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }

    @GetMapping
    @Operation(summary = "Search by word or by definition; records history when authenticated")
    public PageResponse<SlangWordResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "word") String field,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SearchField searchField = "definition".equalsIgnoreCase(field) ? SearchField.DEFINITION : SearchField.WORD;
        PageResponse<SlangWordResponse> result =
                service.search(q, searchField, PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
        historyService.recordSearch(q, (int) result.totalElements());
        return result;
    }

    // Declared before /{word} so the literal path wins over the path variable.
    @GetMapping("/random")
    @Operation(summary = "Return one random slang word")
    public SlangWordResponse random() {
        return service.random();
    }

    @GetMapping("/{word}")
    @Operation(summary = "Look up one slang word")
    public SlangWordResponse get(@PathVariable String word) {
        return service.getByWord(word);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a slang word; 409 if it exists unless overwrite=true")
    public SlangWordResponse create(@Valid @RequestBody UpsertSlangWordRequest request,
                                    @RequestParam(defaultValue = "false") boolean overwrite) {
        return service.create(request, overwrite);
    }

    @PutMapping("/{word}")
    @Operation(summary = "Replace the definitions of an existing word")
    public SlangWordResponse update(@PathVariable String word,
                                    @Valid @RequestBody UpdateSlangWordRequest request) {
        return service.update(word, request);
    }

    @DeleteMapping("/{word}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a slang word")
    public void delete(@PathVariable String word) {
        service.delete(word);
    }
}
